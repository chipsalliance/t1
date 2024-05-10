mod difftest;

use clap::Parser;
use difftest::Difftest;
use difftest::SpikeHandle;
use std::path::Path;
use tracing::Level;
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

	// dont run difftest
	if args.log_file.is_none() {
		let spike = SpikeHandle::new(1usize << 32, Path::new(&args.elf_file));
		let mut count = 0;
		loop {
			count += 1;
			if count % 1000000 == 0 {
				tracing::info!("count = {}", count);
			}
			match spike.exec() {
				Ok(_) => {}
				Err(_) => {
					tracing::info!("Simulation quit ungraceful!!");
				}
			};
		}
	}

	let mut diff = Difftest::new(
		1usize << 32,
		args.elf_file,
		args.log_file.unwrap(),
		args.config_file.unwrap(),
	);

	loop {
		diff.diff().unwrap();
	}
}
