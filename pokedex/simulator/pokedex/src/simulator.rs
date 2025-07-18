use miette::{Context, IntoDiagnostic};
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

pub struct SimulatorParams<'a, 'b> {
    pub max_same_instruction: u8,
    pub dts_cfg_path: &'a str,
    pub elf_path: &'b str,
}

impl<'a, 'b> SimulatorParams<'a, 'b> {
    pub fn try_build(self) -> miette::Result<Simulator> {
        let mut sim = Simulator::new(self.elf_path, self.max_same_instruction)?;

        let entry = sim.load_elf(self.elf_path);
        sim.reset_vector(entry);

        Ok(sim)
    }
}

// simulator states not in ASL side
pub(crate) struct SimulatorState {
    address_space: AddressSpace,
    pc: u32,
    statistic: Statistic,
    exception: Option<SimulationException>,

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
    fn new<P: AsRef<std::path::Path> + std::fmt::Debug>(
        dts: P,
        max_same_instruction: u8,
    ) -> miette::Result<Self> {
        let handle = SimulatorHandle::new();
        let state = SimulatorState {
            address_space: AddressSpace::from_device_config(dts)?,
            pc: 0x1000,
            statistic: Statistic::new(),
            exception: None,
            last_instruction_met_count: 0,
            max_same_instruction,
        };

        Ok(Simulator { handle, state })
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

                    let dst: &mut _ = &mut self.state.address_space[addr..addr + size];
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
    pub(crate) fn inst_fetch(&mut self, pc: u32) -> Option<u16> {
        let inst: u16 = u16::from_le_bytes(self.phy_readmem(pc)?).into();
        self.statistic.fetch_count += 1;

        if pc == self.pc {
            self.last_instruction_met_count += 1;
        } else {
            self.last_instruction_met_count = 0;
        }

        if self.last_instruction_met_count > self.max_same_instruction {
            self.exception = Some(SimulationException::InfiniteInstruction);
        }

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

#[derive(Debug, knuffel::Decode)]
enum AddressSpaceDescNode {
    MMIO(KdlNodeMMIO),
    SRAM(KdlNodeSRAM),
}

#[derive(Debug, knuffel::Decode)]
struct KdlNodeSRAM {
    #[knuffel(argument)]
    name: String,
    #[knuffel(property)]
    base: u32,
    #[knuffel(property)]
    capacity: u32,
}

#[derive(Debug, knuffel::Decode)]
struct KdlNodeMMIO {
    #[knuffel(property)]
    base: u32,
    #[knuffel(property)]
    capacity: u32,
    #[knuffel(children)]
    mapping: Vec<KdlNodeMMIOMapping>,
}

#[derive(Debug, knuffel::Decode)]
struct KdlNodeMMIOMapping {
    #[knuffel(argument)]
    name: String,
    #[knuffel(property)]
    offset: u32,
}

#[derive(Debug)]
pub struct AddressSpace {
    segments: Vec<Box<dyn Segment>>,
    index: Vec<(usize, usize)>,
}

impl AddressSpace {
    pub fn from_device_config<P: AsRef<std::path::Path> + std::fmt::Debug>(
        p: P,
    ) -> miette::Result<Self> {
        let content = std::fs::read_to_string(&p)
            .into_diagnostic()
            .wrap_err_with(|| format!("fail read path: {:?}", &p))?;

        let filepath = p.as_ref().to_string_lossy();
        let address_space: Vec<AddressSpaceDescNode> =
            knuffel::parse(&filepath, &content).into_diagnostic()?;

        let (segments, index): (Vec<Box<dyn Segment>>, Vec<(usize, usize)>) = address_space
            .iter()
            .map(|node| match node {
                AddressSpaceDescNode::SRAM(sram) => {
                    let naive_memory = NaiveMemory::new(sram.capacity as usize);
                    let boxed: Box<dyn Segment> = Box::new(naive_memory);
                    (boxed, (sram.base as usize, sram.capacity as usize))
                }
                _ => todo!(),
            })
            .unzip();

        let mut overlapped_segment = None;
        let mut unchecked_index = index.clone();
        unchecked_index.sort_by_key(|(offset, _)| *offset);
        for window in unchecked_index.windows(2) {
            let (offset1, length1) = window[0];
            let (offset2, _) = window[2];
            if offset2 < (offset1 + length1) {
                overlapped_segment = Some(window[2]);
            }
        }

        if let Some((offset, length)) = overlapped_segment {
            miette::bail!("Address space with offset={offset} length={length} overlapped previous address space")
        }

        Ok(Self { segments, index })
    }

    pub fn request_read(&mut self, addr: usize, length: u32) -> MemResp<'_> {
        let result = self
            .index
            .iter()
            .enumerate()
            .find(|(_, (offset, length))| addr >= *offset && addr < offset + length);

        let Some((idx, (base, _))) = result else {
            return MemResp::IOError(IOError::OutOfMemory);
        };

        let addr_info = MemReqAddrInfo {
            offset: addr - *base,
            length: length as usize,
        };

        self.segments[idx].send_mem_req(MemReq {
            payload: MemReqPayload::Read,
            addr_info,
        })
    }

    pub fn request_write<'a, 'b>(&'a mut self, addr: usize, data: &'b [u8]) -> MemResp<'a> {
        let result = self
            .index
            .iter()
            .enumerate()
            .find(|(_, (offset, length))| addr >= *offset && addr < offset + length);

        let Some((idx, (base, _))) = result else {
            return MemResp::IOError(IOError::OutOfMemory);
        };

        let addr_info = MemReqAddrInfo {
            offset: addr - *base,
            length: data.len(),
        };

        self.segments[idx].send_mem_req(MemReq {
            payload: MemReqPayload::Write(data),
            addr_info,
        })
    }
}

#[test]
fn test_address_space() {
    let addr_spc = AddressSpace::from_device_config("./assets/devices.kdl").unwrap();
    println!("{:#?}", addr_spc);
}

pub struct MemReqAddrInfo {
    offset: usize,
    length: usize,
}

impl MemReqAddrInfo {
    pub fn is_not_valid_in(&self, data: &[u8]) -> bool {
        self.offset >= data.len() || self.offset + self.length > data.len()
    }
}

pub enum MemReqPayload<'a> {
    Read,
    Write(&'a [u8]),
}

pub struct MemReq<'a> {
    payload: MemReqPayload<'a>,
    addr_info: MemReqAddrInfo,
}

