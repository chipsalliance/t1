use std::{fs::File, io::BufReader, path::PathBuf};

use anyhow::Context;
use clap::Parser;
use fst_writer::Writer;

mod events;
mod fst_writer;
mod parse;
mod writer;

#[derive(Parser, Debug)]
struct Args {
    #[arg(value_name = "LOG_FILE")]
    log_path: PathBuf,

    #[arg(long)]
    zstd: bool,

    #[arg(long)]
    wave_path: String,
}

#[derive(Debug, Clone)]
pub struct Config {
    pub lane_count: usize,
}

fn main() -> anyhow::Result<()> {
    let args = Args::parse();
    let log_path = &args.log_path;

    let log_file =
        File::open(log_path).with_context(|| format!("failed to open '{}'", log_path.display()))?;

    // TODO: remove hard-coded config
    let config = Config { lane_count: 8 };

    let writer = Writer::new(&args.wave_path)?;
    let mut handler = writer::Handler::new(config.clone(), writer)?;
    if args.zstd {
        let zstd_stream = zstd::Decoder::new(log_file)?;
        parse::process(&mut BufReader::new(zstd_stream), &mut handler)?;
    } else {
        parse::process(&mut BufReader::new(log_file), &mut handler)?;
    }

    // writer::run()?;

    Ok(())
}
