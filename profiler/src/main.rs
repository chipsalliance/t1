use std::{
    collections::{hash_map, HashMap},
    fs::File,
    io::{BufRead, BufReader},
    path::PathBuf,
};

use anyhow::{anyhow, ensure};
use clap::Parser;
use input_hier::InputVars;
use vcd::IdCode;
use vcd_util::{
    collect_vars, process_signal_map, process_vcd, time_to_cycle, ProcessReport, RawValueRecord,
    Value, FIRST_CYCLE, RESET_DEASSERT_TIME,
};

mod input_hier;

mod vcd_util;

#[derive(Parser)]
struct Cli {
    input_vcd: PathBuf,
}

fn main() -> anyhow::Result<()> {
    let cli = Cli::parse();
    env_logger::builder().format_timestamp(None).init();

    let input = File::open(cli.input_vcd)?;

    let mut input = vcd::Parser::new(BufReader::new(input));
    let header = input.parse_header()?;

    ensure!(header.timescale == Some((1, vcd::TimescaleUnit::PS)));

    let prof_scope = header
        .find_scope(&["TestBench", "verification", "profData"])
        .ok_or_else(|| anyhow!("can not find scope /TestBench/profData"))?;

    // Though `Scope` has `find_var` method, however it uses linear search.
    // Collect to a hashmap to avoid quadratic behavior.
    let vars = collect_vars(prof_scope);

    let input_vars = InputVars::from_vars(&vars);
    let c = input_vars.collect();

    let clock = vars["clock"];
    let reset = vars["reset"];
    let mut clock_values = vec![];
    let mut reset_values = vec![];
    assert_eq!(clock.1, None);
    assert_eq!(reset.1, None);

    let mut signal_map = HashMap::<IdCode, (Option<u32>, Vec<RawValueRecord>)>::new();
    for (code, width) in c.id_list() {
        match signal_map.entry(code) {
            hash_map::Entry::Occupied(e) => assert_eq!(e.get().0, width),
            hash_map::Entry::Vacant(e) => {
                e.insert((width, vec![]));
            }
        }
    }

    let ProcessReport { max_time } = process_vcd(&mut input, |time, code, value| {
        if code == clock.0 {
            match value {
                Value::Scalar(value) => clock_values.push(RawValueRecord {
                    time,
                    is_x: value.is_none(),
                    value: value.unwrap_or(false) as u32,
                }),
                _ => unreachable!(),
            }
        }

        if code == reset.0 {
            match value {
                Value::Scalar(value) => reset_values.push(RawValueRecord {
                    time,
                    is_x: value.is_none(),
                    value: value.unwrap_or(false) as u32,
                }),
                _ => unreachable!(),
            }
        }

        if let Some(v) = signal_map.get_mut(&code) {
            match value {
                Value::Scalar(value) => {
                    assert_eq!(v.0, None);
                    v.1.push(RawValueRecord {
                        time,
                        is_x: value.is_none(),
                        value: value.unwrap_or(false) as u32,
                    });
                }
                Value::Vector { width, data } => {
                    assert_eq!(v.0, Some(width as u32));
                    v.1.push(RawValueRecord {
                        time,
                        is_x: data.is_none(),
                        value: data.unwrap_or(0),
                    });
                }
            }
        }
    })?;

    let max_cycle = time_to_cycle(max_time);
    log::info!("first_cycle = {FIRST_CYCLE}");
    log::info!("max_cycle = {max_cycle}");

    assert_eq!(reset_values.len(), 2);
    assert_eq!(
        reset_values[1],
        RawValueRecord {
            time: RESET_DEASSERT_TIME,
            is_x: false,
            value: 0,
        }
    );

    let signal_map = process_signal_map(signal_map);

    c.set_with_signal_map(&signal_map);

    let _ = input_hier::process(&input_vars, FIRST_CYCLE..max_cycle + 1);

    Ok(())
}
