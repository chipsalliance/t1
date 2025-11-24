use std::{
    collections::HashSet, convert::Infallible, marker::PhantomData, net::TcpStream,
    os::unix::ffi::OsStrExt, path::PathBuf,
};

use gdbstub::{
    arch::Arch,
    common::Signal,
    stub::{
        GdbStub, SingleThreadStopReason,
        run_blocking::{BlockingEventLoop, Event, WaitForStopReasonError},
    },
    target::{
        Target, TargetError, TargetResult,
        ext::{
            base::{
                BaseOps,
                single_register_access::{SingleRegisterAccess, SingleRegisterAccessOps},
                singlethread::{SingleThreadBase, SingleThreadResume, SingleThreadResumeOps},
            },
            breakpoints::{Breakpoints, BreakpointsOps, SwBreakpoint, SwBreakpointOps},
            exec_file::{ExecFile, ExecFileOps},
            target_description_xml_override::{
                TargetDescriptionXmlOverride, TargetDescriptionXmlOverrideOps,
            },
        },
    },
};
use gdbstub_arch::riscv::reg::RiscvCoreRegs;
use tracing::error;

use crate::gdb::PokedexTarget;

// use crate::gdb::PokedexTarget;

pub fn run_gdb(
    stream: TcpStream,
    target: &mut dyn PokedexTarget,
    config: TargetConfig,
) -> anyhow::Result<()> {
    let debugger = GdbStub::new(stream);

    let mut target = TargetWrapper::from(target, config);

    match debugger.run_blocking::<EventLoop>(&mut target) {
        Ok(_) => todo!(),
        Err(e) => {
            error!("{e}");
            Ok(())
        }
    }
}

struct EventLoop<'a>(PhantomData<&'a mut dyn PokedexTarget>);

impl<'a> BlockingEventLoop for EventLoop<'a> {
    type Target = TargetWrapper<'a>;
    type Connection = TcpStream;
    type StopReason = SingleThreadStopReason<u32>;

    fn wait_for_stop_reason(
        target: &mut TargetWrapper<'_>,
        _conn: &mut Self::Connection,
    ) -> Result<Event<Self::StopReason>, WaitForStopReasonError<Infallible, std::io::Error>> {
        // FIXME: if the target enters infinite loop ...
        Ok(Event::TargetStopped(target.poll()))
    }

    fn on_interrupt(
        _target: &mut TargetWrapper<'_>,
    ) -> Result<Option<Self::StopReason>, <Self::Target as Target>::Error> {
        Ok(Some(SingleThreadStopReason::Signal(Signal::SIGINT).into()))
    }
}

struct TargetWrapper<'a> {
    inner: &'a mut dyn PokedexTarget,
    config: TargetConfig,
    breakpoints: HashSet<u32>,
}

pub struct TargetConfig {
    pub elf_path: PathBuf,
    pub target_xml: String,
}

impl<'a> TargetWrapper<'a> {
    fn from(inner: &'a mut dyn PokedexTarget, config: TargetConfig) -> Self {
        TargetWrapper {
            inner,
            config,
            breakpoints: HashSet::new(),
        }
    }

    fn poll(&mut self) -> SingleThreadStopReason<u32> {
        let inner = &mut *self.inner;
        loop {
            if let Some(code) = inner.gdb_is_exited() {
                return SingleThreadStopReason::Exited(code as u8);
            }

            let pc = inner.gdb_read_pc();
            tracing::debug!("pc = {pc:#010x}");
            if self.breakpoints.contains(&pc) {
                return SingleThreadStopReason::SwBreak(());
            }

            inner.gdb_step_one();
        }
    }
}

impl Target for TargetWrapper<'_> {
    type Arch = TargetArch;
    type Error = Infallible;

    fn base_ops(&mut self) -> BaseOps<'_, Self::Arch, Self::Error> {
        BaseOps::SingleThread(self)
    }

    fn support_breakpoints(&mut self) -> Option<BreakpointsOps<'_, Self>> {
        Some(self)
    }

    fn support_exec_file(&mut self) -> Option<ExecFileOps<'_, Self>> {
        Some(self)
    }

    fn support_target_description_xml_override(
        &mut self,
    ) -> Option<TargetDescriptionXmlOverrideOps<'_, Self>> {
        Some(self)
    }
}

struct TargetArch;

// FIXME: due to constaints of gdbstub, the VLEN is hardcoded
const VLEN: usize = 256;
type RiscvRegId = super::arch::RiscvRegId<32, VLEN>;

impl Arch for TargetArch {
    type Usize = u32;
    type Registers = RiscvCoreRegs<u32>;
    type BreakpointKind = usize;
    type RegId = RiscvRegId;
}

