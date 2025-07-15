use std::io::Read;
use std::marker::PhantomData;
use std::sync::atomic::{AtomicU32, Ordering};
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

pub struct SimulatorParams<'a> {
    pub memory_size: usize,
    pub max_same_instruction: u8,
    pub elf_path: &'a std::path::Path,
}

impl<'a> SimulatorParams<'a> {
    pub fn build(self) -> Simulator {
        let mut sim = Simulator::new(self.memory_size, self.max_same_instruction);

        let entry = sim.load_elf(self.elf_path);
        sim.reset_vector(entry);

        sim
    }
}

// simulator states not in ASL side
pub(crate) struct SimulatorState {
    memory: Vec<u8>,
    pc: u32,
    statistic: Statistic,
    exception: Option<SimulationException>,

    last_instruction: u32,
    last_instruction_met_count: u8,
    max_same_instruction: u8,
}

pub struct Simulator {
    // represent states in ASL side
    handle: SimulatorHandle,

    // represent states not in ASL side
    state: SimulatorState,
}

impl Simulator {
    fn new(memory_size: usize, max_same_instruction: u8) -> Self {
        let handle = SimulatorHandle::new();
        let state = SimulatorState {
            memory: vec![0u8; memory_size],
            pc: 0x1000,
            statistic: Statistic::new(),
            exception: None,
            last_instruction: 0,
            last_instruction_met_count: 0,
            max_same_instruction,
        };

        Simulator { handle, state }
    }

    pub fn reset_vector(&mut self, addr: u32) {
        self.handle.reset_states();
        self.handle.reset_vector(addr);
        self.state.pc = addr;

        event!(Level::DEBUG, "reset vector addr to {:#010x}", addr);
        event!(Level::TRACE, event_type = "reset_vector", new_addr = addr,);
    }

    /// `step` drive the ASL model to fetch and execute instruction once,
    /// then do book-keeping operations.
    pub fn step(&mut self) -> Result<(), SimulationException> {
        self.handle.step(&mut self.state);

        if let Some(exception) = &self.state.exception {
            return Err(exception.clone());
        }

        self.state.statistic.instruction_count += 1;
        self.state.statistic.step_count += 1;
        self.state.pc = self.handle.get_pc();

        Ok(())
    }

    pub fn load_elf<P: AsRef<std::path::Path> + std::fmt::Debug>(&mut self, fname: P) -> u32 {
        let mut file = std::fs::File::open(&fname)
            .unwrap_or_else(|err| panic!("fail open '{fname:?}' to read: {err}"));
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

                    let dst: &mut _ = &mut self.state.memory[addr..addr + size];
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

    pub fn take_statistic(&mut self) -> Statistic {
        std::mem::take(&mut self.state.statistic)
    }
}

// callback for ASL generated code
impl SimulatorState {
    pub(crate) fn inst_fetch(&mut self, pc: u32) -> Option<u32> {
        let inst: u32 = u32::from_le_bytes(self.phy_readmem(pc)?).into();
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

        event!(
            Level::TRACE,
            event_type = "instruction_fetch",
            data = inst,
            encoding = format!("{:#010x}", inst)
        );

        Some(inst)
    }

    /// [`phy_readmem`] is `N` length u8 array starting from `address`.
    ///
    /// [`phy_readmem`] will panic in following circumstance:
    ///   * if the u32 `address` fail convert into usize;
    pub(crate) fn phy_readmem<const N: usize>(&self, address: u32) -> Option<[u8; N]> {
        assert!(N != 0 && N % 2 == 0);

        let idx: usize = address.try_into().unwrap_or_else(|_| {
            panic!(
                "phy_readmem: internal error occur: fail to convert address {} to usize type",
                address
            )
        });

        let last_idx = self.memory.len() - 1;
        // N is not possible to be zero, so idx cannot be last index
        if idx >= last_idx && idx + N > last_idx {
            return None;
        }

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

        Some(data)
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
    pub(crate) fn phy_write_mem<T, const N: usize>(&mut self, address: u32, value: T) -> bool
    where
        T: num::ToPrimitive
            + num::traits::ToBytes<Bytes = [u8; N]>
            + PartialEq
            + Eq
            + std::fmt::LowerHex,
    {
        assert!(N != 0 && N % 2 == 0);

        let hex_value = format!("{:#x}", value);
        event!(
            Level::TRACE,
            event_type = "physical_memory",
            action = "write",
            bytes = N,
            address = address,
            data = hex_value,
            "write {N} bytes data {hex_value} to physical memory address: {:#x}",
            address
        );

        const EXIT_ADDR: u32 = 0x40000000;

        // we can safely unwrap here since we already type check the size of input `N`
        if address == EXIT_ADDR {
            event!(Level::DEBUG, "exit address got written, exit simulator");
            self.exception = Some(SimulationException::Exited);
            return true;
        }

        let mem_last = self.memory.len() - 1;

        let idx: usize = address.try_into().unwrap();

        if idx >= mem_last || idx + N > mem_last {
            return false;
        }

        // data bit width has trait constraint, so we are free to not check it
        let data = value.to_le_bytes();
        for i in 0..N {
            self.memory[idx + i] = data[i];
        }

        return true;
    }

    pub fn take_statistic(&mut self) -> Statistic {
        let stat = self.statistic.clone();
        self.statistic = Statistic::new();
        stat
    }

    pub(crate) fn write_register(&self, reg_idx: u8, value: u32) {
        event!(
            Level::TRACE,
            event_type = "register",
            action = "write",
            pc = self.pc,
            reg_idx = reg_idx,
            data = value,
        );
    }

    pub(crate) fn write_csr(&self, idx: u32, name: &str, value: u32) {
        event!(
            Level::TRACE,
            event_type = "csr",
            action = "write",
            pc = self.pc,
            csr_idx = idx,
            csr_name = name,
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

    pub(crate) fn write_pending_interrupt(&self) -> u32 {
        return 0;
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

// SimulatorHandle is a proxy object for ASL generated code.
// It is a ZST, since all states live in ASL side as global variables.
//
// At most one SimulatorHandle could exist at one time,
// protected by [`SIMULATOR_MUTEX`]`. No worry for data race.
pub struct SimulatorHandle {
    // prevent Send/Sync auto implementation.
    phantom: PhantomData<*mut ()>,
}

// TODO: make sure ASL generated code never uses threadlocal
// impl Send for SimulatorHandle {}

// TODO: check all method taking &Self does not introduce data race.
// impl Sync for SimulatorHandle {}

// We never blockingly wait, thus no need to use a real mutex.
// 0: unlocked, 1: locked.
static SIMULATOR_MUTEX: AtomicU32 = AtomicU32::new(0);

impl SimulatorHandle {
    pub fn new() -> Self {
        // try lock the mutex, panic when failing
        if SIMULATOR_MUTEX.swap(1, Ordering::AcqRel) != 0 {
            panic!("Simulator is built twice")
        }

        Self {
            phantom: PhantomData,
        }
    }
}

impl Drop for SimulatorHandle {
    fn drop(&mut self) {
        // unlock the mutex
        SIMULATOR_MUTEX.store(0, Ordering::Release);
    }
}
