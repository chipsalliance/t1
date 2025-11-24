use std::net::{Ipv4Addr, TcpListener};
use std::path::PathBuf;
use std::process::ExitCode;

use tracing::info;
use tracing::level_filters::LevelFilter;
use tracing_subscriber::layer::SubscriberExt as _;
use tracing_subscriber::util::SubscriberInitExt as _;
use tracing_subscriber::{EnvFilter, Layer as _};

use crate::bus::Bus;
use crate::gdb::run::TargetConfig;
use crate::model::StepCode;
use crate::pokedex::simulator::Simulator;

mod arch;
mod run;

#[derive(clap::Parser)]
pub struct GdbArgs {
    #[arg(long, short, default_value = "1234")]
    port: u16,

    elf_path: PathBuf,
}

fn setup_logging() {
    let stdout_log_layer = tracing_subscriber::fmt::layer()
        .without_time()
        .with_ansi(true)
        .with_line_number(false)
        .with_filter(
            EnvFilter::builder()
                .with_default_directive(LevelFilter::INFO.into())
                .with_env_var("POKEDEX_LOG_LEVEL")
                .from_env_lossy(),
        );
    let registry = tracing_subscriber::registry().with(stdout_log_layer);

    registry.init();
}

pub fn run_subcommand(args: &GdbArgs) -> anyhow::Result<ExitCode> {
    setup_logging();

    tracing::warn!("pokedex gdb support is INCOMPLETE");

    let model_loader = crate::model::get_loader()?;
    let bus = Bus::load_from_default_config();
    let mut sim = Simulator::new(model_loader, bus);

    let entry = sim.global.bus.load_elf(&args.elf_path)?;
    sim.reset_core(entry);

    info!("waiting for a gdb connection on localhost:{}", args.port);
    let sock = TcpListener::bind((Ipv4Addr::LOCALHOST, args.port))?;
    let (stream, addr) = sock.accept()?;
    info!("gdb connection accepted, from {addr}");

    let target_xml = arch::Config {
        xlen: 32,
        flen: 32,
        vlen: 256,
    }
    .build_target_xml();

    let config = TargetConfig {
        elf_path: args.elf_path.clone(),
        target_xml,
    };

    run::run_gdb(stream, &mut sim, config)?;

    Ok(ExitCode::SUCCESS)
}

// We use this trait to isolate gdbstub and our simualtor,
// gdbstub is quite messy.
//
// FIXME: currently all method prefixed to avoid name collision
pub trait PokedexTarget {
    fn gdb_read_pc(&self) -> u32;
    fn gdb_read_xreg(&self, idx: u8) -> u32;
    fn gdb_read_freg(&self, idx: u8) -> u32;
    fn gdb_read_vreg(&self, idx: u8, data: &mut [u8]);
    fn gdb_read_csr(&self, idx: u16) -> u32;
    fn gdb_read_mem(&self, addr: u32, data: &mut [u8]) -> usize;

    fn gdb_is_exited(&self) -> Option<u32>;
    fn gdb_step_one(&mut self);
}

impl PokedexTarget for Simulator {
    fn gdb_read_pc(&self) -> u32 {
        self.core().read_pc()
    }

    fn gdb_read_xreg(&self, idx: u8) -> u32 {
        self.core().read_xreg(idx)
    }

    fn gdb_read_freg(&self, idx: u8) -> u32 {
        self.core().read_freg(idx)
    }

    fn gdb_read_vreg(&self, idx: u8, data: &mut [u8]) {
        self.core().read_vreg(idx, data);
    }

    fn gdb_read_csr(&self, idx: u16) -> u32 {
        self.core().read_csr(idx)
    }

    fn gdb_read_mem(&self, addr: u32, data: &mut [u8]) -> usize {
        self.global.bus.debugger_read(addr, data)
    }

    fn gdb_is_exited(&self) -> Option<u32> {
        self.is_exited()
    }

    fn gdb_step_one(&mut self) {
        match self.step() {
            StepCode::Committed => {}

            // FIXME: report it back to gdb
            StepCode::Exception => todo!("program terminated by exception"),

            StepCode::Interrupt => unreachable!(),
        }
    }
}
