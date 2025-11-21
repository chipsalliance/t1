use std::{path::PathBuf, process::ExitCode};

use anyhow::Context;
use serde::Serialize;

use replay::{CpuState, pretty_print_diff};

use crate::difftest::replay::DiffRecord;

mod pokedex;
mod replay;
mod spike;

#[derive(clap::Parser, Debug)]
#[command(version, about, long_about = None)]
pub struct DiffTestArgs {
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

pub fn run_subcommand(args: &DiffTestArgs) -> anyhow::Result<ExitCode> {
    let mut spike_log = spike::backend_from_log(&args.spike_log_path)?;
    let mut pokedex_log = pokedex::backend_from_log(&args.pokedex_log_path)?;

    let pc = pokedex_log.get_reset_pc();

    let result = run_diff(
        &mut spike_log,
        &mut pokedex_log,
        pc,
        SamePolicy::SuccessSource2,
    )?;

    let raw_json = serde_json::to_string_pretty(&result)?;
    std::fs::write(&args.output_path, raw_json)
        .with_context(|| format!("fail to write json: {:?}", args.output_path))?;

    Ok(ExitCode::SUCCESS)
}

#[derive(Serialize)]
pub struct DiffReport {
    // Nix relies on "is_same" field, others are for humans.
    is_same: bool,

    source1: String,
    source2: String,

    exit1: Option<u32>,
    exit2: Option<u32>,

    diff_notes: Vec<String>,

    state1: Option<String>,
    state2: Option<String>,
}

fn run_diff(
    source1: &mut dyn DiffBackend,
    source2: &mut dyn DiffBackend,
    reset_pc: u32,
    same_policy: SamePolicy,
) -> anyhow::Result<DiffReport> {
    let name1 = source1.description();
    let name2 = source2.description();

    source1
        .diff_reset(reset_pc)
        .with_context(|| format!("reset source1={name1}"))?;

    source2
        .diff_reset(reset_pc)
        .with_context(|| format!("reset source2={name2}"))?;

    loop {
        let status1 = source1
            .diff_step()
            .with_context(|| format!("step srouce1={name1}"))?;

        let status2 = source2
            .diff_step()
            .with_context(|| format!("step srouce2={name2}"))?;

        match (&status1, &status2) {
            (Status::Running(dr1), Status::Running(dr2)) => {
                let state1 = source1.state();
                let state2 = source2.state();

                let combined_dr = DiffRecord::combine(dr1, dr2);

                if !combined_dr.compare(state1, state2) {
                    // difftest failed

                    let diff_string =
                        crate::util::fn_to_string(|f| pretty_print_diff(f, state1, state2));
                    let diff_notes: Vec<String> = diff_string.lines().map(|x| x.into()).collect();

                    return Ok(DiffReport {
                        is_same: false,
                        source1: name1,
                        source2: name2,
                        exit1: None,
                        exit2: None,
                        diff_notes,
                        state1: Some(state1.pretty_print_string()),
                        state2: Some(state2.pretty_print_string()),
                    });
                }
            }

            _ => {
                let exit1 = status1.get_exit_code();
                let exit2 = status2.get_exit_code();

                let is_same = same_policy.is_same(exit1, exit2);

                let mut state1 = None;
                let mut state2 = None;
                if !is_same {
                    state1 = Some(source1.state().pretty_print_string());
                    state2 = Some(source2.state().pretty_print_string());
                }

                return Ok(DiffReport {
                    is_same,
                    source1: name1,
                    source2: name2,
                    exit1,
                    exit2,
                    diff_notes: vec![],
                    state1,
                    state2,
                });
            }
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SamePolicy {
    /// both sides exit at the same time with the same code
    Strict,

    /// allow source 1 exit early, provided with exit code 0
    SuccessSource1,

    /// allow source 2 exit early, provided with exit code 0
    SuccessSource2,
}

impl SamePolicy {
    pub fn is_same(self, exit1: Option<u32>, exit2: Option<u32>) -> bool {
        // if both are none, the case should be still running
        assert!(exit1.is_some() || exit2.is_some());

        if exit1 == exit2 {
            return true;
        }

        match self {
            SamePolicy::Strict => false,
            SamePolicy::SuccessSource1 => exit1 == Some(0),
            SamePolicy::SuccessSource2 => exit2 == Some(0),
        }
    }
}

#[derive(Debug)]
pub enum Status {
    Running(DiffRecord),

    // Once it exits, should not call `LogSource::step` further.
    Exit { code: u32 },
}

impl Status {
    pub fn get_exit_code(&self) -> Option<u32> {
        match self {
            Status::Exit { code } => Some(*code),
            Self::Running(_) => None,
        }
    }
}

pub trait DiffBackend {
    fn description(&self) -> String;

    fn diff_reset(&mut self, pc: u32) -> anyhow::Result<()>;
    fn diff_step(&mut self) -> anyhow::Result<Status>;

    fn state(&self) -> &CpuState;
}
