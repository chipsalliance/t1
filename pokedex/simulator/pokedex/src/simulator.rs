use miette::{Context, IntoDiagnostic};
use std::fmt::Debug;
use std::io::Read;
use std::marker::PhantomData;
use std::ops::Range;
use std::path::Path;
use std::sync::atomic::{AtomicBool, AtomicI32, AtomicU32, Ordering};
use std::sync::Arc;
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
    Exited(i32),
}

pub struct SimulatorParams<'a, 'b> {
    pub max_same_instruction: u8,
    pub dts_cfg_path: &'a str,
    pub elf_path: &'b str,
}

impl SimulatorParams<'_, '_> {
    pub fn try_build(self) -> miette::Result<Simulator> {
        let handle = SimulatorHandle::new();
        let bus_info = BusInfo::from_device_config(self.dts_cfg_path)?;
        let state = SimulatorState {
            is_reset: false,
            bus_bridge: bus_info.bus_bridge,
            pc: 0x1000,
            statistic: Statistic::new(),
            ic_handle: bus_info.ic_handle,
            exception: None,
            last_instruction_fetch_data: 0u16,
            last_instruction_met_count: 0,
            max_same_instruction: self.max_same_instruction,
        };

        let mut sim = Simulator { handle, state };

        let entry = sim.load_elf(self.elf_path);
        sim.reset_vector(entry);

        Ok(sim)
    }
}

// simulator states not in ASL side
pub(crate) struct SimulatorState {
    bus_bridge: BusBridge,
    pc: u32,
    statistic: Statistic,
    exception: Option<SimulationException>,
    ic_handle: ICHandle,

    is_reset: bool,

    last_instruction_fetch_data: u16,
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
    pub fn reset_vector(&mut self, addr: u32) {
        self.handle.reset_states();
        self.handle.reset_vector(addr);
        self.state.pc = addr;
        self.state.is_reset = true;

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

        if let Some(ic) = self.state.ic_handle.get_enabled_ic() {
            match ic.get_id() {
                InterruptType::Exit => {
                    let value = ic.get_value();
                    let exit_code = i32::from_le_bytes(value);
                    return Err(SimulationException::Exited(exit_code));
                }
            }
        }

        Ok(())
    }

    pub fn load_elf<P: AsRef<Path> + Debug>(&mut self, fname: P) -> u32 {
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
                    let addr = ph.virtual_addr;

                    let slice = &buffer[offset..offset + size];

                    if let Err(err) = self.state.req_bus_write(addr, slice) {
                        panic!("fail loading elf to memory: {err:?}");
                    };
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

    pub fn dump_regs(&self) {
        const COLUMN_SIZE: u8 = 8;

        for i in 0..4 {
            for j in 0..COLUMN_SIZE {
                let index = j + COLUMN_SIZE * i;
                let reg_val = self.handle.get_register(j + COLUMN_SIZE * i);
                print!("x{:<2}: {:#010x}  ", index, reg_val)
            }
            println!()
        }
    }
}

// callback for ASL generated code
impl SimulatorState {
    pub fn req_bus_read<const N: usize>(&mut self, addr: u32) -> Result<[u8; N], BusError> {
        if self.is_reset {
            event!(
                Level::TRACE,
                event_type = "physical_memory",
                action = "read",
                pc = format!("{:#010x}", self.pc),
                address = addr,
                addr_hex = format!("{:#010x}", addr),
                bytes = N,
            );
        }

        let result = self
            .bus_bridge
            .address_space
            .iter_mut()
            .find(|(addr_space, _)| addr_space.contains(&addr));

        let Some((addr_space, device)) = result else {
            return Err(BusError::DecodeError(addr));
        };

        let offset = addr - addr_space.start;

        let mut buffer = [0u8; N];
        device.do_bus_read(offset, &mut buffer).map(|_| buffer)
    }

