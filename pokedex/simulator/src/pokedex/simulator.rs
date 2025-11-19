use std::ffi::CStr;

use tracing::debug;

use crate::common::StateWrite;

use crate::pokedex::bus::{AtomicOp, Bus, BusError, BusResult};
use crate::pokedex::ffi;

pub struct Config {
    pub max_same_instruction: u32,
}

pub struct Simulator {
    core: ffi::ModelRawHandle,

    // represent states not in ASL side
    //
    // NOTE: It's actually a Box.
    //       Use Box to have stable address.
    //       Use raw pointer to prevent potential aliasing issues.
    global: *mut Global,
}

impl Drop for Simulator {
    fn drop(&mut self) {
        unsafe {
            self.core.destroy();

            // In case of core.destroy() invokes callbacks,
            // we drop the global after the core.
            _ = Box::from_raw(self.global);
        }
    }
}

impl Simulator {
    pub fn new(vtable: ffi::VTable, bus: Bus, config: Config) -> Self {
        let global = Global {
            bus,

            stats: Statistic::new(),

            trace_state: TraceState::None,
            trace_issue: None,
            trace_xcpt: None,
            trace_writes: vec![],
        };

        let mut global_box = Box::new(global);

        let core = unsafe { ffi::ModelRawHandle::new(vtable, global_box.as_mut()) };

        Simulator {
            core,
            global: Box::leak(global_box),
        }
    }
}

impl Simulator {
    fn core_reset(&mut self, pc: u32) {
        // core.step() borrows global implicitly through potential callbacks
        unsafe {
            self.core.reset(pc);
        }
    }

    fn core_step(&mut self) {
        // core.step() borrows global implicitly through potential callbacks
        unsafe {
            self.core.step();
        }
    }

    pub fn global(&self) -> &Global {
        unsafe { &*self.global }
    }

    pub fn global_mut(&mut self) -> &mut Global {
        unsafe { &mut *self.global }
    }
}

#[derive(Debug, Clone)]
pub enum StepResult {
    Exit { code: u32 },
}

impl Simulator {
    pub fn reset_core(&mut self, pc: u32, tracer: &mut dyn Tracer) {
        assert_eq!(self.global().trace_state, TraceState::None);

        // may uncomment to debug issue inside model reset
        // debug!("reset core with pc={pc:#010x}");

        self.core_reset(pc);
        tracer.trace_reset(pc);
    }

    pub fn step(&mut self, tracer: &mut dyn Tracer) -> Result<(), StepResult> {
        {
            // pre-step book keeping
            let global = self.global_mut();

            assert_eq!(global.trace_state, TraceState::None);
            global.trace_state = TraceState::Start;

            global.trace_issue = None;
            global.trace_writes.clear();

            global.stats.step_count += 1;
        }

        self.core_step();

        {
            // post-step book keeping
            let global = self.global_mut();

            match global.trace_state {
                TraceState::Committed => {
                    // committed must happen after issue, so the unwrap is safe
                    let (pc, inst) = global.trace_issue.unwrap();

                    tracer.trace_commit(pc, inst, &global.trace_writes);
                }
                TraceState::Xcpt => {
                    let xcpt_info = global.trace_xcpt.unwrap();

                    if let Some((pc, inst)) = global.trace_issue {
                        // Issue -> Xcpt: exceptions in inst execution

                        tracer.trace_inst_xcpt(pc, inst, xcpt_info, &global.trace_writes);
                    } else {
                        // Start -> Xcpt: exceptions in inst fetch
                        assert!(
                            global.trace_writes.is_empty(),
                            "exceptions in fetch should not have state writes"
                        );

                        tracer.trace_fetch_xcpt(xcpt_info);
                    }
                }
                TraceState::Intr => {
                    todo!("trace interrupt");
                }
                _ => unreachable!(
                    "unexpected trace state `{:?}` in post step",
                    global.trace_state
                ),
            }
            global.trace_state = TraceState::None;

            if let Some(code) = global.bus.try_get_exit_code() {
                return Err(StepResult::Exit { code });
            }
        }

        Ok(())
    }
}

