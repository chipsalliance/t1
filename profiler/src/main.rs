use std::{fs::File, io::BufReader, path::PathBuf};

use anyhow::anyhow;
use clap::Parser;
use vcd::ScopeItem;

#[derive(Parser)]
struct Cli {
    input_vcd: PathBuf,
}

fn main() -> anyhow::Result<()> {
    let cli = Cli::parse();

    let input = File::open(cli.input_vcd)?;

    let mut input = vcd::Parser::new(BufReader::new(input));
    let header = input.parse_header()?;

    let prof_data = header
        .find_scope(&["TestBench", "profData"])
        .ok_or_else(|| anyhow!("can not find scope /TestBench/profData"))?;

    for item in &prof_data.items {
        match item {
            ScopeItem::Var(var) => {
                let name = &var.reference;
                let width = var.size;
                println!("- {name} : bit({width})");
            }
            _ => {}
        }
    }

    Ok(())
}
