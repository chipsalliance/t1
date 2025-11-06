use std::path::{Path, PathBuf};

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
    spike_log_path: PathBuf,
    /// Path to the pokedex trace log
    #[arg(short = 'p', long)]
    pokedex_log_path: PathBuf,
    /// Output path for writing difftest result
    #[arg(short = 'o', long)]
    output_path: PathBuf,
}

fn main() -> miette::Result<()> {
    let arg = DiffTestArgs::try_parse().into_diagnostic()?;
    let spike_log = parse_spike_log(&arg.spike_log_path)?;

    let pokedex_log = parse_pokedex_log(&arg.pokedex_log_path)?;

    let result = diff_against_pokedex_spike(&pokedex_log, &spike_log);

    let raw_json = serde_json::to_string(&result).into_diagnostic()?;
    std::fs::write(arg.output_path, raw_json).into_diagnostic()?;

    Ok(())
}

fn parse_spike_log(log_path: &Path) -> miette::Result<Vec<spike_parser::Commit>> {
    // FIXME: parse in stream

    let raw_str = std::fs::read_to_string(log_path)
        .into_diagnostic()
        .with_context(|| format!("reading spike log {}", log_path.display()))?;

    let mut spike_log = vec![];
    for (line_number, line_str) in raw_str.lines().enumerate() {
        let tokens = spike_parser::tokenize_spike_log_line(line_str);

        // Check for any Unknown tokens before starting.
        for token in &tokens {
            if let spike_parser::Token::Unknown { raw_token } = token {
                return Err(miette::miette!(
                    "fail parse spike log {}, line {line_number}: unknown token `{raw_token}`",
                    log_path.display()
                ));
            }
        }

        let commit = spike_parser::parse_single_commit(&tokens).map_err(|err| {
            miette::miette!(
                "fail parse spike log {}, line {line_number}: {err}",
                log_path.display(),
            )
        })?;

        spike_log.push(commit);
    }

    Ok(spike_log)
}

fn parse_pokedex_log(log_path: &Path) -> miette::Result<Vec<pokedex::InsnCommit>> {
    // FIXME: parse in stream

    let raw_str = std::fs::read_to_string(log_path)
        .into_diagnostic()
        .with_context(|| format!("reading pokedex log {}", log_path.display()))?;

    let mut pokedex_log = vec![];
    for (line_number, line_str) in raw_str.lines().enumerate() {
        let commit: pokedex::InsnCommit = serde_json::from_str(line_str).map_err(|err| {
            miette::miette!(
                "fail parse pokedex log {}, line {line_number}: {err}",
                log_path.display(),
            )
        })?;
        pokedex_log.push(commit);
    }

    Ok(pokedex_log)
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
                            ======================================================
                            Diff: spike <-> pokedex
                            {state_diff}
                            >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
                        ",
                spike_state = util::from_fn(|f| spike_state.pretty_print(f)),
                pokedex_state = util::from_fn(|f| pokedex_state.pretty_print(f)),
                state_diff = util::from_fn(|f| replay::pretty_print_diff(f, spike_state, pokedex_state)),
            });
        };
    }

    DiffMeta::passed()
}
