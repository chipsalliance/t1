use std::{
    fs::File,
    io::{BufWriter, Write as _},
    path::{Path, PathBuf},
    process::ExitCode,
};

use anyhow::Context;
use clap::Parser;
use tracing::{Level, error, event, info};
use tracing_subscriber::{EnvFilter, prelude::*};

use crate::{
    common::{CommitLog, PokedexLog, StateWrite},
    pokedex::simulator::StepDetail,
};

use self::{
    bus::{AddressSpaceDescNode, Bus},
    ffi::VTable,
    simulator::{Inst, Simulator},
};

mod bus;
mod ffi;
mod simulator;

/// Simple program to greet a person
#[derive(Parser, Debug)]
#[command(version, about, long_about = None)]
pub struct RunArgs {
    /// Path to the RISC-V ELF for pokedex to execute
    elf_path: PathBuf,

    /// Path to KDL configuration file
    #[arg(short = 'c', long)]
    config_path: String,

    /// Control verbosity of pokedex output
    #[arg(short, long, action = clap::ArgAction::Count)]
    verbose: u8,

    /// Write trace json log to output path
    #[arg(short = 'o', long)]
    output_log_path: Option<PathBuf>,

    /// Write trace log to stdout in human readable format
    #[arg(long)]
    stdout: bool,
}

