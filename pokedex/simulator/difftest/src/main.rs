use clap::Parser;
use miette::{Context, IntoDiagnostic};
use serde::Serialize;

mod pokedex;
mod replay;
mod spike_parser;
mod util;

#[derive(clap::Parser, Debug)]
#[command(version, about, long_about = None)]
struct DiffTestArgs {
    /// Path to the Spike commit log
    #[arg(short = 's', long)]
    spike_log_path: String,
    /// Path to the pokedex trace log
    #[arg(short = 'p', long)]
    pokedex_log_path: String,
    /// Output path for writing difftest result
    #[arg(short = 'o', long)]
    output_path: String,
}

fn main() -> miette::Result<()> {
    let arg = DiffTestArgs::try_parse().into_diagnostic()?;
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

    let result = diff_against_pokedex_spike(&pokedex_log, &spike_log);

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

    fn failed(ctx: String) -> Self {
        Self {
            is_same: false,
            context: ctx.to_string(),
        }
    }
}

fn diff_against_pokedex_spike(
    pokedex_log: &[pokedex::InsnCommit],
    spike_log: &[spike_parser::Commit],
) -> DiffMeta {
    let mut pokedex_log = pokedex_log.iter().peekable();
    let mut pokedex_cassette = replay::CommitCassette::new(&mut pokedex_log);

    let mut spike_log = spike_log.iter().peekable();
    let mut spike_cassette = replay::CommitCassette::new(&mut spike_log);

    loop {
        if pokedex_cassette.get_state().is_reset {
            break;
        }
        pokedex_cassette.roll();
    }

    if !pokedex_cassette.get_state().is_reset {
        panic!("internal error: pokedex have no reset event");
    };

    let reset_pc = pokedex_cassette.get_state().reset_vector;
    assert!(pokedex_cassette.roll_until(reset_pc));
    assert!(spike_cassette.roll_until(reset_pc));

    while let Some(dr1) = spike_cassette.roll() {
        let Some(dr2) = pokedex_cassette.roll() else {
            panic!("internal error: pokedex log ends before spike")
        };

        if pokedex_cassette.get_state().is_poweroff {
            break;
        }

        let combined_dr = replay::DiffRecord::combine(&dr1, &dr2);

        let pokedex_state = pokedex_cassette.get_state();
        let spike_state = spike_cassette.get_state();

        if !combined_dr.compare(spike_state, pokedex_state) {
            let pc = spike_state.pc;
            return DiffMeta::failed(indoc::formatdoc! {"
                            ======================================================
                            Error: difftest error after pc={pc:#010x}
                            ======================================================

                            <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
                            Spike Dump:
                            {spike_state}
                            ======================================================
                            Pokedex Dump:
                            {pokedex_state}
                            >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
                        "});
        };
    }

    DiffMeta::passed()
}
