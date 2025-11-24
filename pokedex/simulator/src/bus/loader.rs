use std::{ops::Range, path::Path};

use anyhow::{Context as _, bail};

use crate::bus::{AddressSpaceDescNode, Addressable, Bus, MMIOAddrDecoder, NaiveMemory};

#[derive(Debug, knuffel::Decode)]
struct MmapConfig {
    #[knuffel(argument)]
    name: String,
    #[knuffel(property)]
    offset: u32,
}

#[derive(Debug, knuffel::Decode)]
struct MmioConfig {
    #[knuffel(child, unwrap(argument))]
    base: u32,
    #[knuffel(child, unwrap(argument))]
    length: u32,
    #[knuffel(children(name = "mmap"))]
    mmaps: Vec<MmapConfig>,
}

#[derive(Debug, knuffel::Decode)]
struct SramConfig {
    #[knuffel(child, unwrap(argument))]
    base: u32,
    #[knuffel(child, unwrap(argument))]
    length: u32,
}

#[derive(Debug, knuffel::Decode)]
struct PokedexConfig {
    #[knuffel(child, unwrap(argument))]
    reset_vector: Option<u32>,
    #[knuffel(child)]
    mmio: MmioConfig,
    #[knuffel(child)]
    sram: SramConfig,
}

pub fn load_from_config_str(path: &str, content: &str) -> anyhow::Result<Bus> {
    let config: PokedexConfig = knuffel::parse(path, content)?;

    let configuration = [
        AddressSpaceDescNode::Sram {
            name: "single-naive-memory".to_string(),
            base: config.sram.base,
            length: config.sram.length,
        },
        AddressSpaceDescNode::Mmio {
            base: config.mmio.base,
            length: config.mmio.length,
            mmap: config
                .mmio
                .mmaps
                .iter()
                .map(|mmap| (mmap.name.to_string(), mmap.offset))
                .collect(),
        },
    ];

    let mut segments = Vec::new();
    let mut exit_state = None;
    for node in configuration {
        match node {
            AddressSpaceDescNode::Sram {
                name: _,
                base,
                length,
            } => {
                let naive_memory = NaiveMemory::new(length as usize);
                let boxed: Box<dyn Addressable> = Box::new(naive_memory);
                segments.push(((base..(base + length)), boxed))
            }
            AddressSpaceDescNode::Mmio { base, length, mmap } => {
                let (mmio_decoder, controllers) = MMIOAddrDecoder::try_build_from(&mmap)?;
                exit_state = Some(controllers);
                let boxed: Box<dyn Addressable> = Box::new(mmio_decoder);
                segments.push(((base..base + length), boxed))
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
        bail!(
            "Address space with offset={:#010x} length={:#010x} overlapped previous address space",
            range.start,
            range.end - range.start
        )
    }

    Ok(Bus {
        address_space: segments,
        exit_state: exit_state.unwrap(),
        reset_vector: config.reset_vector,
    })
}

pub fn load_from_config_path(config_path: &Path) -> anyhow::Result<Bus> {
    let config_content = std::fs::read_to_string(config_path)
        .with_context(|| format!("failed to read {config_path:?}"))?;
    let config_path_str = config_path.display().to_string();
    load_from_config_str(&config_path_str, &config_content)
        .with_context(|| format!("in parsing {config_path_str}"))
}
