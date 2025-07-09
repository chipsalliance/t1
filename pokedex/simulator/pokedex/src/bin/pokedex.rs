use clap::Parser;
use pokedex::{SimulationException, SimulatorParams};
use std::str::FromStr;
use std::{fmt::Display, path::Path};
use tracing::{event, Level};
use tracing_subscriber::{layer::Filter, prelude::*, EnvFilter};

const VERBOSITY_WRITE_TRACE: u8 = 2;

/// Simple program to greet a person
#[derive(Parser, Debug)]
#[command(version, about, long_about = None)]
struct Args {
    /// Name of the person to greet
    #[arg(short, long)]
    elf_path: String,

    /// Number of times to greet
    #[arg(short, long, default_value_t = MemorySize(0xa000_0000))]
    memory_size: MemorySize,

    /// Exit when same instruction occur N time
    #[arg(long, default_value_t = 50)]
    max_same_instruction: u8,

    #[arg(short, long, action = clap::ArgAction::Count)]
    verbose: u8,

    #[arg(short = 'o', long, default_value_t = String::from("./pokedex-sim-events.jsonl"))]
    output_log_path: String,
}

#[derive(Debug, Clone)]
struct MemorySize(usize);
impl MemorySize {
    fn to_usize(self) -> usize {
        self.0
    }

    fn as_usize(&self) -> usize {
        self.0
    }
}

impl Display for MemorySize {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.as_usize())
    }
}

impl From<usize> for MemorySize {
    fn from(value: usize) -> Self {
        Self(value)
    }
}

impl FromStr for MemorySize {
    type Err = String;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        if s.starts_with("0x") {
            let raw = s.trim_start_matches("0x");
            let final_mem_size: usize = match raw.len() {
                8 => {
                    let result = u32::from_str_radix(raw, 16);
                    if let Err(err) = result {
                        return Err(format!("invalid hex string {s}: {err}"));
                    }
                    result
                        .unwrap()
                        .try_into()
                        .expect("value is not a unsigned 32 bit value")
                }
                16 => {
                    let result = u64::from_str_radix(raw, 16);
                    if let Err(err) = result {
                        return Err(format!("invalid hex string {s}: {err}"));
                    }
                    result
                        .unwrap()
                        .try_into()
                        .expect("you specify a 64 bit value but your system doesn't support it")
                }
                _ => {
                    return Err(format!(
                        "fail decoding hex {} to usize: only support 32-bit or 64-bit memory size",
                        s
                    ));
                }
            };
            Ok(MemorySize(final_mem_size))
        } else {
            let result: Result<usize, _> = s.parse();
            if let Err(err) = result {
                Err(format!("fail converting digit {} to usize: {}", s, err))
            } else {
                Ok(MemorySize(result.unwrap()))
            }
        }
    }
}

struct OnlyTrace;
impl<S> Filter<S> for OnlyTrace {
    fn enabled(
        &self,
        meta: &tracing::Metadata<'_>,
        _: &tracing_subscriber::layer::Context<'_, S>,
    ) -> bool {
        *meta.level() == Level::TRACE
    }
}

fn setup_logging(args: &Args) {
    let stdout_log_layer = tracing_subscriber::fmt::layer()
        .without_time()
        .with_ansi(true)
        .with_line_number(false)
        .with_filter(
            EnvFilter::builder()
                .with_env_var("POKEDEX_LOG_LEVEL")
                .with_default_directive(match args.verbose {
                    0 => Level::INFO.into(),
                    _ => Level::DEBUG.into(),
                })
                .from_env_lossy(),
        );
    let registry = tracing_subscriber::registry().with(stdout_log_layer);

    let json_log = if args.verbose > VERBOSITY_WRITE_TRACE {
        let log_file = std::fs::File::create(&args.output_log_path).unwrap_or_else(|err| {
            panic!("fail to create log file {}: {}", args.output_log_path, err)
        });

        let file_log_layer = tracing_subscriber::fmt::layer()
            .json()
            .flatten_event(true)
            .without_time()
            .with_target(false)
            .with_level(false)
            .with_writer(log_file)
            .with_filter(OnlyTrace);

        Some(file_log_layer)
    } else {
        None
    };

    registry.with(json_log).init();
}

fn main() {
    let args = Args::parse();

    setup_logging(&args);

    let mut sim_handle = SimulatorParams {
        memory_size: args.memory_size.to_usize(),
        max_same_instruction: args.max_same_instruction,
        elf_path: Path::new(&args.elf_path),
    }
    .build();

    loop {
        let step_result = sim_handle.step();

        if let Err(exception) = step_result {
            match exception {
                SimulationException::Exited => {
                    event!(Level::INFO, "simulation exit successfully");
                }
                other => {
                    event!(Level::ERROR, "simulation exit with error: {other}")
                }
            };
            break;
        }
    }

    let stat = sim_handle.take_statistic();
    event!(Level::INFO, ?stat);

    if args.verbose > VERBOSITY_WRITE_TRACE {
        event!(Level::INFO, "trace log store in {}", args.output_log_path);
    }
}
