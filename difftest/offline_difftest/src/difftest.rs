mod dut;
mod spike;

use dut::*;
use serde::Deserialize;
use spike::SpikeHandle;
use std::path::Path;
use tracing::error;

pub struct Difftest {
	spike: SpikeHandle,
	dut: Dut,
	config: Config,
}

#[derive(Deserialize, Debug)]
pub struct LsuBankParameter {
	name: String,
	region: String,
	beatbyte: u32,
	accessScalar: bool,
}

#[derive(Deserialize, Debug)]
pub struct LogicModuleParametersParameter {
	datapathWidth: u32,
	latency: u32,
}

#[derive(Deserialize, Debug)]
pub struct LogicModuleParameter {
	parameter: LogicModuleParametersParameter,
	generator: String,
}

#[derive(Deserialize, Debug)]
pub struct OtherModuleParametersParameter {
	datapathWidth: u32,
	vlMaxBits: u32,
	groupNumberBits: u32,
	laneNumberBits: u32,
	dataPathByteWidth: u32,
	latency: u32,
}

#[derive(Deserialize, Debug)]
pub struct OtherModuleParameters {
	parameter: OtherModuleParametersParameter,
	generator: String,
}

#[derive(Deserialize, Debug)]
pub struct VfuInstantiateParameter {
	slotCount: u32,
	logicModuleParameters: Vec<(LogicModuleParameter, Vec<u32>)>,
	aluModuleParameters: Vec<(LogicModuleParameter, Vec<u32>)>,
	shifterModuleParameters: Vec<(LogicModuleParameter, Vec<u32>)>,
	mulModuleParameters: Vec<(LogicModuleParameter, Vec<u32>)>,
	divModuleParameters: Vec<(LogicModuleParameter, Vec<u32>)>,
	divfpModuleParameters: Vec<(LogicModuleParameter, Vec<u32>)>,
	floatModuleParameters: Vec<(LogicModuleParameter, Vec<u32>)>,
	otherModuleParameters: Vec<(OtherModuleParameters, Vec<u32>)>,
}

#[derive(Deserialize, Debug)]
pub struct Parameter {
	vLen: u32,
	dLen: u32,
	extensions: Vec<String>,
	lsuBankParameters: Vec<LsuBankParameter>,
	vrfBankSize: u32,
	vrfRamType: String,
	vfuInstantiateParameter: VfuInstantiateParameter,
}

#[derive(Deserialize, Debug)]
pub struct Config {
	parameter: Parameter,
	generator: String,
}

fn read_config(path: &Path) -> anyhow::Result<Config> {
	let file = std::fs::File::open(path)?;
	let reader = std::io::BufReader::new(file);
	let json = serde_json::from_reader(reader)?;
	Ok(json)
}

impl Difftest {
	pub fn new(size: usize, elf_file: String, log_file: String, config_file: String) -> Self {
		Self {
			spike: SpikeHandle::new(size, Path::new(&elf_file)),
			dut: Dut::new(Path::new(&log_file)),
			config: read_config(Path::new(&config_file)).unwrap(),
		}
	}

	pub fn diff(&mut self) -> anyhow::Result<()> {
		let event = self.dut.step()?;
		self.spike.step(&self.config)?;

		match &*event.event {
			"peekTL" => {
				// check align
				let addr = event.parameter.address.unwrap() as u128;
				let size = event.parameter.size.unwrap();
				if addr % (1 << size) != 0 {
					error!("unaligned access (addr={:08X}, size={})", addr, 1 << size)
				}
			}
			_ => {}
		}

		Ok(())
	}
}