    pub fn req_bus_write(&mut self, addr: u32, data: &[u8]) -> Result<(), BusError> {
        if self.is_reset {
            let hexable: Vec<u8> = data.iter().rev().copied().collect();
            event!(
                Level::TRACE,
                event_type = "physical_memory",
                action = "write",
                pc = format!("{:#010x}", self.pc),
                address = addr,
                addr_hex = format!("{:#010x}", addr),
                data_hex = hex::encode_upper(hexable),
                bytes = data.len(),
            );
        }

        let result = self
            .bus_bridge
            .address_space
            .iter_mut()
            .find(|(addr_space, _)| addr_space.contains(&addr));

        let Some((addr_space, device)) = result else {
            return Err(BusError::DecodeError(addr));
        };

        let offset = addr - addr_space.start;

        device.do_bus_write(offset, data)
    }

    pub(crate) fn inst_fetch(&mut self, pc: u32) -> Option<u16> {
        let inst = match self.req_bus_read(pc) {
            Ok(inst) => inst,
            Err(err) => {
                event!(Level::DEBUG, "fail reading memory at PC: {pc:#010x}: {err}");
                return None;
            }
        };

        let inst: u16 = u16::from_le_bytes(inst);
        self.statistic.fetch_count += 1;

        if inst == self.last_instruction_fetch_data {
            self.last_instruction_met_count += 1;
        } else {
            self.last_instruction_met_count = 0;
        }

        self.last_instruction_fetch_data = inst;
        if self.last_instruction_met_count > self.max_same_instruction {
            self.exception = Some(SimulationException::InfiniteInstruction);
        }

        event!(
            Level::TRACE,
            event_type = "instruction_fetch",
            pc = pc,
            pc_hex = format!("{:#010x}", pc),
            instruction = inst,
            inst_hex = format!("{:#06x}", inst)
        );

        Some(inst)
    }

    pub(crate) fn fence_i(&self) {
        event!(Level::DEBUG, "fence_i called");
    }

