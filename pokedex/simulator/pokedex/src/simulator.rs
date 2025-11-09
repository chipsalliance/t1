use std::fmt::Debug;
use std::fs::File;
use std::io::{BufWriter, Read, Write};
use std::marker::PhantomData;
use std::ops::Range;
use std::path::Path;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, AtomicI32, AtomicU32, Ordering};
use thiserror::Error;
use tracing::{Level, event};
use xmas_elf::program::{ProgramHeader, Type};
use xmas_elf::{ElfFile, header};

#[derive(Error, Debug, Clone)]
pub enum SimulationException {
    #[error("fail to fetch instruction")]
    InstructionFetch,
    #[error("Same instruction occur too many time")]
    InfiniteInstruction,
    #[error("simulator exited")]
    Exited(i32),
}

pub struct SimulatorParams<'a> {
    pub max_same_instruction: u32,
    pub elf_path: &'a str,
    pub commit_log_path: Option<&'a str>,
    pub reset_vector: Option<u32>,
}

impl SimulatorParams<'_> {
    pub fn into_simulator(self, bus_info: BusInfo) -> Simulator {
        let handle = SimulatorHandle::new();
        let state = SimulatorState {
            is_reset: false,
            bus_bridge: bus_info.bus_bridge,
            addr_reservation: None,
            statistic: Statistic::new(),
            ic_handle: bus_info.ic_handle,
            model_state_writes: Vec::new(),
            commit_logger: self.commit_log_path.map(|p| {
                let file = std::fs::OpenOptions::new()
                    .append(true)
                    .create(true)
                    .open(p)
                    .unwrap_or_else(|err| panic!("fail open '{p}' for commit log writing: {err}"));
                BufWriter::new(file)
            }),
            exception: None,
            last_instruction_fetch_data: 0u16,
            last_instruction_met_count: 0,
            max_same_instruction: self.max_same_instruction,
        };

        let mut sim = Simulator { handle, state };

        let entry = sim.load_elf(self.elf_path);
        sim.reset_vector(self.reset_vector.unwrap_or(entry));

        sim
    }
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
        self.state.reset_states();
        self.state.reset_vector(addr);

        event!(Level::DEBUG, "reset vector addr to {:#010x}", addr);
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

        if let Some(ic) = self.state.ic_handle.get_enabled_ic() {
            match ic.get_id() {
                InterruptType::Exit => {
                    let value = ic.get_value();
                    let exit_code = i32::from_le_bytes(value);
                    self.state.poweroff(exit_code);
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

    pub fn current_pc(&self) -> u32 {
        self.handle.get_pc()
    }

    pub fn dump_regs(&self) -> Vec<u32> {
        (0..32).map(|i| self.handle.get_register(i)).collect()
    }
}

#[derive(Debug, PartialEq, Eq, serde::Serialize)]
#[serde(tag = "dest", rename_all = "lowercase")]
pub(crate) enum ModelStateWrite {
    Xrf { rd: u8, value: u32 },
    Frf { rd: u8, value: u32 },
    Csr { name: String, value: u32 },
    Load { addr: u32 },
    Store { addr: u32, data: Vec<u8> },
    ResetVector { pc: u32 },
    Poweroff { exit_code: i32 },
    // internal use
    _Insn { addr: u32 },
}

// simulator states not in ASL side
pub(crate) struct SimulatorState {
    bus_bridge: BusBridge,
    addr_reservation: Option<u32>,

    statistic: Statistic,
    exception: Option<SimulationException>,
    ic_handle: ICHandle,

    model_state_writes: Vec<ModelStateWrite>,
    commit_logger: Option<BufWriter<File>>,

    is_reset: bool,

    last_instruction_fetch_data: u16,
    last_instruction_met_count: u32,
    max_same_instruction: u32,
}

// callback for ASL generated code
impl SimulatorState {
    pub fn reset_states(&mut self) {
        self.addr_reservation = None;
        self.exception = None;
        self.model_state_writes.clear();
        self.is_reset = false;
        self.last_instruction_met_count = 0;
        self.last_instruction_met_count = 0;
    }

    pub fn reset_vector(&mut self, new_pc: u32) {
        self.is_reset = true;
        self.model_state_writes
            .push(ModelStateWrite::ResetVector { pc: new_pc });
        self.commit_log_insn(0, 0, false);
    }

    pub fn poweroff(&mut self, exit_code: i32) {
        self.model_state_writes
            .push(ModelStateWrite::Poweroff { exit_code });
        self.commit_log_insn(0, 0, false);
    }

    pub fn req_bus_read<const N: usize>(&mut self, addr: u32) -> Result<[u8; N], BusError> {
        if self.is_reset {
            self.model_state_writes.push(ModelStateWrite::Load { addr });
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
            self.model_state_writes.push(ModelStateWrite::Store {
                addr,
                data: data.to_vec(),
            })
        }

        self.yield_reservation(addr);

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
        self.model_state_writes
            .push(ModelStateWrite::_Insn { addr: pc });
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

    #[inline]
    pub(crate) fn write_register(&mut self, rd: u8, value: u32) {
        self.model_state_writes
            .push(ModelStateWrite::Xrf { rd, value })
    }

    #[inline]
    pub(crate) fn write_fp_register(&mut self, rd: u8, value: u32) {
        self.model_state_writes
            .push(ModelStateWrite::Frf { rd, value })
    }

    pub(crate) fn write_csr(&mut self, name: &str, value: u32) {
        self.model_state_writes.push(ModelStateWrite::Csr {
            name: name.to_string(),
            value,
        })
    }

    // only for debug purpose
    pub(crate) fn debug_log_issue(&mut self, pc: u32, inst: u32, is_c: bool) {
        if is_c {
            event!(
                Level::TRACE,
                event_type = "inst_issue_c",
                pc_hex = format!("{:#010x}", pc),
                inst_hex = format!("{:#06x}", inst)
            );
        } else {
            event!(
                Level::TRACE,
                event_type = "inst_issue_w",
                pc_hex = format!("{:#010x}", pc),
                inst_hex = format!("{:#010x}", inst)
            );
        }
    }

    pub(crate) fn commit_log_insn(&mut self, pc: u32, insn: u32, is_c: bool) {
        let insn_fetch: Vec<u32> = self
            .model_state_writes
            .iter()
            .filter_map(|ev| match ev {
                ModelStateWrite::_Insn { addr } => Some(*addr),
                _ => None,
            })
            .collect();

        let state_change: Vec<&ModelStateWrite> = self
            .model_state_writes
            .iter()
            .filter(|ev| match ev {
                ModelStateWrite::_Insn { .. } => false,
                ModelStateWrite::Load { addr } => !insn_fetch.contains(addr),
                _ => true,
            })
            .collect();

        self.commit_logger.iter_mut().for_each(|logger| {
            let commit = serde_json::json! ({
                "pc": pc,
                "instruction": insn,
                "is_compressed": is_c,
                "states_changed": state_change,
            });
            writeln!(logger, "{}", commit).expect("fail writing commit log")
        });

        self.model_state_writes.clear();
    }

    pub(crate) fn print_string(&self, s: &str) {
        event!(Level::DEBUG, "ASL model: {s}")
    }

    pub(crate) fn debug_trap_xcpt(&self, cause: i128, tval: u32) {
        event!(
            Level::DEBUG,
            "ASL model trap exception with cause={cause} tval={tval:#010x}"
        )
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

    pub(crate) fn load_reserved(&mut self, addr: u32) -> Option<[u8; 4]> {
        self.addr_reservation = None;
        let data: [u8; 4] = self
            .req_bus_read(addr)
            .inspect_err(|e| event!(Level::DEBUG, "LR {addr:#010X} error: {e}"))
            .ok()?;
        self.addr_reservation = Some(addr);
        Some(data)
    }

    pub(crate) fn store_conditional(&mut self, addr: u32, data: &[u8; 4]) -> bool {
        if self.yield_reservation(addr) {
            self.req_bus_write(addr, data)
                .inspect_err(|e| event!(Level::DEBUG, "SC {addr:#010X} error: {e}"))
                .is_ok()
        } else {
            false
        }
    }

    pub(crate) fn yield_reservation(&mut self, addr: u32) -> bool {
        let reservation = self.addr_reservation.take();
        reservation == Some(addr)
    }

    pub(crate) fn amo_exec(
        &mut self,
        op: impl FnOnce(u32, u32) -> u32,
        addr: u32,
        src2: u32,
        _is_aq: bool,
        _is_rl: bool,
    ) -> Option<u32> {
        let old: [u8; 4] = self
            .req_bus_read(addr)
            .inspect_err(|e| event!(Level::DEBUG, "AMO read {addr:#010X} error: {e}"))
            .ok()?;
        let old = u32::from_le_bytes(old);
        let new = op(old, src2);
        self.req_bus_write(addr, &new.to_le_bytes())
            .inspect_err(|e| event!(Level::DEBUG, "AMO write {addr:#010X} error: {e}"))
            .ok()?;
        Some(old)
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

#[derive(Debug)]
pub enum AddressSpaceDescNode {
    Mmio {
        base: u32,
        length: u32,
        mmap: Vec<(String, u32)>,
    },
    Sram {
        #[allow(dead_code)]
        name: String,
        base: u32,
        length: u32,
    },
}

pub struct BusBridge {
    address_space: Vec<(Range<u32>, Box<dyn Addressable>)>,
}

pub struct BusInfo {
    bus_bridge: BusBridge,
    ic_handle: ICHandle,
}

impl BusInfo {
    pub fn try_from_config(configuration: &[AddressSpaceDescNode]) -> miette::Result<Self> {
        let mut segments = Vec::new();
        let mut ic_handle = None;
        for node in configuration {
            match node {
                AddressSpaceDescNode::Sram {
                    name: _,
                    base,
                    length,
                } => {
                    let naive_memory = NaiveMemory::new(*length as usize);
                    let boxed: Box<dyn Addressable> = Box::new(naive_memory);
                    segments.push(((*base..(base + length)), boxed))
                }
                AddressSpaceDescNode::Mmio { base, length, mmap } => {
                    let (mmio_decoder, controllers) = MMIOAddrDecoder::try_build_from(mmap)?;
                    ic_handle = Some(controllers);
                    let boxed: Box<dyn Addressable> = Box::new(mmio_decoder);
                    segments.push(((*base..base + length), boxed))
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
    fn try_build_from(config: &[(String, u32)]) -> Result<(Self, ICHandle), miette::Error> {
        let mut regs = Vec::new();
        let mut ic_handle = ICHandle::new();
        for (name, offset) in config {
            match name.as_str() {
                "exit" => {
                    let exit_rc = ExitController::new();
                    ic_handle.add(exit_rc.clone());
                    regs.push((*offset, MmioRegs::Exit(exit_rc)))
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
