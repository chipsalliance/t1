use clap::Parser;
use pokedex::{SimulationException, SimulatorParams};
use tracing::{event, Level};
use tracing_subscriber::{layer::Filter, prelude::*, EnvFilter};

const VERBOSITY_WRITE_TRACE: u8 = 2;

/// Simple program to greet a person
#[derive(Parser, Debug)]
#[command(version, about, long_about = None)]
struct Args {
    /// Path to ELF
    #[arg(short, long)]
    elf_path: String,

    /// Path to device tree KDL configuration
    #[arg(short, long)]
    dts_cfg_path: String,

    /// Exit when same instruction occur N time
    #[arg(long, default_value_t = 50)]
    max_same_instruction: u8,

    #[arg(short, long, action = clap::ArgAction::Count)]
    verbose: u8,

    #[arg(short = 'o', long, default_value_t = String::from("./pokedex-sim-events.jsonl"))]
    output_log_path: String,

    #[arg(long, default_value_t = 0)]
    slow_motion_ms: u8,
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
        max_same_instruction: args.max_same_instruction,
        dts_cfg_path: &args.dts_cfg_path,
        elf_path: &args.elf_path,
    }
    .try_build()
    .unwrap_or_else(|err| {
        eprintln!("{err:?}");
        std::process::exit(1)
    });

    let mut exit_code = 0;
    loop {
        let step_result = sim_handle.step();

        if let Err(exception) = step_result {
            match exception {
                SimulationException::Exited(ret_code) => {
                    if ret_code == 0 {
                        event!(Level::INFO, "simulation exit with exit code {ret_code}");
                    } else {
                        event!(Level::ERROR, "simulation exit with exit code {ret_code}");
                        event!(Level::INFO, "dump simulator register");
                        sim_handle.dump_regs();
                    }
                    exit_code = ret_code;
                }
                other => {
                    event!(Level::ERROR, "simulation exit with error: {other}")
                }
            };
            break;
        }

        if args.slow_motion_ms > 0 {
            std::thread::sleep(std::time::Duration::from_millis(args.slow_motion_ms.into()));
        }
    }

    let stat = sim_handle.take_statistic();
    event!(Level::INFO, ?stat);

    if args.verbose > VERBOSITY_WRITE_TRACE {
        event!(Level::INFO, "trace log store in {}", args.output_log_path);
    }

    std::process::exit(exit_code);
}
