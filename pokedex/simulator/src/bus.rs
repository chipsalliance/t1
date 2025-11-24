use std::{
    ops::Range,
    path::Path,
    sync::{
        Arc,
        atomic::{AtomicU64, Ordering},
    },
};

use anyhow::{bail, ensure};
use tracing::debug;

mod elf;
mod loader;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum AtomicOp {
    Swap,
    Add,
    And,
    Or,
    Xor,
    Min,
    Minu,
    Max,
    Maxu,
}

impl AtomicOp {
    pub fn do_arith_u32(self, mem_value: u32, core_value: u32) -> u32 {
        match self {
            AtomicOp::Swap => core_value,
            AtomicOp::Add => mem_value.wrapping_add(core_value),
            AtomicOp::And => mem_value & core_value,
            AtomicOp::Or => mem_value | core_value,
            AtomicOp::Xor => mem_value ^ core_value,
            AtomicOp::Min => i32::min(mem_value as i32, core_value as i32) as u32,
            AtomicOp::Max => i32::max(mem_value as i32, core_value as i32) as u32,
            AtomicOp::Minu => u32::min(mem_value, core_value),
            AtomicOp::Maxu => u32::max(mem_value, core_value),
        }
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

pub struct Bus {
    address_space: Vec<(Range<u32>, Box<dyn Addressable>)>,
    exit_state: Arc<AtomicU64>,
    reset_vector: Option<u32>,
}

impl Bus {
    pub fn load_from_config(config_path: &Path) -> anyhow::Result<Self> {
        loader::load_from_config_path(config_path)
    }

    pub fn load_from_default_config() -> Self {
        let default_config_content = include_str!("../assets/configs.kdl");
        loader::load_from_config_str("embedded-default-config.kdl", default_config_content)
            .expect("error in parsing default bus config")
    }

    // return ELF entrypoint if success
    pub fn load_elf(&mut self, elf_path: &Path) -> anyhow::Result<u32> {
        elf::load_elf(self, elf_path)
    }

    // get the platform preset reset vector
    pub fn reset_vector(&self) -> Option<u32> {
        self.reset_vector
    }

    // None indicates it still runs
    pub fn try_get_exit_code(&self) -> Option<u32> {
        let raw_state = self.exit_state.load(Ordering::Acquire);
        if raw_state == 0 {
            None
        } else {
            Some(raw_state as u32)
        }
    }
}

impl Bus {
    pub fn read(&mut self, addr: u32, data: &mut [u8]) -> Result<(), BusError> {
        let result = self
            .address_space
            .iter_mut()
            .find(|(addr_space, _)| addr_space.contains(&addr));

        let Some((addr_space, device)) = result else {
            return Err(BusError::DecodeError);
        };

        let offset = addr - addr_space.start;

        device.do_bus_read(offset, data)
    }

    pub fn write(&mut self, addr: u32, data: &[u8]) -> Result<(), BusError> {
        let result = self
            .address_space
            .iter_mut()
            .find(|(addr_space, _)| addr_space.contains(&addr));

        let Some((addr_space, device)) = result else {
            return Err(BusError::DecodeError);
        };

        let offset = addr - addr_space.start;

        device.do_bus_write(offset, data)
    }

    pub fn debugger_read(&self, addr: u32, data: &mut [u8]) -> usize {
        let result = self
            .address_space
            .iter()
            .find(|(addr_space, _)| addr_space.contains(&addr));

        let Some((addr_space, device)) = result else {
            return 0;
        };

        let offset = addr - addr_space.start;

        device.do_debugger_read(offset, data)
    }
}

type ExitStateRef = Arc<AtomicU64>;

#[derive(Debug, Clone, Default)]
pub struct Syscon(Arc<AtomicU64>);
impl Syscon {
    fn new(state: ExitStateRef) -> Self {
        Self(state)
    }
}

#[derive(Debug, thiserror::Error)]
pub enum BusError {
    #[error("device fail: {id}")]
    DeviceError { id: &'static str },
    #[error("decode error")]
    DecodeError,
}

pub type BusResult<T> = Result<T, BusError>;

pub trait Addressable: Send {
    fn do_bus_read(&mut self, offset: u32, dest: &mut [u8]) -> Result<(), BusError>;
    fn do_bus_write(&mut self, offset: u32, data: &[u8]) -> Result<(), BusError>;

    // return the size of read data
    fn do_debugger_read(&self, offset: u32, dest: &mut [u8]) -> usize {
        let _ = (offset, dest);
        0
    }
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
            });
        }

        self.memory[offset as usize..offset as usize + data.len()].copy_from_slice(data);

        Ok(())
    }

    fn do_debugger_read(&self, offset: u32, dest: &mut [u8]) -> usize {
        if (offset as usize) >= self.memory.len() {
            return 0;
        }

        let data = &self.memory[offset as usize..];
        let len = data.len().min(dest.len());
        dest[..len].copy_from_slice(&data[..len]);
        len
    }
}

#[derive(Debug, Clone)]
pub enum MmioRegs {
    Exit(Syscon),
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
                eic.0.store((1 << 32) | (v as u64), Ordering::Release);
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
    fn try_build_from(config: &[(String, u32)]) -> anyhow::Result<(Self, ExitStateRef)> {
        let mut regs = Vec::new();
        let mut exit_state = None;
        for (name, offset) in config {
            match name.as_str() {
                "exit" => {
                    let s = Arc::new(AtomicU64::new(0));
                    let exit_rc = Syscon::new(s.clone());
                    exit_state = Some(s);
                    regs.push((*offset, MmioRegs::Exit(exit_rc)))
                }
                name => bail!("unsupported MMIO device {name}"),
            }
        }

        ensure!(!regs.is_empty(), "no mmio node found");

        regs.sort_unstable_by_key(|(offset, _)| *offset);

        Ok((Self { regs }, exit_state.expect("exit node not found")))
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
            debug!("unhandle MMIO write to offset={offset} with value {new_val}");
            Err(BusError::DeviceError { id: "MMIOWrite" })
        }
    }

    fn do_bus_read(&mut self, offset: u32, dest: &mut [u8]) -> Result<(), BusError> {
        let index = self.regs.binary_search_by(|(reg, _)| reg.cmp(&offset));

        if let Ok(i) = index {
            // a non u32 read is consider as implmentation bug and should be immediately bail out
            dest.copy_from_slice(&self.regs[i].1.load().to_le_bytes());

            Ok(())
        } else {
            debug!("unhandle MMIO read to offset={offset}");
            Err(BusError::DeviceError { id: "MMIORead" })
        }
    }
}
