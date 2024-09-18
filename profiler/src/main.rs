use std::{
    collections::{hash_map, HashMap},
    fs::File,
    io::{BufRead, BufReader},
    path::PathBuf,
};

use anyhow::{anyhow, bail, ensure};
use clap::Parser;
use input_hier::{InputVars, VarCollector};
use vcd::{IdCode, ScopeItem};

mod input_hier;
use input_hier::Collect as _;

#[derive(Parser)]
struct Cli {
    input_vcd: PathBuf,
}

fn main() -> anyhow::Result<()> {
    let cli = Cli::parse();

    let input = File::open(cli.input_vcd)?;

    let mut input = vcd::Parser::new(BufReader::new(input));
    let header = input.parse_header()?;

    ensure!(header.timescale == Some((1, vcd::TimescaleUnit::PS)));

    let prof_scope = header
        .find_scope(&["TestBench", "verification", "profData"])
        .ok_or_else(|| anyhow!("can not find scope /TestBench/profData"))?;

    // Though `Scope` has `find_var` method, however it uses linear search.
    // Collect to a hashmap to avoid quadratic behavior.
    let vars = collect_vars(&prof_scope);

    let input_vars = InputVars::from_vars(&vars);
    let c = input_vars.collect();

    let mut signal_map = HashMap::<IdCode, Option<u32>>::new();
    for (code, width) in c.id_list() {
        match signal_map.entry(code) {
            hash_map::Entry::Occupied(e) => assert_eq!(*e.get(), width),
            hash_map::Entry::Vacant(e) => {
                e.insert(width);
            }
        }
    }

    process(&mut input, |time, code, value| {
        if signal_map.contains_key(&code) {
            println!("[T={time}] {value:?}");
        }
    })?;

    Ok(())
}

fn collect_vars(scope: &vcd::Scope) -> HashMap<String, (IdCode, Option<u32>)> {
    let mut signals = HashMap::new();
    for item in &scope.items {
        match item {
            ScopeItem::Var(var) => {
                let name = &var.reference;
                let width = match var.index {
                    None => {
                        // scalar
                        assert_eq!(var.size, 1);
                        None
                    }
                    Some(index) => {
                        // vector
                        assert_eq!(index, vcd::ReferenceIndex::Range(var.size as i32 - 1, 0));
                        Some(var.size)
                    }
                };

                signals.insert(name.into(), (var.code, width));
            }
            ScopeItem::Scope(_) => {
                // ignore subscopes
            }
            _ => {}
        }
    }

    signals
}

#[derive(Debug)]
enum Value {
    Scalar(Option<bool>),
    Vector { width: u8, data: Option<u32> },
}

impl Value {
    fn from_scalar(value: vcd::Value) -> Self {
        let value = match value {
            vcd::Value::V1 => Some(true),
            vcd::Value::V0 => Some(false),
            _ => None,
        };
        Value::Scalar(value)
    }

    fn from_vector(value: vcd::Vector) -> Self {
        let width = value.len().try_into().unwrap();
        assert!(1 <= width && width <= 32);

        let mut data: u32 = 0;

        // value[0] is MSB, value[width-1] is LSB
        // here we reverse the bit order
        for s in &value {
            match s {
                vcd::Value::V1 => {
                    data = (data << 1) + 1;
                }
                vcd::Value::V0 => {
                    data = data << 1;
                }
                _ => return Value::Vector { width, data: None },
            }
        }

        Value::Vector {
            width,
            data: Some(data),
        }
    }
}

fn process<T: BufRead, Callback>(
    vcd: &mut vcd::Parser<T>,
    mut callback: Callback,
) -> anyhow::Result<()>
where
    Callback: FnMut(u64, vcd::IdCode, Value),
{
    let mut time: u64 = 0;
    while let Some(cmd) = vcd.next().transpose()? {
        // println!("{cmd:?}");
        match cmd {
            vcd::Command::Begin(_) | vcd::Command::End(_) => {
                // ignored
            }
            vcd::Command::Timestamp(new_time) => {
                time = new_time;
            }
            vcd::Command::ChangeScalar(id, value) => {
                callback(time, id, Value::from_scalar(value));
            }
            vcd::Command::ChangeVector(id, value) => {
                callback(time, id, Value::from_vector(value));
            }
            _ => {
                unreachable!("unexpected command: {cmd:?}")
            }
        }
    }
    Ok(())
}
