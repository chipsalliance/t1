use clap::Parser;
use miette::{Context, IntoDiagnostic};
use serde::Serialize;

use crate::pokedex::{PokedexEventKind, PokedexLog};
use crate::spike::SpikeLog;

mod pokedex;
mod spike;

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
    let spike_log = {
        let raw_str = std::fs::read_to_string(arg.spike_log_path.as_str())
            .into_diagnostic()
            .with_context(|| format!("reading spike log {}", arg.spike_log_path))?;
        SpikeLog::parse_from(&raw_str)
    };
    let pokedex_log = {
        let raw_str = std::fs::read(arg.pokedex_log_path.as_str())
            .into_diagnostic()
            .with_context(|| format!("reading pokedex log {}", arg.pokedex_log_path))?;

        crate::pokedex::parse_from(&raw_str)
    };
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
    spike_log: SpikeLog,
    pokedex_log: PokedexLog,
    end_mmio_address: u32,
}

impl DiffTest {
    fn run(&self) -> DiffMeta {
        assert!(!self.spike_log.is_empty());
        assert!(!self.pokedex_log.is_empty());

        let mut pokedex_log_cursor = 0;
        let mut is_reset = false;

        // spike contains vendored bootrom but doesn't provide a way to remove it.
        // so we need to compares commit log from when the emulator run reset_vector
        let reset_vector_addr = self
            .pokedex_log
            .iter()
            .find_map(|event| event.get_reset_vector())
            .unwrap_or_else(|| {
                unreachable!("reset_vector event not found");
            });
        let test_end_pc = self.spike_log.has_memory_write_commits().find_map(|log| {
            let (write_address, _) = log.commits.get_mem_write().unwrap();
            if write_address == self.end_mmio_address {
                Some(log.pc)
            } else {
                None
            }
        });
        let Some(end_pc) = test_end_pc else {
            return DiffMeta::failed("Can't find any end pattern from spike log");
        };

        for spike_event in self.spike_log.iter() {
            if !is_reset {
                if spike_event.pc == reset_vector_addr {
                    is_reset = true;
                } else {
                    continue;
                }
            }

            if spike_event.pc == end_pc {
                break;
            }

            // ignore memory read write only commits
            if !spike_event.commits.have_state_changed() {
                continue;
            }

            let search_result = self.pokedex_log[pokedex_log_cursor..]
                .iter()
                .enumerate()
                .find_map(|(i, event)| {
                    let pc = event.get_pc()?;

                    if pc == spike_event.pc {
                        pokedex_log_cursor += i + 1;
                        Some(event)
                    } else {
                        None
                    }
                });

            let Some(search_result) = search_result else {
                // Event at Spike side doesn't found at Pokedex side
                return DiffMeta::failed(indoc::formatdoc! {"
                At PC={:#010x} spike have following commit events that are not occur at simulator side:

                {spike_event}
                ", spike_event.pc
                });
            };

            // we must ensure we have already handle diff test for event at this PC
            let mut is_retired = false;

            if let PokedexEventKind::Register {
                reg_idx, data, pc, ..
            } = search_result
            {
                is_retired = spike_event.commits.iter().any(|event| {
                    if let spike::LoadStoreType::XReg { index, value } = event {
                        index == reg_idx && value == data
                    } else {
                        false
                    }
                });

                if !is_retired {
                    return DiffMeta::failed(indoc::formatdoc! {"
                        At PC={pc:#010x} simulator write {data:#010x} to register x{reg_idx},
                        but this action is mismatch at spike side.

                        ------------
                        |Event Dump|
                        ------------

                        We get simulator:
                        {search_result}

                        But have spike:
                        {spike_event}
                    "});
                }
            };

            if let PokedexEventKind::Csr {
                action,
                pc,
                csr_idx,
                csr_name,
                data,
            } = search_result
            {
                is_retired = spike_event.commits.iter().any(|event| {
                    if let spike::LoadStoreType::Csr {
                        index,
                        name: _,
                        value,
                    } = event
                    {
                        index == csr_idx && value == data
                    } else {
                        false
                    }
                });

                if !is_retired {
                    return DiffMeta::failed(indoc::formatdoc! {"
                        At PC={pc:#010x} simulator {action} {data:#010x} to CSR {csr_name},
                        but this action is mismatch at spike side.

                        ------------
                        |Event Dump|
                        ------------

                        We get simulator:
                        {search_result}

                        But have spike:
                        {spike_event}
                    "});
                }
            }

            if let PokedexEventKind::FpReg {
                action,
                pc,
                reg_idx,
                data,
            } = search_result
            {
                is_retired = spike_event.commits.iter().any(|event| {
                    if let spike::LoadStoreType::FReg { index, value } = event {
                        index == reg_idx && value == data
                    } else {
                        false
                    }
                });

                if !is_retired {
                    return DiffMeta::failed(indoc::formatdoc! {"
                        At PC={pc:#010x} simulator {action} {data:#010x} to FP register f{reg_idx},
                        but this action is mismatch at spike side.

                        ------------
                        |Event Dump|
                        ------------

                        We get simulator:
                        {search_result}

                        But have spike:
                        {spike_event}
                    "});
                }
            }

            if !is_retired {
                let msg = indoc::formatdoc! {"
              An internal error occur: event found at Pokedex and Spike side,
              but no differtial test was proceed.
              Spike: {spike_event}
              Pokedex: {search_result}
            "};

                panic!("{}", msg);
            }
        }

        DiffMeta::passed()
    }
}
