use crate::pokedex::bus::{AtomicOp, Bus, BusError, BusResult};
use crate::pokedex::ffi;

pub struct Config {
    pub max_same_instruction: u32,
}

pub struct Simulator {
    core: ffi::ModelHandle,

    pub(crate) global: Global,
}

impl Simulator {
    pub fn new(vtable: ffi::VTable, bus: Bus, config: Config) -> Self {
        let global = Global {
            bus,

            stats: Statistic::new(),
        };

        let core = unsafe { ffi::ModelHandle::new(vtable) };

        Simulator { core, global }
    }

    pub fn stats(&self) -> &Statistic {
        &self.global.stats
    }
}

#[derive(Debug, Clone)]
pub enum StepResult {
    Exit { code: u32 },
}

impl Simulator {
    pub fn reset_core(&mut self, pc: u32) {
        // may uncomment to debug issue inside model reset
        // debug!("reset core with pc={pc:#010x}");

        self.core.reset(pc);
    }

    pub fn step(&mut self) -> StepCode {
        // pre-step book keeping
        self.global.stats.step_count += 1;

        self.core.step(&mut self.global)
    }

    pub fn step_trace(&mut self) -> StepDetail<'_> {
        // pre-step book keeping
        self.global.stats.step_count += 1;

        self.core.step_trace(&mut self.global)
    }

    pub fn is_exited(&self) -> Option<u32> {
        self.global.bus.try_get_exit_code()
    }
}

pub struct Global {
    pub(crate) bus: Bus,
    pub(crate) stats: Statistic,
}

impl ffi::PokedexCallbackMem for Global {
    type CbMemError = BusError;

    fn inst_fetch_2(&mut self, addr: u32) -> BusResult<u16> {
        assert!(addr % 2 == 0);

        self.stats.fetch_count += 1;

        let mut data = [0; 2];
        self.bus
            .read(addr, &mut data)
            .map(|_| u16::from_le_bytes(data))
    }

    fn read_mem_u8(&mut self, addr: u32) -> BusResult<u8> {
        let mut data = [0; 1];
        self.bus
            .read(addr, &mut data)
            .map(|_| u8::from_le_bytes(data))
    }

    fn read_mem_u16(&mut self, addr: u32) -> BusResult<u16> {
        assert!(addr % 2 == 0);

        let mut data = [0; 2];
        self.bus
            .read(addr, &mut data)
            .map(|_| u16::from_le_bytes(data))
    }

    fn read_mem_u32(&mut self, addr: u32) -> BusResult<u32> {
        assert!(addr % 4 == 0);

        let mut data = [0; 4];
        self.bus
            .read(addr, &mut data)
            .map(|_| u32::from_le_bytes(data))
    }

    fn write_mem_u8(&mut self, addr: u32, value: u8) -> BusResult<()> {
        self.bus.write(addr, &value.to_le_bytes())
    }

    fn write_mem_u16(&mut self, addr: u32, value: u16) -> BusResult<()> {
        self.bus.write(addr, &value.to_le_bytes())
    }

    fn write_mem_u32(&mut self, addr: u32, value: u32) -> BusResult<()> {
        self.bus.write(addr, &value.to_le_bytes())
    }

    fn amo_mem_u32(&mut self, addr: u32, op: AtomicOp, value: u32) -> BusResult<u32> {
        // TODO: currently we simulate AMO using read-modify-write.
        // Consider forward it directly to bus later

        let mut read_bytes = [0; 4];
        self.bus.read(addr, &mut read_bytes)?;
        let read_value = u32::from_le_bytes(read_bytes);

        let write_value: u32 = op.do_arith_u32(read_value, value);
        self.bus.write(addr, &write_value.to_le_bytes())?;

        Ok(read_value)
    }
}

#[derive(Debug, Clone, Default)]
pub struct Statistic {
    pub fetch_count: u64,
    pub step_count: u64,
}

impl Statistic {
    pub fn new() -> Self {
        Self::default()
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum Inst {
    NC(u32),
    C(u16),
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum StepCode {
    Interrupt,
    Exception,
    Committed,
}

pub struct StepDetail<'a> {
    pub code: StepCode,
    pub pc: u32,
    pub inst: Option<Inst>,
    pub changes: ffi::CoreChange<'a>,
}