pub enum MemResp<'a> {
    Read(&'a [u8]),
    WriteAck,
    IOError(IOError),
}

pub enum IOError {
    OutOfMemory,
    Invalid,
}

pub trait Segment: std::fmt::Debug {
    // todo: here is a simple ping pong service, we can have bufferize and decoupled IO in later
    // refactor
    fn send_mem_req<'a>(&'a mut self, req: MemReq<'a>) -> MemResp<'a>;
}

#[derive(Debug)]
pub struct NaiveMemory {
    memory: Vec<u8>,
}

impl NaiveMemory {
    pub fn new(size: usize) -> Self {
        Self {
            memory: Vec::with_capacity(size),
        }
    }

    /// read return a slice of the inner memory. Caller should guarantee index and read length is
    /// valid. An out of range slicing will directly bail out.
    fn read(&self, index: usize, length: usize) -> &[u8] {
        &self.memory[index..index + length]
    }

    fn write(&mut self, index: usize, data: &[u8], length: usize) {
        assert!(data.len() >= length);

        (&mut self.memory[index..index + length]).copy_from_slice(&data[0..length]);
    }
}

impl Segment for NaiveMemory {
    fn send_mem_req<'a, 'b>(&'a mut self, req: MemReq<'b>) -> MemResp<'a> {
        if req.addr_info.is_not_valid_in(&self.memory) {
            return MemResp::IOError(IOError::OutOfMemory);
        }

        match req.payload {
            MemReqPayload::Read => {
                let raw_bytes = self.read(req.addr_info.offset, req.addr_info.length);
                MemResp::Read(raw_bytes)
            }
            MemReqPayload::Write(payload) => {
                if payload.len() < req.addr_info.length {
                    MemResp::IOError(IOError::Invalid)
                } else {
                    self.write(
                        req.addr_info.offset,
                        &payload[0..req.addr_info.length],
                        req.addr_info.length,
                    );

                    MemResp::WriteAck
                }
            }
        }
    }
}
