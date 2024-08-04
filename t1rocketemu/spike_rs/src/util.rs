use crate::Spike;
use std::fs::File;
use std::io::Read;
use std::path::Path;
use xmas_elf::program::{ProgramHeader, Type};
use xmas_elf::{header, ElfFile};

pub fn load_elf(spike: &mut Spike, fname: &Path) -> anyhow::Result<u64> {
  let mut file = File::open(fname).unwrap();
  let mut buffer = Vec::new();
  file.read_to_end(&mut buffer).unwrap();

  let elf_file = ElfFile::new(&buffer).unwrap();

  let header = elf_file.header;
  assert_eq!(header.pt2.machine().as_machine(), header::Machine::RISC_V);
  assert_eq!(header.pt1.class(), header::Class::ThirtyTwo);

  for ph in elf_file.program_iter() {
    if let ProgramHeader::Ph32(ph) = ph {
      if ph.get_type() == Ok(Type::Load) {
        let offset = ph.offset as usize;
        let size = ph.file_size as usize;
        let addr = ph.virtual_addr as usize;

        let slice = &buffer[offset..offset + size];
        spike.load_bytes_to_mem(addr, size, slice.to_vec()).unwrap();
      }
    }
  }

  Ok(header.pt2.entry_point())
}

// todo: unify load_elf and load_elf_to_buffer
pub fn load_elf_to_buffer(mem: &mut [u8], fname: &Path) -> anyhow::Result<u64> {
  let mut file = File::open(fname).unwrap();
  let mut buffer = Vec::new();
  file.read_to_end(&mut buffer).unwrap();

  let elf_file = ElfFile::new(&buffer).unwrap();

  let header = elf_file.header;
  assert_eq!(header.pt2.machine().as_machine(), header::Machine::RISC_V);
  assert_eq!(header.pt1.class(), header::Class::ThirtyTwo);

  for ph in elf_file.program_iter() {
    if let ProgramHeader::Ph32(ph) = ph {
      if ph.get_type() == Ok(Type::Load) {
        let offset = ph.offset as usize;
        let size = ph.file_size as usize;
        let addr = ph.virtual_addr as usize;

        let slice = &buffer[offset..offset + size];

        let dst: &mut _ = &mut mem[addr..addr + size];
        for (i, byte) in slice.iter().enumerate() {
          dst[i] = *byte;
        }
      }
    }
  }

  Ok(header.pt2.entry_point())
}