impl ExecFile for TargetWrapper<'_> {
    fn get_exec_file(
        &self,
        _pid: Option<gdbstub::common::Pid>,
        offset: u64,
        length: usize,
        buf: &mut [u8],
    ) -> TargetResult<usize, Self> {
        let elf_path = self.config.elf_path.as_os_str().as_bytes();
        if offset >= elf_path.len() as u64 {
            return Ok(0);
        }

        let data = &elf_path[offset as usize..];
        // NOTE: length > buf.len() may happen
        let len = data.len().min(buf.len().min(length));
        buf[..len].copy_from_slice(&data[..len]);
        Ok(len)
    }
}

impl TargetDescriptionXmlOverride for TargetWrapper<'_> {
    fn target_description_xml(
        &self,
        annex: &[u8],
        offset: u64,
        length: usize,
        buf: &mut [u8],
    ) -> TargetResult<usize, Self> {
        if annex != b"target.xml" {
            return Err(TargetError::NonFatal);
        }

        let target_xml = self.config.target_xml.as_bytes();
        if offset >= target_xml.len() as u64 {
            return Ok(0);
        }

        let data = &target_xml[offset as usize..];
        // NOTE: length > buf.len() may happen
        let len = data.len().min(buf.len().min(length));
        buf[..len].copy_from_slice(&data[..len]);
        Ok(len)
    }
}

impl SingleThreadBase for TargetWrapper<'_> {
    fn support_single_register_access(&mut self) -> Option<SingleRegisterAccessOps<'_, (), Self>> {
        Some(self)
    }

    fn support_resume(&mut self) -> Option<SingleThreadResumeOps<'_, Self>> {
        Some(self)
    }

    fn read_registers(&mut self, regs: &mut RiscvCoreRegs<u32>) -> TargetResult<(), Self> {
        regs.pc = self.inner.gdb_read_pc();
        for i in 0..32 {
            regs.x[i] = self.inner.gdb_read_xreg(i as u8);
        }

        Ok(())
    }

    fn write_registers(&mut self, _regs: &RiscvCoreRegs<u32>) -> TargetResult<(), Self> {
        Err(TargetError::NonFatal)
    }

    fn read_addrs(&mut self, start_addr: u32, data: &mut [u8]) -> TargetResult<usize, Self> {
        Ok(self.inner.gdb_read_mem(start_addr, data))
    }

    fn write_addrs(&mut self, _start_addr: u32, _data: &[u8]) -> TargetResult<(), Self> {
        Err(TargetError::NonFatal)
    }
}

impl SingleRegisterAccess<()> for TargetWrapper<'_> {
    fn read_register(
        &mut self,
        _tid: (),
        reg_id: RiscvRegId,
        buf: &mut [u8],
    ) -> TargetResult<usize, Self> {
        let inner = &mut *self.inner;
        match reg_id {
            RiscvRegId::Pc => {
                buf[..4].copy_from_slice(&inner.gdb_read_pc().to_le_bytes());
                Ok(4)
            }
            RiscvRegId::X(idx) => {
                buf[..4].copy_from_slice(&inner.gdb_read_xreg(idx).to_le_bytes());
                Ok(4)
            }
            RiscvRegId::F(idx) => {
                buf[..4].copy_from_slice(&inner.gdb_read_freg(idx).to_le_bytes());
                Ok(4)
            }
            RiscvRegId::V(idx) => {
                inner.gdb_read_vreg(idx, &mut buf[..VLEN / 8]);
                Ok(VLEN / 8)
            }
            RiscvRegId::Csr(idx) => {
                buf[..4].copy_from_slice(&inner.gdb_read_csr(idx).to_le_bytes());
                Ok(4)
            }
            RiscvRegId::Priv => todo!(),
        }
    }

    fn write_register(
        &mut self,
        _tid: (),
        _reg_id: RiscvRegId,
        _val: &[u8],
    ) -> TargetResult<(), Self> {
        Err(TargetError::NonFatal)
    }
}

impl Breakpoints for TargetWrapper<'_> {
    fn support_sw_breakpoint(&mut self) -> Option<SwBreakpointOps<'_, Self>> {
        Some(self)
    }
}

impl SwBreakpoint for TargetWrapper<'_> {
    fn add_sw_breakpoint(&mut self, addr: u32, _kind: usize) -> TargetResult<bool, Self> {
        self.breakpoints.insert(addr);
        Ok(true)
    }

    fn remove_sw_breakpoint(&mut self, addr: u32, _kind: usize) -> TargetResult<bool, Self> {
        self.breakpoints.remove(&addr);
        Ok(true)
    }
}

impl SingleThreadResume for TargetWrapper<'_> {
    fn resume(&mut self, _signal: Option<Signal>) -> Result<(), Self::Error> {
        Ok(())
    }
}