// Represents trace state for each instruction
//
// State transition rules:
//   reset:       None -> None
//
//   pre-step:    None -> Start
//   issue:       Start -> Issued
//   commit:      Issued -> Committed
//   xcpt:        Start | Issued -> Xcpt
//   intr:        Start -> Intr
//   post-step:   Commited | Xcpt | Intr -> None
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TraceState {
    None,
    Start,
    Issued,
    Committed,
    Xcpt,
    Intr,
}

pub struct Global {
    pub(crate) bus: Bus,
    pub(crate) stats: Statistic,

    trace_state: TraceState,

    // (pc, inst), written in log_issue
    trace_issue: Option<(u32, Inst)>,

    // written in log_inst_xcpt
    trace_xcpt: Option<XcptInfo>,

    trace_writes: Vec<StateWrite>,
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

impl ffi::PokedexCallbackTrace for Global {
    fn log_inst_issue(&mut self, pc: u32, inst: Inst) {
        assert_eq!(self.trace_state, TraceState::Start);
        self.trace_state = TraceState::Issued;
        self.trace_issue = Some((pc, inst));

        // may uncomment to debug panics inside model step
        // match inst {
        //     Inst::NC(inst) => debug!("inst issue: pc={pc:#010x}, inst={inst:#010x}"),
        //     Inst::C(inst) => debug!("inst issue: pc={pc:#010x}, inst={inst:#06x}, compressed"),
        // }
    }

    fn log_inst_commit(&mut self) {
        assert_eq!(self.trace_state, TraceState::Issued);
        self.trace_state = TraceState::Committed;
    }

    fn log_inst_xcpt(&mut self, xcause: u32, xtval: u32) {
        assert!(matches!(
            self.trace_state,
            TraceState::Start | TraceState::Issued
        ));
        self.trace_state = TraceState::Xcpt;
        self.trace_xcpt = Some(XcptInfo { xcause, xtval });
    }

    fn log_write_xreg(&mut self, rd: u8, value: u32) {
        assert!(1 <= rd && rd <= 31);
        self.trace_writes.push(StateWrite::Xrf { rd, value });
    }

    fn log_write_freg(&mut self, rd: u8, value: u32) {
        assert!(rd <= 31);
        self.trace_writes.push(StateWrite::Frf { rd, value });
    }

    fn log_write_vreg(&mut self, rd: u8, value: &[u8]) {
        assert!(rd <= 31);
        self.trace_writes.push(StateWrite::Vrf {
            rd,
            value: value.into(),
        });
    }

    fn log_write_csr(&mut self, name: &str, value: u32) {
        self.trace_writes.push(StateWrite::Csr {
            name: name.into(),
            value,
        });
    }

    fn debug_write(&mut self, message: &CStr) {
        debug!("ASL MODEL: {message:?}");
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

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct XcptInfo {
    pub xcause: u32,
    pub xtval: u32,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum Inst {
    NC(u32),
    C(u16),
}

pub trait Tracer {
    fn trace_reset(&mut self, pc: u32);

    fn trace_exit(&mut self, exit_code: u32);

    // An instruction commits
    fn trace_commit(&mut self, pc: u32, inst: Inst, writes: &[StateWrite]);

    // Exceptions happens during instruction decoding/execution
    fn trace_inst_xcpt(&mut self, pc: u32, inst: Inst, xcpt_info: XcptInfo, writes: &[StateWrite]);

    // Exceptions happens during instruction fetch
    // TODO: add more information? e.g. pc
    fn trace_fetch_xcpt(&mut self, xcpt_info: XcptInfo);

    fn flush(&mut self);
}

pub struct NoopTracer;

impl Tracer for NoopTracer {
    fn trace_reset(&mut self, _pc: u32) {}
    fn trace_exit(&mut self, _exit_code: u32) {}
    fn trace_commit(&mut self, _pc: u32, _inst: Inst, _writes: &[StateWrite]) {}
    fn trace_inst_xcpt(
        &mut self,
        _pc: u32,
        _inst: Inst,
        _xcpt_info: XcptInfo,
        _writes: &[StateWrite],
    ) {
    }
    fn trace_fetch_xcpt(&mut self, _xcpt_info: XcptInfo) {}
    fn flush(&mut self) {}
}
