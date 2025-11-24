use std::process::ExitCode;

use clap::{Parser, Subcommand};

mod bus;
mod common;
mod difftest;
mod gdb;
mod model;
mod pokedex;
mod util;

#[derive(clap::Parser)]
struct Args {
    #[command(subcommand)]
    command: Commands,
}

#[derive(Subcommand)]
enum Commands {
    Run(pokedex::RunArgs),
    Debug(gdb::GdbArgs),
    Difftest(difftest::DiffTestArgs),
}

fn main() -> anyhow::Result<ExitCode> {
    let args = Args::parse();

    match &args.command {
        Commands::Run(args) => pokedex::run_subcommand(args),
        Commands::Debug(args) => gdb::run_subcommand(args),
        Commands::Difftest(args) => difftest::run_subcommand(args),
    }
}