fn setup_logging(verbose: u8) {
    let stdout_log_layer = tracing_subscriber::fmt::layer()
        .without_time()
        .with_ansi(true)
        .with_line_number(false)
        .with_filter(
            EnvFilter::builder()
                .with_env_var("POKEDEX_LOG_LEVEL")
                .with_default_directive(match verbose {
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

pub fn run_subcommand(args: &RunArgs) -> anyhow::Result<ExitCode> {
    setup_logging(args.verbose);

    let vtable = match std::env::var("POKEDEX_MODEL_DYLIB") {
        Ok(so_path) => VTable::from_dylib(&so_path),
        Err(_) => {
            #[cfg(not(feature = "bundled-model-lib"))]
            {
                error!("env POKEDEX_MODEL_DYLIB not set");
                error!("pokedex is not compiled with a bundled model lib");
                return Ok(ExitCode::FAILURE);
            }

            #[cfg(feature = "bundled-model-lib")]
            {
                info!("env POKEDEX_MODEL_DYLIB not set, using bundled version");
                VTable::bundled()
            }
        }
    };

    let mut tracer_ = match &args.output_log_path {
        Some(path) => {
            AppTracer::json_log(path).with_context(|| format!("failed to open {path:?}"))?
        }
        None => {
            if args.stdout {
                AppTracer::stdout()
            } else {
                AppTracer::noop()
            }
        }
    };
    let tracer = tracer_.as_tracer();

    let config_content = std::fs::read_to_string(&args.config_path)
        .with_context(|| format!("failed to read {:?}", args.config_path))?;
    let config: PokedexConfig = knuffel::parse(&args.config_path, config_content.as_str())?;

    let bus = Bus::try_from_config(&[
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

    let mut sim = Simulator::new(
        vtable,
        bus,
        simulator::Config {
            max_same_instruction: config.max_same_instruction,
        },
    );

    info!("running case: {:?}", args.elf_path);
    let elf_entry = load_elf(&mut sim.global.bus, &args.elf_path);

    // if config defines reset vector, use it, otherwise use ELF entrypoint
    let reset_vector = config.reset_vector.unwrap_or(elf_entry);
    sim.reset_core(reset_vector);
    tracer.trace_reset(reset_vector);

    let exit_code;
    loop {
        if let Some(code) = sim.is_exited() {
            if code == 0 {
                info!("simulation exit with exit code {code}");
            } else {
                error!("simulation exit with exit code {code}");
            }
            exit_code = code;
            tracer.trace_exit(code);
            break;
        }
        let step_result = sim.step_trace();
        tracer.trace_step(step_result);

        // std::thread::sleep(std::time::Duration::from_millis(1000));
    }

    tracer.flush();

    let stats = sim.stats();
    event!(Level::INFO, ?stats);

    if let Some(log_path) = &args.output_log_path {
        info!("trace log store in {}", log_path.display());
    }

    if exit_code == 0 {
        Ok(ExitCode::SUCCESS)
    } else {
        Ok(ExitCode::FAILURE)
    }
}

// return the ELF entrypoint if success
fn load_elf(bus: &mut bus::Bus, elf_path: &Path) -> u32 {
    use xmas_elf::{
        ElfFile, header,
        program::{ProgramHeader, Type},
    };

    let buffer = std::fs::read(elf_path)
        .expect("fail reading elf file to memory, maybe a broken file system or file too large");

    let elf_file =
        ElfFile::new(&buffer).unwrap_or_else(|err| panic!("fail serializing ELF file: {err}"));

    let header = elf_file.header;
    if header.pt2.machine().as_machine() != header::Machine::RISC_V {
        panic!("ELF is not built for RISC-V")
    }

    for ph in elf_file.program_iter() {
        if let ProgramHeader::Ph32(ph) = ph {
            if ph.get_type() == Ok(Type::Load) {
                let offset = ph.offset as usize;
                let size = ph.file_size as usize;
                let addr = ph.virtual_addr;

                let slice = &buffer[offset..offset + size];

                if let Err(err) = bus.write(addr, slice) {
                    panic!("fail loading elf to memory: {err:?}, addr={addr:#x}, size={size:#x}");
                };
            }
        }
    }

    header
        .pt2
        .entry_point()
        .try_into()
        .expect("return ELF address should be in u32 range")
}

pub enum AppTracer {
    JsonFile(JsonFileTracer),
    Stdout(StdoutTracer),
    None(NoopTracer),
}

impl AppTracer {
    pub fn json_log(path: &Path) -> Result<Self, std::io::Error> {
        JsonFileTracer::open(path).map(Self::JsonFile)
    }
    pub fn stdout() -> Self {
        Self::Stdout(StdoutTracer)
    }
    pub fn noop() -> Self {
        Self::None(NoopTracer)
    }

    pub fn as_tracer(&mut self) -> &mut dyn Tracer {
        match self {
            Self::JsonFile(tracer) => tracer,
            Self::Stdout(tracer) => tracer,
            Self::None(tracer) => tracer,
        }
    }
}

pub trait Tracer {
    fn trace_reset(&mut self, pc: u32);
    fn trace_exit(&mut self, exit_code: u32);
    fn trace_step(&mut self, detail: StepDetail);
    fn flush(&mut self);
}

pub struct NoopTracer;

impl Tracer for NoopTracer {
    fn trace_reset(&mut self, _pc: u32) {}
    fn trace_exit(&mut self, _exit_code: u32) {}
    fn trace_step(&mut self, _detail: StepDetail) {}
    fn flush(&mut self) {}
}

pub struct StdoutTracer;

impl StdoutTracer {
    pub const NEW: StdoutTracer = StdoutTracer;
}

impl Tracer for StdoutTracer {
    fn trace_reset(&mut self, _pc: u32) {
        todo!()
    }

    fn trace_exit(&mut self, _exit_code: u32) {
        todo!()
    }

    fn trace_step(&mut self, _detail: StepDetail) {
        todo!()
    }

    fn flush(&mut self) {}
}

pub struct JsonFileTracer {
    writer: BufWriter<File>,
}

impl JsonFileTracer {
    pub fn open(path: &Path) -> Result<Self, std::io::Error> {
        File::create(path).map(Self::from_file)
    }

    pub fn from_file(file: File) -> Self {
        Self {
            writer: BufWriter::new(file),
        }
    }

    fn write_json_line(&mut self, value: &PokedexLog) {
        serde_json::to_writer(&mut self.writer, value).expect("json log serialize failed");
        self.writer.write_all(b"\n").unwrap();
    }
}

impl Tracer for JsonFileTracer {
    fn trace_reset(&mut self, pc: u32) {
        self.write_json_line(&PokedexLog::Reset { pc });
    }

    fn trace_exit(&mut self, exit_code: u32) {
        self.write_json_line(&PokedexLog::Exit { code: exit_code });
    }

    fn trace_step(&mut self, detail: StepDetail) {
        if let Some(inst) = detail.inst {
            let (instruction, is_compressed) = match inst {
                Inst::NC(inst) => (inst, false),
                Inst::C(inst) => (inst as u32, true),
            };
            let mut writes = vec![];
            for (rd, value) in detail.changes.xreg_changes() {
                writes.push(StateWrite::Xrf { rd, value });
            }
            for (rd, value) in detail.changes.freg_changes() {
                writes.push(StateWrite::Frf { rd, value });
            }
            for rd in detail.changes.vreg_change_indices() {
                let mut value = vec![0; 32];
                detail.changes.core.read_vreg(rd, &mut value);
                writes.push(StateWrite::Vrf { rd, value });
            }
            for csr in detail.changes.csr_change_indices() {
                writes.push(StateWrite::Csr {
                    name: name_of_csr(csr).into(),
                    value: detail.changes.core.read_csr(csr),
                });
            }
            let json = PokedexLog::Commit(CommitLog {
                pc: detail.pc,
                instruction,
                is_compressed,
                states_changed: writes,
            });
            self.write_json_line(&json);
        } else {
            assert!(detail.changes.is_empty_changes())
        }
    }

    fn flush(&mut self) {
        self.writer.flush().expect("json log flush failed");
    }
}

fn name_of_csr(csr: u16) -> &'static str {
    assert!(csr <= 0xFFF);

    match csr {
        // urw
        0x001 => "fflags",
        0x002 => "frm",
        0x003 => "fcsr",
        0x008 => "vstart",
        0x009 => "vxsat",
        0x00a => "vxrm",
        0x00f => "vcsr",

        // uro
        0xc20 => "vl",
        0xc21 => "vtype",
        0xc22 => "vlenb",

        // mrw
        0x300 => "mstatus",
        0x301 => "misa",
        0x304 => "mie",
        0x305 => "mtvec",
        0x310 => "mstatush",
        0x340 => "mscratch",
        0x341 => "mepc",
        0x342 => "mcause",
        0x344 => "mip",

        // mro
        0xf11 => "mvendorid",
        0xf12 => "marchid",
        0xf13 => "mimpid",
        0xf14 => "mhartid",
        0xf15 => "mconfigptr",

        _ => panic!("unknown csr {csr:#05x}"),
    }
}
