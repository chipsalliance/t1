use std::io::Read;
use std::sync::Mutex;
use thiserror::Error;
use tracing::{event, Level};
use xmas_elf::program::{ProgramHeader, Type};
use xmas_elf::{header, ElfFile};

#[derive(Error, Debug, Clone)]
pub enum SimulationException {
    #[error("fail to fetch instruction")]
    InstructionFetch,
    #[error("Same instruction occur too many time")]
    InfiniteInstruction,
    #[error("simulator exited")]
    Exited,
}

pub struct SimulatorParams {
    memory_size: usize,
    max_same_instruction: u8,
    elf_path: std::path::PathBuf,
}

impl SimulatorParams {
    pub fn to_sim_handle(
        memory_size: usize,
        max_same_instruction: u8,
        elf_path: impl AsRef<std::path::Path>,
    ) -> &'static SimulatorHandler<Simulator> {
        Self {
            memory_size,
            max_same_instruction,
            elf_path: elf_path.as_ref().into(),
        }
        .into()
    }
}

impl From<SimulatorParams> for &SimulatorHandler<Simulator> {
    fn from(params: SimulatorParams) -> Self {
        let mut sim = Simulator::new(params.memory_size, params.max_same_instruction);

        let entry = sim.load_elf(params.elf_path);

        unsafe {
            sim.reset_vector(entry);
        }

        SIM_HANDLE.init(|| sim);

        &SIM_HANDLE
    }
}

pub struct Simulator {
    memory: Vec<u8>,
    pc: u32,
    statistic: Statistic,
    exception: Option<SimulationException>,

    last_instruction: u32,
    last_instruction_met_count: u8,
    max_same_instruction: u8,
}

impl Simulator {
    fn new(memory_size: usize, max_same_instruction: u8) -> Self {
        Self {
            memory: vec![0u8; memory_size],
            pc: 0x1000,
            statistic: Statistic::new(),
            exception: None,
            last_instruction: 0,
            last_instruction_met_count: 0,
            max_same_instruction,
        }
    }

    // We can't use step here cuz the Simulator is always used in a Mutex lock, multiple simulator
    // call at the same time will lead to dead lock.
    pub fn check_step(&mut self) -> Result<(), SimulationException> {
        if let Some(exception) = &self.exception {
            return Err(exception.clone());
        }

        self.statistic.instruction_count += 1;
        self.statistic.step_count += 1;
        self.pc = crate::ffi::get_pc();

        Ok(())
    }

    pub unsafe fn reset_vector(&mut self, addr: u32) {
        unsafe { crate::ffi::reset_states() };
        unsafe { crate::ffi::reset_vector(addr) };
        self.pc = addr;

        event!(Level::DEBUG, "reset vector addr to {:#010x}", addr);
        event!(Level::TRACE, event_type = "reset_vector", new_addr = addr,);
    }

    /// `step` drive the ASL model to fetch and execute instruction once.
    pub unsafe fn step() {
        unsafe { crate::ffi::step() };
    }

