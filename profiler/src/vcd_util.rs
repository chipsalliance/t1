use std::{collections::HashMap, io::BufRead, rc::Rc};

use vcd::{IdCode, ScopeItem};

use crate::input_hier::{SignalData, ValueRecord};

pub fn time_to_cycle(time: u64) -> u32 {
    let cycle = (time / 20000) as u32;
    assert_eq!(time, 20000 * (cycle as u64) + 10000);
    cycle
}

// sync with t1rocketemu/TestBench
pub const RESET_DEASSERT_TIME: u64 = 100000;
pub const FIRST_CYCLE: u32 = 5;

pub fn process_signal_map(
    signal_map: HashMap<IdCode, (Option<u32>, Vec<RawValueRecord>)>,
) -> HashMap<IdCode, SignalData> {
    signal_map
        .into_iter()
        .map(|(code, (width, data))| {
            let data = match width {
                None => {
                    let mut started = false;
                    let mut last_value = (true, 0);
                    let mut data2 = vec![];
                    for x in data {
                        if x.time >= RESET_DEASSERT_TIME {
                            let cycle = time_to_cycle(x.time);
                            if !started {
                                started = true;
                                if cycle > FIRST_CYCLE {
                                    data2.push(ValueRecord {
                                        cycle: FIRST_CYCLE,
                                        is_x: last_value.0,
                                        value: last_value.1 != 0,
                                    });
                                }
                            }
                            data2.push(ValueRecord {
                                cycle,
                                is_x: x.is_x,
                                value: x.value != 0,
                            });
                        } else {
                            last_value = (x.is_x, x.value);
                        }
                    }
                    if !started {
                        data2.push(ValueRecord {
                            cycle: FIRST_CYCLE,
                            is_x: last_value.0,
                            value: last_value.1 != 0,
                        });
                    }
                    SignalData::Scalar {
                        data: Rc::new(data2),
                    }
                }
                Some(width) => {
                    let mut started = false;
                    let mut last_value = (true, 0);
                    let mut data2 = vec![];
                    for x in data {
                        if x.time >= RESET_DEASSERT_TIME {
                            let cycle = time_to_cycle(x.time);
                            if !started {
                                started = true;
                                if cycle > FIRST_CYCLE {
                                    data2.push(ValueRecord {
                                        cycle: FIRST_CYCLE,
                                        is_x: last_value.0,
                                        value: last_value.1 as u64,
                                    });
                                }
                            }
                            data2.push(ValueRecord {
                                cycle,
                                is_x: x.is_x,
                                value: x.value as u64,
                            });
                        } else {
                            last_value = (x.is_x, x.value);
                        }
                    }
                    if !started {
                        data2.push(ValueRecord {
                            cycle: FIRST_CYCLE,
                            is_x: last_value.0,
                            value: last_value.1 as u64,
                        });
                    }
                    SignalData::Vector {
                        width,
                        data: Rc::new(data2),
                    }
                }
            };
            (code, data)
        })
        .collect()
}

pub fn collect_vars(scope: &vcd::Scope) -> HashMap<String, (IdCode, Option<u32>)> {
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

#[derive(Debug, PartialEq, Eq)]
pub struct RawValueRecord {
    pub time: u64,
    pub is_x: bool,
    pub value: u32,
}

#[derive(Debug)]
pub enum Value {
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

pub struct ProcessReport {
    pub max_time: u64,
}

// return max time
pub fn process_vcd<T: BufRead, Callback>(
    vcd: &mut vcd::Parser<T>,
    mut callback: Callback,
) -> anyhow::Result<ProcessReport>
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
    Ok(ProcessReport { max_time: time })
}
