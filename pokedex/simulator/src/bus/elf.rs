use std::path::Path;

use crate::bus::Bus;

use anyhow::bail;
use xmas_elf::{
    ElfFile, header,
    program::{ProgramHeader, Type},
};

// return the ELF entrypoint if success
pub fn load_elf(bus: &mut Bus, elf_path: &Path) -> anyhow::Result<u32> {
    let buffer = std::fs::read(elf_path)?;

    let elf_file =
        ElfFile::new(&buffer).unwrap_or_else(|err| panic!("fail serializing ELF file: {err}"));

    let header = elf_file.header;
    if header.pt2.machine().as_machine() != header::Machine::RISC_V {
        bail!("ELF is not built for RISC-V")
    }

    for ph in elf_file.program_iter() {
        if let ProgramHeader::Ph32(ph) = ph {
            if ph.get_type() == Ok(Type::Load) {
                let offset = ph.offset as usize;
                let size = ph.file_size as usize;
                let addr = ph.virtual_addr;

                let slice = &buffer[offset..offset + size];

                if let Err(err) = bus.write(addr, slice) {
                    bail!("fail loading elf to memory: {err:?}, addr={addr:#x}, size={size:#x}");
                };
            }
        }
    }

    Ok(header
        .pt2
        .entry_point()
        .try_into()
        .expect("return ELF address should be in u32 range"))
}