    pub fn load_elf<P: AsRef<std::path::Path>>(&mut self, fname: P) -> u32 {
        let mut file = std::fs::File::open(fname)
            .unwrap_or_else(|err| panic!("fail open ELF file to read: {err}"));
        let mut buffer = Vec::new();
        file.read_to_end(&mut buffer).expect(
            "fail reading elf file to memory, maybe a broken file system or file too large",
        );

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
                    let addr = ph.virtual_addr as usize;

                    let slice = &buffer[offset..offset + size];

                    let dst: &mut _ = &mut self.memory[addr..addr + size];
                    dst.copy_from_slice(slice);
                }
            }
        }

        header
            .pt2
            .entry_point()
            .try_into()
            .unwrap_or_else(|_| panic!("return ELF address should be in u32 range"))
    }

    pub(crate) fn inst_fetch(&mut self, pc: u32) -> u32 {
        let inst: u32 = u32::from_le_bytes(self.phy_readmem(pc)).into();
        self.statistic.fetch_count += 1;

        if inst == self.last_instruction {
            self.last_instruction_met_count += 1;
        } else {
            self.last_instruction_met_count = 0;
        }

        if self.last_instruction_met_count > self.max_same_instruction {
            self.exception = Some(SimulationException::InfiniteInstruction);
        }

        self.last_instruction = inst;

        // TODO: instruction valid should be determine at ASL model side.
        if inst == 0 {
            panic!("[simulator] instruction fetch fail with zero data")
        }

        event!(
            Level::TRACE,
            event_type = "instruction_fetch",
            data = inst,
            encoding = format!("{:#010x}", inst)
        );

        inst
    }

    /// [`phy_readmem`] is `N` length u8 array starting from `address`.
    ///
    /// [`phy_readmem`] will panic in following circumstance:
    ///   * if the u64 `address` fail convert into usize;
    ///   * if `address` larger than memory length (OOM);
    ///   * if `address` plus `N` offset larger than memory length (OOM);
    pub(crate) fn phy_readmem<const N: usize>(&self, address: u32) -> [u8; N] {
        let idx: usize = address.try_into().unwrap_or_else(|_| {
            panic!(
                "phy_readmem: internal error occur: fail to convert address {} to usize type",
                address
            )
        });
        let mut data = [0u8; N];
        data.copy_from_slice(&self.memory[idx..idx + N]);

        event!(
            Level::TRACE,
            event_type = "physical_memory",
            action = "read",
            bytes = N,
            address = address,
            data = ?data,
            "read {} bytes from physical memory address: {:#x}",
            N,
            address
        );

        data
    }

    pub(crate) fn fence_i(&self) -> () {
        event!(Level::DEBUG, "fence_i called");
    }

    /// [`phy_write_mem`] write each byte of `value` in little endian order to `address` at
    /// internal physical memory.
    ///
    /// This function will panic in following circumstance:
    ///   * fail converting u64 `address` to usize
    ///   * index overflow
    pub(crate) fn phy_write_mem<T, const N: usize>(&mut self, address: u32, value: T) -> ()
    where
        T: num::ToPrimitive
            + num::traits::ToBytes<Bytes = [u8; N]>
            + PartialEq
            + Eq
            + std::fmt::LowerHex,
    {
        let hex_value = format!("{:#x}", value);
        event!(
            Level::TRACE,
            event_type = "physical_memory",
            action = "write",
            bytes = N,
            address = address,
            data = hex_value,
            "write {N} bytes data {hex_value} from physical memory address: {:#x}",
            address
        );

        const EXIT_ADDR: u32 = 0x40000000;

        // we can safely unwrap here since we already type check the size of input `N`
        if address == EXIT_ADDR {
            event!(Level::DEBUG, "exit address got written, exit simulator");
            self.exception = Some(SimulationException::Exited);
            return;
        }

        let idx: usize = address.try_into().unwrap();
        let data = value.to_le_bytes();
        for i in 0..N {
            self.memory[idx + i] = data[i];
        }
    }

    pub fn take_statistic(&mut self) -> Statistic {
        let stat = self.statistic.clone();
        self.statistic = Statistic::new();
        stat
    }

    pub(crate) fn write_register(&self, reg_idx: u8, value: u32) {
        event!(
            Level::TRACE,
            event_type = "arch_state",
            action = "register_update",
            pc = self.pc,
            reg_idx = reg_idx,
            data = value,
        );
    }

    pub(crate) fn print_string(&self, s: &str) {
        event!(Level::DEBUG, "ASL model: {s}")
    }

    pub(crate) fn print_bits_hex(&self, d: u32) {
        event!(Level::DEBUG, "ASL model: {:#010x}", d)
    }

    pub(crate) fn handle_ebreak(&self) {
        event!(Level::DEBUG, "TODO: model met ebreak")
    }

    pub(crate) fn handle_ecall(&self) {
        event!(Level::DEBUG, "TODO: model met ecall")
    }
}

#[derive(Debug, Clone, Default)]
pub struct Statistic {
    instruction_count: u64,
    fetch_count: u64,
    step_count: u64,
}

impl Statistic {
    fn new() -> Self {
        Self::default()
    }
}

pub struct SimulatorHandler<T> {
    simulator: Mutex<Option<T>>,
}

impl<T> SimulatorHandler<T> {
    pub const fn new() -> Self {
        Self {
            simulator: Mutex::new(None),
        }
    }

    #[track_caller]
    pub fn init(&self, init_fn: impl FnOnce() -> T) {
        let mut sim = self
            .simulator
            .lock()
            .expect("fail fetch lock when initializing the global simulator");
        if sim.is_some() {
            panic!("simulator initialized twice!");
        }
        *sim = Some(init_fn());
    }

    #[track_caller]
    pub fn with<R>(&self, f: impl FnOnce(&mut T) -> R) -> R {
        let mut sim = self
            .simulator
            .lock()
            .expect("fail fetch lock when referencing global simulator");
        let sim = sim.as_mut().expect("simulator is not initialized");
        f(sim)
    }

    #[track_caller]
    pub fn with_optional<R>(&self, f: impl FnOnce(Option<&mut T>) -> R) -> R {
        match self.simulator.lock() {
            Ok(mut sim) => f(sim.as_mut()),

            // treat poisoned mutex as non-initialized
            Err(_) => f(None),
        }
    }

    #[track_caller]
    pub fn dispose(&self) {
        let mut core = self
            .simulator
            .lock()
            .expect("fail fetch lock when disposing");
        if core.is_none() {
            panic!("simulator is not initialized");
        }
        *core = None;
    }
}

// For each function in [`Simulator`], we must declare a C function as external function
pub static SIM_HANDLE: SimulatorHandler<Simulator> = SimulatorHandler::new();
