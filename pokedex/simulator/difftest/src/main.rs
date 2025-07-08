use anyhow::Context;
use clap::Parser;
use pokedex::{PokedexEvent, PokedexLog};
use serde::Deserialize;
use serde::Deserializer;
use spike::SpikeLog;
use tracing::{error, info};
use tracing_subscriber;

mod pokedex;
mod spike;

/// Describe the expected memory behavior for program exit
#[derive(Debug, Deserialize)]
struct EndPattern {
    // Memory action a EndPattern should capture. Support only "write" now.
    #[allow(dead_code)]
    action: String,
    // Hex string with "0x" prefix indicate the memory address a EndPattern should
    // capture
    #[serde(deserialize_with = "hex_to_u32")]
    memory_address: u32,
    // Hex string with "0x" prefix indicate the data value should be capture on the given memory address
    #[serde(deserialize_with = "hex_to_u32")]
    data: u32,
}

fn hex_to_u32<'de, D>(de: D) -> Result<u32, D::Error>
where
    D: Deserializer<'de>,
{
    let val: &str = Deserialize::deserialize(de)?;
    u32::from_str_radix(val.trim_start_matches("0x"), 16)
        .map_err(|err| serde::de::Error::custom(format!("cannot convert hex value to u64: {err}")))
}

#[derive(Debug, Deserialize)]
struct CaseConfig {
    elf_path_glob: String,
    pokedex_args: Vec<String>,
    spike_args: Vec<String>,
    end_pattern: EndPattern,
}

#[derive(clap::Parser, Debug)]
#[command(version, about, long_about = None)]
struct DiffTestArgs {
    #[arg(short = 'c', long, default_value_t = String::from("./sail_difftest_config.json"))]
    config_path: String,
}

fn main() -> anyhow::Result<()> {
    let args = DiffTestArgs::parse();

    tracing_subscriber::fmt()
        .pretty()
        .with_file(false)
        .with_line_number(false)
        .without_time()
        .init();

    let cfg_raw =
        std::fs::read(args.config_path).with_context(|| "fail to read sail difftest config")?;
    let cfg: CaseConfig =
        serde_json::from_slice(&cfg_raw).with_context(|| "fail to parse sail difftest config")?;

    let all_elf_files = glob::glob(&cfg.elf_path_glob)
        .with_context(|| format!("invalid path glob {}", cfg.elf_path_glob))?;

    let mut errors = Vec::new();
    for path in all_elf_files {
        let path = path.with_context(|| "internal error: glob leads to unreadable path")?;

        info!("running difftest for {path:?}");

        let spike_log = spike::run_process(&cfg.spike_args, &path)?;
        let pokedex_log = pokedex::run_process(&cfg.pokedex_args, &path)?;
        let diff_result = diff(&spike_log, &pokedex_log, &cfg.end_pattern);

        if !diff_result.is_same {
            errors.push((path, diff_result.context));
        } else {
            info!("difftest pass")
        }
    }

    if errors.is_empty() {
        return Ok(());
    }

    for (path, err) in errors {
        error!("{}\n{}", path.display(), err)
    }

    anyhow::bail!("difftest fail")
}

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

fn diff(spike_log: &SpikeLog, pokedex_log: &PokedexLog, end_pattern: &EndPattern) -> DiffMeta {
    assert!(!spike_log.is_empty());
    assert!(!pokedex_log.is_empty());

    let mut pokedex_log_cursor = 0;
    let mut is_reset = false;

    // spike contains vendored bootrom but doesn't provide a way to remove it.
    // so we need to compares commit log from when the emulator run reset_vector
    let reset_vector_addr = pokedex_log
        .iter()
        .find_map(|event| event.get_reset_vector())
        .unwrap_or_else(|| {
            unreachable!("reset_vector event not found");
        });
    let test_end_pc = spike_log
        .has_memory_write_commits()
        .into_iter()
        .find_map(|log| {
            let (write_address, write_data) = log.commits.get_mem_write().unwrap();
            if write_address == end_pattern.memory_address && write_data == end_pattern.data {
                Some(log.pc)
            } else {
                None
            }
        });
    let Some(end_pc) = test_end_pc else {
        return DiffMeta::failed("Can't find any end pattern from spike log");
    };

    for spike_event in spike_log.iter() {
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
        if !spike_event.has_reg_write_commit() {
            continue;
        }

        let search_result = pokedex_log[pokedex_log_cursor..]
            .iter()
            .enumerate()
            .find_map(|(i, event)| {
                let Some(pc) = event.get_pc() else {
                    return None;
                };

                if pc == spike_event.pc {
                    pokedex_log_cursor = i;
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

        if let PokedexEvent::Register {
            reg_idx, data, pc, ..
        } = search_result
        {
            let match_event = spike_event
                .get_register_write_commits()
                .into_iter()
                .find(|event| {
                    let (idx, value) = event.get_register().unwrap();
                    idx == *reg_idx && value == *data
                });

            if match_event.is_none() {
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

            is_retired = true;
        };

        if let PokedexEvent::CSR {
            action,
            pc,
            csr_idx,
            csr_name,
            data,
        } = search_result
        {
            let match_event = spike_event
                .get_csr_write_commits()
                .into_iter()
                .find(|event| {
                    let (index, _, value) = event.get_csr().unwrap();
                    index == *csr_idx && value == *data
                });

            if match_event.is_none() {
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

            is_retired = true;
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
