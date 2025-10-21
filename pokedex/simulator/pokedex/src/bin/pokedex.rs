use clap::Parser;
use miette::IntoDiagnostic;
use pokedex::{AddressSpaceDescNode, BusInfo, SimulationException, SimulatorParams};
use tracing::{event, Level};
use tracing_subscriber::{prelude::*, EnvFilter};

const VERBOSITY_WRITE_TRACE: u8 = 2;

/// Simple program to greet a person
#[derive(Parser, Debug)]
#[command(version, about, long_about = None)]
struct Args {
    /// Path to the RISC-V ELF for pokedex to execute
    elf_path: String,

    /// Path to KDL configuration file
    #[arg(short = 'c', long)]
    config_path: String,

    /// Control verbosity of pokedex output
    #[arg(short, long, action = clap::ArgAction::Count)]
    verbose: u8,

    /// Write trace log to output path instead of printing to screen
    #[arg(short = 'o', long)]
    output_log_path: Option<String>,
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
                    1 => Level::DEBUG.into(),
                    _ => Level::TRACE.into(),
                })
                .from_env_lossy(),
        );
    let registry = tracing_subscriber::registry().with(stdout_log_layer);

    registry.init();
}

#[derive(Debug, knuffel::Decode)]
struct DumpConfig {
    #[knuffel(child)]
    on: bool,
    #[knuffel(child)]
    off: bool,
    #[knuffel(child, unwrap(arguments))]
    before_pc: Option<Vec<u32>>,
}

#[derive(Debug, knuffel::Decode)]
struct MmapConfig {
    #[knuffel(argument)]
    name: String,
    #[knuffel(property)]
    offset: u32,
}

#[derive(Debug, knuffel::Decode)]
struct MmioConfig {
    #[knuffel(child, unwrap(argument))]
    base: u32,
    #[knuffel(child, unwrap(argument))]
    length: u32,
    #[knuffel(children(name = "mmap"))]
    mmaps: Vec<MmapConfig>,
}

#[derive(Debug, knuffel::Decode)]
struct SramConfig {
    #[knuffel(child, unwrap(argument))]
    base: u32,
    #[knuffel(child, unwrap(argument))]
    length: u32,
}

#[derive(Debug, knuffel::Decode)]
struct PokedexConfig {
    #[knuffel(child, unwrap(argument))]
    max_same_instruction: u32,
    #[knuffel(child, unwrap(argument))]
    slow_motion_ms: u64,
    #[knuffel(child, unwrap(argument))]
    reset_vector: Option<u32>,
    #[knuffel(child)]
    dump: DumpConfig,
    #[knuffel(child)]
    mmio: MmioConfig,
    #[knuffel(child)]
    sram: SramConfig,
}

fn pretty_print_regs(pc: u32, regs: &[u32]) {
    assert_eq!(regs.len(), 32);

    const ROW_SIZE: usize = 4;
    const COLUMN_SIZE: usize = 8;

    println!();
    println!("{}", "=".repeat(80));
    println!("Register Dumps before PC {pc:#010x}:");
    for i in 0..ROW_SIZE {
        for j in 0..COLUMN_SIZE {
            let index = j + COLUMN_SIZE * i;
            let reg_val = regs[j + COLUMN_SIZE * i];
            print!("x{:<2}: {:#010x}  ", index, reg_val)
        }
        println!();
    }
    println!("{}", "=".repeat(80));
    println!();
}

fn main() -> miette::Result<()> {
    let args = Args::parse();

    setup_logging(&args);

    let config_content = std::fs::read_to_string(&args.config_path).into_diagnostic()?;

    let config: PokedexConfig = knuffel::parse(&args.config_path, config_content.as_str())?;

    let bus_info = BusInfo::try_from_config(&[
        AddressSpaceDescNode::Sram {
            name: "single-naive-memory".to_string(),
            base: config.sram.base,
            length: config.sram.length,
        },
        AddressSpaceDescNode::Mmio {
            base: config.mmio.base,
            length: config.mmio.length,
            mmap: config
                .mmio
                .mmaps
                .iter()
                .map(|mmap| (mmap.name.to_string(), mmap.offset))
                .collect(),
        },
    ])?;

    let mut sim_handle = SimulatorParams {
        max_same_instruction: config.max_same_instruction,
        elf_path: &args.elf_path,
        commit_log_path: args.output_log_path.as_deref(),
        reset_vector: config.reset_vector,
    }
    .into_simulator(bus_info);

    let mut exit_code = 0;
    loop {
        if config.dump.on
            && !config.dump.off
            && config
                .dump
                .before_pc
                .as_ref()
                .is_some_and(|pc| pc.contains(&sim_handle.current_pc()))
        {
            pretty_print_regs(sim_handle.current_pc(), &sim_handle.dump_regs());
        }

        let step_result = sim_handle.step();

        if let Err(exception) = step_result {
            match exception {
                SimulationException::Exited(ret_code) => {
                    if ret_code == 0 {
                        event!(Level::INFO, "simulation exit with exit code {ret_code}");
                    } else {
                        event!(Level::ERROR, "simulation exit with exit code {ret_code}");
                    }
                    exit_code = ret_code;
                }
                other => {
                    event!(Level::ERROR, "simulation exit with error: {other}")
                }
            };
            break;
        }

        if config.slow_motion_ms > 0 {
            std::thread::sleep(std::time::Duration::from_millis(config.slow_motion_ms));
        }
    }

    let stat = sim_handle.take_statistic();
    event!(Level::INFO, ?stat);

    if args.output_log_path.is_some() && args.verbose > VERBOSITY_WRITE_TRACE {
        event!(
            Level::INFO,
            "trace log store in {}",
            args.output_log_path.unwrap()
        );
    }

    if exit_code != 0 {
        miette::bail!("exit with {exit_code}");
    }

    Ok(())
}
