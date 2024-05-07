mod difftest;

use clap::Parser;
use difftest::Difftest;
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
	log_file: String,

	/// Path to the config file
	#[arg(short, long)]
	config_file: String,
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

	let mut diff = Difftest::new(1usize << 32, args.elf_file, args.log_file, args.config_file);

	loop {
		diff.diff().unwrap();
	}
}
