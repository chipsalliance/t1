use anyhow::Context;
use serde::Deserialize;
use std::fmt::Display;
use std::path::PathBuf;

pub type PokedexLog = Vec<PokedexEvent>;

pub fn run_process(
    args: &[String],
    elf_path: impl AsRef<std::ffi::OsStr>,
) -> anyhow::Result<PokedexLog> {
    let pokedex_exec = which::which("pokedex").with_context(|| "pokedex exec not found")?;

    let elf_path = PathBuf::from(&elf_path);

    let event_path = format!(
        "./{}-pokedex-sim-event.jsonl",
        elf_path
            .file_name()
            .expect("elf have no filename")
            .to_string_lossy()
    );

    let result = std::process::Command::new(&pokedex_exec)
        .arg("-vvv")
        .arg("--elf-path")
        .arg(&elf_path)
        .arg("--output-log-path")
        .arg(&event_path)
        .args(args)
        .output()
        .with_context(|| "fail exeuting pokedex")?;

    if !result.status.success() {
        anyhow::bail!(
            "fail to execute pokedex with args {args:?} for elf {}",
            elf_path.to_string_lossy()
        );
    }

    let trace_event =
        std::fs::read(&event_path).with_context(|| format!("fail reading {event_path}"))?;

    let pokedex_log = get_pokedex_events(&trace_event);

    Ok(pokedex_log)
}

fn get_pokedex_events(raw: impl AsRef<[u8]>) -> PokedexLog {
    String::from_utf8_lossy(raw.as_ref())
        .lines()
        .enumerate()
        .map(|(line_number, line_str)| {
            serde_json::from_str::<PokedexEvent>(line_str).unwrap_or_else(|err| {
                panic!("fail parsing pokedex log at line {line_number}: {err}")
            })
        })
        .collect()
}

#[allow(dead_code)]
#[derive(Debug, Deserialize)]
#[serde(tag = "event_type")]
pub enum PokedexEvent {
    #[serde(rename = "physical_memory")]
    PhysicalMemory {
        action: String,
        bytes: u8,
        address: u64,
    },
    #[serde(rename = "csr")]
    CSR {
        action: String,
        pc: u32,
        csr_idx: u32,
        csr_name: String,
        data: u32,
    },
    #[serde(rename = "register")]
    Register {
        action: String,
        pc: u32,
        reg_idx: u8,
        data: u32,
    },
    #[serde(rename = "instruction_fetch")]
    InstructionFetch { data: u32 },
    #[serde(rename = "reset_vector")]
    ResetVector { new_addr: u32 },
}

impl Display for PokedexEvent {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Register {
                action,
                pc,
                reg_idx,
                data,
            } => indoc::writedoc!(
                f,
                "PC={pc:#010x} {action} to register [x{reg_idx}] with [{data:#010x}]"
            ),
            _ => write!(f, "{self:#?}"),
        }
    }
}

impl PokedexEvent {
    pub fn get_reset_vector(&self) -> Option<u32> {
        match self {
            Self::ResetVector { new_addr } => Some(*new_addr),
            _ => None,
        }
    }

    pub fn get_pc(&self) -> Option<u32> {
        match self {
            Self::CSR { pc, .. } => Some(pc.clone()),
            Self::Register { pc, .. } => Some(pc.clone()),
            _ => None,
        }
    }
}

#[test]
fn test_parsing_pokedex_log() {
    let mut d = std::path::PathBuf::from(env!("CARGO_MANIFEST_DIR"));
    d.push("assets/pokedex-sim-event.jsonl.example");
    let sample_log = std::fs::read(d).unwrap();
    assert!(!sample_log.is_empty());
    let log = get_pokedex_events(sample_log);
    assert!(!log.is_empty());
    dbg!(log);
}
