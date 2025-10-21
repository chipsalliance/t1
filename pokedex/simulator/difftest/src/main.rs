use clap::Parser;
use miette::{Context, IntoDiagnostic};
use serde::Serialize;

mod pokedex;
mod spike_parser;

use pokedex::ModelStateWrite as PokedexStateChange;
use spike_parser::Modification as SpikeStateChange;

#[derive(clap::Parser, Debug)]
#[command(version, about, long_about = None)]
struct DiffTestArgs {
    /// Path to the Spike commit log
    #[arg(short = 's', long)]
    spike_log_path: String,
    /// Path to the pokedex trace log
    #[arg(short = 'p', long)]
    pokedex_log_path: String,
    /// MMIO address that act as differential test end pattern, support only hex string
    #[arg(short = 'm', long)]
    mmio_address: String,
    /// Output path for writing difftest result
    #[arg(short = 'o', long)]
    output_path: String,
}

fn main() -> miette::Result<()> {
    let arg = DiffTestArgs::try_parse().into_diagnostic()?;
    // use block to force drop raw_str after parse done
    let raw_str = std::fs::read_to_string(arg.spike_log_path.as_str())
        .into_diagnostic()
        .with_context(|| format!("reading spike log {}", arg.spike_log_path))?;
    let lines = spike_parser::tokenize_spike_log(&raw_str);
    let mut spike_log = Vec::new();
    for l in lines {
        let commit = spike_parser::parse_single_commit(&l).map_err(|err| {
            miette::miette!(
                "fail parse spike log {}: {err}",
                arg.spike_log_path.as_str()
            )
        })?;
        spike_log.push(commit);
    }

    let pokedex_log: Vec<pokedex::InsnCommit> =
        std::fs::read_to_string(arg.pokedex_log_path.as_str())
            .into_diagnostic()
            .with_context(|| format!("reading pokedex log {}", arg.pokedex_log_path))?
            .lines()
            .enumerate()
            .map(|(line_number, line_str)| {
                serde_json::from_str::<pokedex::InsnCommit>(line_str).unwrap_or_else(|err| {
                    panic!("fail parsing pokedex log at line {line_number}: {err}")
                })
            })
            .collect();
    let end_mmio_address = u32::from_str_radix(arg.mmio_address.trim_start_matches("0x"), 16)
        .into_diagnostic()
        .with_context(|| format!("parsing {} to uint32_t", arg.mmio_address))?;

    let result = DiffTest {
        spike_log,
        pokedex_log,
        end_mmio_address,
    }
    .run();

    let raw_json = serde_json::to_string(&result).into_diagnostic()?;
    std::fs::write(arg.output_path, raw_json).into_diagnostic()?;

    Ok(())
}

#[derive(Debug, Serialize)]
struct DiffMeta {
    is_same: bool,
    context: String,
}

impl DiffMeta {
    fn passed() -> Self {
        Self {
            is_same: true,
            context: String::new(),
        }
    }

    fn failed(ctx: impl ToString) -> Self {
        Self {
            is_same: false,
            context: ctx.to_string(),
        }
    }
}

struct DiffTest {
    spike_log: Vec<spike_parser::Commit>,
    pokedex_log: Vec<pokedex::InsnCommit>,
    end_mmio_address: u32,
}

impl DiffTest {
    fn exact_match(
        &self,
        spike_commit: &spike_parser::Commit,
        pokedex_commit: &pokedex::InsnCommit,
    ) -> bool {
        // PC and instruction between two simulator should be natually aligned before diff started
        assert_eq!(spike_commit.pc, pokedex_commit.pc);
        assert_eq!(spike_commit.instruction, pokedex_commit.instruction);

        spike_commit
            .state_changes
            .iter()
            .fold(true, |all_same, write| {
                let is_same = match write {
                    SpikeStateChange::WriteXReg {
                        rd: spike_rd,
                        bits: spike_value,
                    } => pokedex_commit.expect_exists(|evt| match evt {
                        // TODO: add verbose log
                        PokedexStateChange::Xrf {
                            rd: pokedex_rd,
                            value: pokedex_value,
                        } => *spike_rd == *pokedex_rd && *spike_value == (*pokedex_value) as u64,
                        _ => false,
                    }),
                    SpikeStateChange::WriteFReg {
                        rd: spike_rd,
                        bits: spike_value,
                    } => pokedex_commit.expect_exists(|evt| match evt {
                        PokedexStateChange::Frf {
                            rd: pokedex_rd,
                            value: pokedex_value,
                        } => *spike_rd == *pokedex_rd && *spike_value == (*pokedex_value) as u64,
                        _ => false,
                    }),
                    _ => true, // TODO: compare all
                };

                is_same && all_same
            })
    }

    fn run(&self) -> DiffMeta {
        assert!(!self.spike_log.is_empty());
        assert!(!self.pokedex_log.is_empty());

        let mut pokedex_log_cursor = 0;
        let mut is_reset = false;

        let reset_vector_event = self
            .pokedex_log
            .iter()
            .find_map(|commit| commit.find_reset_vector());
        let Some(reset_vector_pc) = reset_vector_event else {
            return DiffMeta::failed("no reset vector event found in pokedex log");
        };

        let Some(end_commit) = self.spike_log.iter().find(|commit| {
            commit
                .find_store_addr_match(self.end_mmio_address.into())
                .is_some()
        }) else {
            return DiffMeta::failed("Can't find any end pattern from spike log");
        };

        for cur_spike_commit in self.spike_log.iter() {
            if !is_reset {
                if cur_spike_commit.pc == reset_vector_pc as u64 {
                    is_reset = true;
                } else {
                    continue;
                }
            }

            if cur_spike_commit.pc == end_commit.pc {
                break;
            }

            let search_result = self.pokedex_log[pokedex_log_cursor..]
                .iter()
                .enumerate()
                .find_map(|(i, event)| {
                    if event.pc == cur_spike_commit.pc {
                        pokedex_log_cursor += i + 1;
                        Some(event)
                    } else {
                        None
                    }
                });

            let Some(pokedex_commit) = search_result else {
                // Event at Spike side doesn't found at Pokedex side
                return DiffMeta::failed(indoc::formatdoc! {"
                    At PC={:#010x} spike have following commit events that are not occur at simulator side:

                    {cur_spike_commit:?}
                ", cur_spike_commit.pc
                });
            };

            if !self.exact_match(cur_spike_commit, pokedex_commit) {
                return DiffMeta::failed(indoc::formatdoc! {"
                    Found unmatched event between spike and pokedex.

                    ===============================================
                    Spike dump:
                    {cur_spike_commit:#?}
                    ===============================================

                    ===============================================
                    Pokedex dump:
                    {pokedex_commit}
                    ===============================================
                "});
            }
        }

        DiffMeta::passed()
    }
}