    #[allow(dead_code)]
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
            pc_hex = format!("{:#010x}", self.pc),
            reg_idx = reg_idx,
            data = value,
            data_hex = format!("{:#010x}", value)
        );
    }

    pub(crate) fn write_csr(&self, idx: u32, name: &str, value: u32) {
        event!(
            Level::TRACE,
            event_type = "csr",
            action = "write",
            pc = self.pc,
            pc_hex = format!("{:#010x}", self.pc),
            csr_idx = idx,
            csr_idx_hex = format!("{:#0x}", idx),
            csr_name = name,
            data = value,
            data_hex = format!("{:#010x}", value)
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

    #[allow(dead_code)]
    pub(crate) fn write_pending_interrupt(&self) -> u32 {
        0
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
    Mmio(KdlNodeMMIO),
    Sram(KdlNodeSRAM),
}

#[derive(Debug, knuffel::Decode)]
struct KdlNodeSRAM {
    #[allow(dead_code)]
    #[knuffel(argument)]
    name: String,
    #[knuffel(property)]
    base: u32,
    #[knuffel(property)]
    length: u32,
}

#[derive(Debug, knuffel::Decode)]
struct KdlNodeMMIO {
    #[knuffel(property)]
    base: u32,
    #[knuffel(property)]
    length: u32,
    #[knuffel(children)]
    mmap: Vec<KdlNodeMMIOMapping>,
}

#[derive(Debug, knuffel::Decode)]
struct KdlNodeMMIOMapping {
    #[knuffel(argument)]
    name: String,
    #[knuffel(property)]
    offset: u32,
}

pub struct BusBridge {
    address_space: Vec<(Range<u32>, Box<dyn Addressable>)>,
}

struct BusInfo {
    bus_bridge: BusBridge,
    ic_handle: ICHandle,
}

impl BusInfo {
    pub fn from_device_config<P: AsRef<Path>>(p: P) -> miette::Result<Self> {
        let content = std::fs::read_to_string(&p)
            .into_diagnostic()
            .wrap_err_with(|| format!("fail read path: {}", p.as_ref().display()))?;

        let filepath = p.as_ref().to_string_lossy();
        let configuration: Vec<AddressSpaceDescNode> = knuffel::parse(&filepath, &content)?;

        let mut segments = Vec::new();
        let mut ic_handle = None;
        for node in configuration {
            match node {
                AddressSpaceDescNode::Sram(sram) => {
                    let naive_memory = NaiveMemory::new(sram.length as usize);
                    let boxed: Box<dyn Addressable> = Box::new(naive_memory);
                    segments.push(((sram.base..sram.base + sram.length), boxed))
                }
                AddressSpaceDescNode::Mmio(mmio_config) => {
                    let (mmio_decoder, controllers) =
                        MMIOAddrDecoder::try_build_from(mmio_config.mmap)?;
                    ic_handle = Some(controllers);
                    let boxed: Box<dyn Addressable> = Box::new(mmio_decoder);
                    segments.push((
                        (mmio_config.base..mmio_config.base + mmio_config.length),
                        boxed,
                    ))
                }
            }
        }

        let mut overlapped_segment = None;
        let mut unchecked_index: Vec<Range<u32>> =
            segments.iter().map(|(index, _)| index.clone()).collect();
        unchecked_index.sort_by_key(|range| range.start);
        for window in unchecked_index.windows(2) {
            let addr1 = &window[0];
            let addr2 = &window[1];
            if addr2.start < addr1.end {
                overlapped_segment = Some(&window[1]);
            }
        }

        if let Some(range) = overlapped_segment {
            miette::bail!(
                "Address space with offset={:#010x} length={:#010x} overlapped previous address space",
                range.start,
                range.end - range.start
            )
        }

        Ok(Self {
            ic_handle: ic_handle.unwrap(),
            bus_bridge: BusBridge {
                address_space: segments,
            },
        })
    }
}

#[test]
fn test_address_space() -> miette::Result<()> {
    let _ = BusInfo::from_device_config("./assets/devices.kdl")?;
    Ok(())
}

pub enum InterruptType {
    Exit,
}

pub trait InterruptController: Sync + Send {
    fn get_id(&self) -> InterruptType;
    fn is_enabled(&self) -> bool;
    fn set_enable(&mut self);
    fn get_value(&self) -> [u8; 4];
    fn set_value(&mut self, val: [u8; 4]);
}

struct ICHandle(Vec<Box<dyn InterruptController>>);
impl ICHandle {
    fn new() -> Self {
        Self(Vec::new())
    }

    fn add<T: InterruptController + 'static>(&mut self, ic: T) {
        self.0.push(Box::new(ic))
    }

    fn get_enabled_ic(&self) -> Option<&dyn InterruptController> {
        self.0.iter().find_map(|ic| {
            if ic.is_enabled() {
                Some(ic.as_ref())
            } else {
                None
            }
        })
    }
}

#[derive(Debug, Clone, Default)]
pub struct ExitController(Arc<AtomicBool>, Arc<AtomicI32>);
impl ExitController {
    fn new() -> Self {
        Self(
            Arc::new(AtomicBool::new(false)),
            Arc::new(AtomicI32::new(0)),
        )
    }
}

impl InterruptController for ExitController {
    fn get_id(&self) -> InterruptType {
        InterruptType::Exit
    }

    fn is_enabled(&self) -> bool {
        self.0.load(Ordering::Acquire)
    }

    fn set_enable(&mut self) {
        self.0.store(true, Ordering::Release);
    }

    fn set_value(&mut self, val: [u8; 4]) {
        let v = i32::from_le_bytes(val);
        self.1.store(v, Ordering::Release);
    }

    fn get_value(&self) -> [u8; 4] {
        let v = self.1.load(Ordering::Acquire);
        v.to_le_bytes()
    }
}

#[derive(Debug, thiserror::Error)]
pub enum BusError {
    #[error("device fail {id} at {addr} with length {len}")]
    DeviceError {
        id: &'static str,
        addr: u32,
        len: u32,
    },
    #[error("no device map at address {0}")]
    DecodeError(u32),
}

pub trait Addressable: Send {
    fn do_bus_read(&mut self, offset: u32, dest: &mut [u8]) -> Result<(), BusError>;
    fn do_bus_write(&mut self, offset: u32, data: &[u8]) -> Result<(), BusError>;
}

#[derive(Debug)]
pub struct NaiveMemory {
    memory: Vec<u8>,
}

impl NaiveMemory {
    pub fn new(size: usize) -> Self {
        Self {
            memory: vec![0u8; size],
        }
    }
}

impl Addressable for NaiveMemory {
    /// read return a slice of the inner memory. Caller should guarantee index and read length is
    /// valid. An out of range slicing will directly bail out.
    fn do_bus_read(&mut self, offset: u32, dest: &mut [u8]) -> Result<(), BusError> {
        let length = self.memory.len() as u32;
        if offset >= length || offset + dest.len() as u32 > length {
            return Err(BusError::DeviceError {
                id: "NaiveMemoryRead",
                addr: offset,
                len: dest.len() as u32,
            });
        }

        dest.copy_from_slice(&self.memory[offset as usize..offset as usize + dest.len()]);

        Ok(())
    }

    fn do_bus_write(&mut self, offset: u32, data: &[u8]) -> Result<(), BusError> {
        let length = self.memory.len() as u32;
        if offset >= length || offset + data.len() as u32 > length {
            return Err(BusError::DeviceError {
                id: "NaiveMemoryWrite",
                addr: offset,
                len: data.len() as u32,
            });
        }

        self.memory[offset as usize..offset as usize + data.len()].copy_from_slice(data);

        Ok(())
    }
}

#[derive(Debug, Clone)]
pub enum MmioRegs {
    Exit(ExitController),
}

impl MmioRegs {
    fn load(&self) -> u32 {
        match self {
            Self::Exit(_) => {
                panic!("unexpected read from exit MMIO device");
            }
        }
    }

    fn store(&mut self, v: u32) {
        match self {
            Self::Exit(eic) => {
                eic.set_value(v.to_le_bytes());
                eic.set_enable();
            }
        }
    }
}

#[derive(Debug)]
pub struct MMIOAddrDecoder {
    // offset, type
    regs: Vec<(u32, MmioRegs)>,
}

impl MMIOAddrDecoder {
    fn try_build_from(config: Vec<KdlNodeMMIOMapping>) -> Result<(Self, ICHandle), miette::Error> {
        let mut regs = Vec::new();
        let mut ic_handle = ICHandle::new();
        for node in config {
            match node.name.as_str() {
                "exit" => {
                    let exit_rc = ExitController::new();
                    ic_handle.add(exit_rc.clone());
                    regs.push((node.offset, MmioRegs::Exit(exit_rc)))
                }
                name => miette::bail!("unsupported MMIO device {name}"),
            }
        }

        if regs.is_empty() {
            miette::bail!("no mmio node found");
        }

        regs.sort_unstable_by_key(|(offset, _)| *offset);

        Ok((Self { regs }, ic_handle))
    }
}

impl Addressable for MMIOAddrDecoder {
    fn do_bus_write(&mut self, offset: u32, data: &[u8]) -> Result<(), BusError> {
        // a non u32 write is consider as implmentation bug and should be immediately bail out
        let new_val = u32::from_le_bytes(data.try_into().unwrap());
        let index = self.regs.binary_search_by(|(reg, _)| reg.cmp(&offset));

        if let Ok(i) = index {
            self.regs[i].1.store(new_val);

            Ok(())
        } else {
            event!(
                Level::DEBUG,
                "unhandle MMIO write to offset={offset} with value {new_val}"
            );
            Err(BusError::DeviceError {
                id: "MMIOWrite",
                addr: offset,
                len: data.len() as u32,
            })
        }
    }

    fn do_bus_read(&mut self, offset: u32, dest: &mut [u8]) -> Result<(), BusError> {
        let index = self.regs.binary_search_by(|(reg, _)| reg.cmp(&offset));

        if let Ok(i) = index {
            // a non u32 read is consider as implmentation bug and should be immediately bail out
            dest.copy_from_slice(&self.regs[i].1.load().to_le_bytes());

            Ok(())
        } else {
            event!(Level::DEBUG, "unhandle MMIO read to offset={offset}");
            Err(BusError::DeviceError {
                id: "MMIORead",
                addr: offset,
                len: 0,
            })
        }
    }
}
