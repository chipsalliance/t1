mod difftest;

use clap::Parser;
use difftest::Difftest;
use difftest::SpikeHandle;
use std::path::Path;
use tracing::{info, Level};
use tracing_subscriber::{EnvFilter, FmtSubscriber};

/// A simple offline difftest tool
#[derive(Parser, Debug)]
#[command(author, version, about, long_about = None)]
struct Args {
	/// Path to the ELF file
	#[arg(short, long)]
	elf_file: String,

	/// Path to the log file
	#[arg(short, long)]
	log_file: Option<String>,

	/// Path to the config file
	#[arg(short, long)]
	config_file: Option<String>,
}

fn main() -> anyhow::Result<()> {
	let global_logger = FmtSubscriber::builder()
		.with_env_filter(EnvFilter::from_default_env())
		.with_max_level(Level::TRACE)
		.without_time()
		.with_target(false)
		.compact()
		.finish();
	tracing::subscriber::set_global_default(global_logger)
		.expect("internal error: fail to setup log subscriber");

	let args = Args::parse();

	// count the instruction
	let mut count: u64 = 0;

	// if there is no log file, just run spike and quit
	if args.log_file.is_none() {
		let spike = SpikeHandle::new(1usize << 32, Path::new(&args.elf_file));
		loop {
			count += 1;
			if count % 1000000 == 0 {
				info!("count = {}", count);
			}
			match spike.exec() {
				Ok(_) => {}
				Err(_) => {
					info!("total count = {}", count);
					info!("Simulation quit graceful");
					return Ok(());
				}
			};
		}
	}

	// if there is a log file, run difftest
	let mut diff = Difftest::new(
		1usize << 32,
		args.elf_file,
		args.log_file.unwrap(),
		args.config_file.unwrap(),
	);

	loop {
		count += 1;
		match diff.diff() {
			Ok(_) => {}
			Err(_) => {
				info!("total count = {}", count);
				info!("Simulation quit graceful");
				return Ok(());
			}
		}
	}
}
