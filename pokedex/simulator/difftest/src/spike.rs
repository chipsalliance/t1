use std::fmt::Display;
use std::ops::{Deref, DerefMut};
use std::path::PathBuf;

use anyhow::Context;

pub fn run_process(
    args: &[String],
    elf_path: impl AsRef<std::ffi::OsStr>,
) -> anyhow::Result<SpikeLog> {
    let spike_exec = which::which("spike").with_context(|| "spike exec not found")?;

    let result = std::process::Command::new(&spike_exec)
        .args(args)
        .arg(&elf_path)
        .output()
        .with_context(|| "fail exeuting spike")?;

    if !result.status.success() {
        println!("{}", String::from_utf8_lossy(&result.stderr));
        anyhow::bail!(
            "fail to execute spike with args {} for elf {}",
            args.join(" "),
            elf_path.as_ref().to_str().unwrap()
        );
    }

    let elf_path = PathBuf::from(&elf_path);
    let spike_log_path = format!(
        "./{}-spike-commits.log",
        elf_path.file_name().unwrap().to_string_lossy()
    );
    std::fs::write(spike_log_path, &result.stderr)
        .with_context(|| "fail storing spike commit log")?;
    let stdout = String::from_utf8_lossy(&result.stderr);
    let spike_log_ast = parse_spike_log(stdout);

    Ok(spike_log_ast)
}

fn parse_spike_log(log: impl AsRef<str>) -> SpikeLog {
    log.as_ref()
        .lines()
        .enumerate()
        .map(|(line_number, line)| match SpikeLogSyntax::parse(line) {
            Err(err) => {
                panic!("fail parsing line at line {line_number}: {err}. Original line: '{line}'")
            }
            Ok(ast) => ast,
        })
        .collect()
}

#[derive(Debug, PartialEq)]
pub struct SpikeLog(Vec<SpikeLogSyntax>);

impl FromIterator<SpikeLogSyntax> for SpikeLog {
    fn from_iter<T: IntoIterator<Item = SpikeLogSyntax>>(iter: T) -> Self {
        Self(Vec::from_iter(iter))
    }
}

impl Deref for SpikeLog {
    type Target = Vec<SpikeLogSyntax>;
    fn deref(&self) -> &Self::Target {
        &self.0
    }
}

impl DerefMut for SpikeLog {
    fn deref_mut(&mut self) -> &mut Self::Target {
        &mut self.0
    }
}

impl SpikeLog {
    pub fn is_empty(&self) -> bool {
        self.0.is_empty()
    }

    // TODO: future usage
    #[allow(dead_code)]
    pub fn has_register_commits(&self) -> Vec<&SpikeLogSyntax> {
        self.0
            .iter()
            .filter(|log| {
                !log.commits.is_empty() && log.commits.iter().any(|c| c.is_register_write_commit())
            })
            .collect()
    }

    pub fn has_memory_write_commits(&self) -> Vec<&SpikeLogSyntax> {
        self.0
            .iter()
            .filter(|log| !log.commits.is_empty() && log.commits.iter().any(|c| c.is_mem_write()))
            .collect()
    }
}

/// Describe all load store behavior occurs at Spike side
#[derive(Debug, PartialEq)]
pub enum LoadStoreType {
    Register {
        index: u8,
        value: u32,
    },
    CSR {
        index: u32,
        name: String,
        value: u32,
    },
    MemoryRead {
        address: u32,
    },
    MemoryWrite {
        address: u32,
        value: u32,
    },
}

impl Display for LoadStoreType {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Register { index, value } => {
                write!(f, "write register x{index} with {value:#010x}")
            }
            Self::MemoryWrite { address, value } => {
                write!(f, "write memory {address:#010x} with {value:#010x}")
            }
            Self::MemoryRead { address } => write!(f, "read memory {address:#010x}"),
            Self::CSR { index, name, value } => {
                write!(f, "write CSR {index:#010x}({name}) with {value}")
            }
        }
    }
}

impl LoadStoreType {
    pub fn is_register_write_commit(&self) -> bool {
        match self {
            Self::Register { .. } => true,
            _ => false,
        }
    }

    pub fn get_register(&self) -> Option<(u8, u32)> {
        match self {
            Self::Register { index, value } => Some((*index, *value)),
            _ => None,
        }
    }

    pub fn is_csr_commit(&self) -> bool {
        match self {
            Self::CSR { .. } => true,
            _ => false,
        }
    }

    pub fn get_csr(&self) -> Option<(u32, String, u32)> {
        match self {
            Self::CSR { index, name, value } => Some((*index, name.to_string(), *value)),
            _ => None,
        }
    }

    pub fn is_mem_write(&self) -> bool {
        match self {
            Self::MemoryWrite { .. } => true,
            _ => false,
        }
    }

    pub fn get_mem_write(&self) -> Option<(u32, u32)> {
        match self {
            Self::MemoryWrite { address, value } => Some((*address, *value)),
            _ => None,
        }
    }
}

#[derive(Debug, Default, PartialEq)]
pub struct LoadStoreCommits(Vec<LoadStoreType>);

impl LoadStoreCommits {
    pub fn get_mem_write(&self) -> Option<(u32, u32)> {
        self.0.iter().find_map(|commit| commit.get_mem_write())
    }

    pub fn has_reg_write_commit(&self) -> bool {
        self.0
            .iter()
            .any(|commit| commit.is_register_write_commit())
    }
}

impl Deref for LoadStoreCommits {
    type Target = Vec<LoadStoreType>;

    fn deref(&self) -> &Self::Target {
        &self.0
    }
}

impl DerefMut for LoadStoreCommits {
    fn deref_mut(&mut self) -> &mut Self::Target {
        &mut self.0
    }
}

#[derive(Debug, Default, PartialEq)]
pub struct SpikeLogSyntax {
    pub core: u8,
    pub privilege: u8,
    pub pc: u32,
    pub instruction: u32,
    pub commits: LoadStoreCommits,
}

impl Display for SpikeLogSyntax {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let SpikeLogSyntax {
            core,
            privilege,
            pc,
            instruction,
            commits,
        } = self;

        let display_commit = commits.iter().fold(String::new(), |display, event| {
            format!("{display}* {event}\n")
        });

        indoc::writedoc! {f, "
            core: {core}
            priviledge: {privilege}
            pc: {pc:#010x}
            instruction: {instruction:#010x}
            commits:
            {}
        ", display_commit}
    }
}

impl SpikeLogSyntax {
    pub fn get_register_write_commits(&self) -> Vec<&LoadStoreType> {
        self.commits
            .iter()
            .filter(|event| event.is_register_write_commit())
            .collect()
    }

    pub fn get_csr_write_commits(&self) -> Vec<&LoadStoreType> {
        self.commits
            .iter()
            .filter(|event| event.is_csr_commit())
            .collect()
    }

    pub fn has_reg_write_commit(&self) -> bool {
        self.commits.has_reg_write_commit()
    }
}

enum ParseCursor<'a> {
    Core,
    Priv,
    Pc,
    Insn,
    RegParseBegin,
    RegParseName(&'a str),
    CsrParseName(&'a str),
    MemParseBegin,
    MemParseRead(u32),
    Error(String),
}

struct ParseContext<'a> {
    cursor: ParseCursor<'a>,
    state: SpikeLogSyntax,
}

impl Default for ParseContext<'_> {
    fn default() -> Self {
        Self {
            cursor: ParseCursor::Core,
            state: SpikeLogSyntax::default(),
        }
    }
}

impl ParseContext<'_> {
    fn new() -> Self {
        Self::default()
    }

    fn try_parse(self) -> Result<SpikeLogSyntax, String> {
        match self.cursor {
            ParseCursor::Error(err) => Err(err),
            _ => Ok(self.state),
        }
    }
}

impl SpikeLogSyntax {
    fn parse<'a>(line: &'a str) -> Result<Self, String> {
        fn to_error<'a>(expect: &str, actual: &str, err: impl Display) -> ParseCursor<'a> {
            ParseCursor::Error(format!("expect {expect}, get '{actual}': {err}"))
        }

        let segments: Vec<&'a str> = line
            .trim()
            .split(" ")
            .filter(|part| !part.is_empty())
            .collect();
        let total_segments = segments.len();
        let ctx: ParseContext =
            segments
                .into_iter()
                .enumerate()
                .fold(ParseContext::new(), |mut ctx, (index, elem)| {
                    match ctx.cursor {
                        // skip literal "core"
                        ParseCursor::Core if elem == "core" => ctx,
                        // vec[0] is core id. core id always comes with ":", strip it here
                        ParseCursor::Core => {
                            if !elem.ends_with(":") {
                                ctx.cursor = to_error(
                                    "':' suffixed string",
                                    elem,
                                    "core_id not ends with ':'",
                                );
                                return ctx;
                            }

                            match (elem[0..elem.len() - 1]).parse::<u8>() {
                                Ok(v) => {
                                    ctx.state.core = v;
                                    ctx.cursor = ParseCursor::Priv;
                                    ctx
                                }
                                Err(err) => {
                                    ctx.cursor = to_error("u8 value core_id", elem, err);
                                    ctx
                                }
                            }
                        }
                        ParseCursor::Priv => match elem.parse::<u8>() {
                            Ok(priv_id) => {
                                ctx.state.privilege = priv_id;
                                ctx.cursor = ParseCursor::Pc;
                                ctx
                            }
                            Err(err) => {
                                ctx.cursor = to_error("u8 value priv_id", elem, err);
                                ctx
                            }
                        },
                        ParseCursor::Pc => {
                            if !elem.starts_with("0x") {
                                ctx.cursor =
                                    to_error("hex string", elem, "pc value not prefixed with '0x'");
                                return ctx;
                            };

                            match u32::from_str_radix(elem.trim_start_matches("0x"), 16) {
                                Ok(pc) => {
                                    ctx.state.pc = pc;
                                    ctx.cursor = ParseCursor::Insn;
                                    ctx
                                }
                                Err(err) => {
                                    ctx.cursor = to_error("u32 value pc", elem, err);
                                    ctx
                                }
                            }
                        }
                        // vec[3] is instruction decode, it always has surrounding parentheses
                        ParseCursor::Insn => {
                            if !elem.starts_with("(0x") {
                                ctx.cursor = to_error(
                                    "parentheses surrounding hex string",
                                    elem,
                                    "instruction not started with '(0x'",
                                );
                                return ctx;
                            };

                            if !elem.ends_with(")") {
                                ctx.cursor = to_error(
                                    "parentheses surrounding hex string",
                                    elem,
                                    "instruction not ends with ')'",
                                );
                                return ctx;
                            };

                            match u32::from_str_radix(&elem[3..elem.len() - 1], 16) {
                                Ok(insn) => {
                                    ctx.state.instruction = insn;
                                    ctx.cursor = ParseCursor::RegParseBegin;
                                    ctx
                                }
                                Err(err) => {
                                    ctx.cursor = to_error("u32 value instruction", elem, err);
                                    ctx
                                }
                            }
                        }
                        // then all other parts are register change, memory load and memory write
                        // spike handle memory in undocumented way and we don't need to compare memory
                        // behavior to spike, so trim memory here
                        ParseCursor::RegParseBegin if elem != "mem" && !elem.starts_with("0x") => {
                            let prefix = elem.chars().next().unwrap();
                            match prefix {
                                'x' => ctx.cursor = ParseCursor::RegParseName(elem),
                                'c' => ctx.cursor = ParseCursor::CsrParseName(elem),
                                _ => {
                                    ctx.cursor = to_error(
                                        "register name",
                                        elem,
                                        "register name not prefixed with 'x', 'f', 'v' or 'c'",
                                    )
                                }
                            }

                            ctx
                        }
                        ParseCursor::RegParseBegin if elem == "mem" => {
                            ctx.cursor = ParseCursor::MemParseBegin;
                            ctx
                        }
                        ParseCursor::RegParseName(reg_name) => {
                            if !elem.starts_with("0x") {
                                ctx.cursor = to_error(
                                    "hex string",
                                    elem,
                                    "register value not prefixed with '0x'",
                                );
                                return ctx;
                            };

                            let Ok(reg_idx): Result<u8, _> = reg_name[1..].parse() else {
                                ctx.cursor = to_error(
                                    "register index",
                                    elem,
                                    "register is not a valid digit",
                                );
                                return ctx;
                            };

                            match u32::from_str_radix(elem.trim_start_matches("0x"), 16) {
                                Ok(reg_val) => {
                                    ctx.cursor = ParseCursor::RegParseBegin;
                                    ctx.state.commits.push(LoadStoreType::Register {
                                        index: reg_idx,
                                        value: reg_val,
                                    });
                                    ctx
                                }
                                Err(err) => {
                                    ctx.cursor =
                                        to_error("u32 value register_value", elem, err.to_string());
                                    ctx
                                }
                            }
                        }
                        ParseCursor::MemParseBegin => {
                            if !elem.starts_with("0x") {
                                ctx.cursor = to_error(
                                    "hex string",
                                    elem,
                                    "memory value not prefixed with '0x'",
                                );
                                return ctx;
                            };

                            match u32::from_str_radix(elem.trim_start_matches("0x"), 16) {
                                Ok(address) => {
                                    ctx.cursor = ParseCursor::MemParseRead(address);
                                    let is_line_end = index == total_segments - 1;
                                    if is_line_end {
                                        ctx.state
                                            .commits
                                            .push(LoadStoreType::MemoryRead { address });
                                    }
                                    ctx
                                }
                                Err(err) => {
                                    ctx.cursor = to_error("u32 memory address", elem, err);
                                    ctx
                                }
                            }
                        }
                        ParseCursor::MemParseRead(address) if elem.starts_with("0x") => {
                            match u32::from_str_radix(elem.trim_start_matches("0x"), 16) {
                                Ok(write_val) => {
                                    ctx.cursor = ParseCursor::RegParseBegin;
                                    ctx.state.commits.push(LoadStoreType::MemoryWrite {
                                        address,
                                        value: write_val,
                                    });
                                    ctx
                                }
                                Err(err) => {
                                    ctx.cursor =
                                        to_error("u32 memory write value", elem, err.to_string());
                                    ctx
                                }
                            }
                        }
                        ParseCursor::MemParseRead(address) => {
                            ctx.cursor = ParseCursor::RegParseBegin;
                            ctx.state
                                .commits
                                .push(LoadStoreType::MemoryRead { address });
                            ctx
                        }
                        ParseCursor::CsrParseName(csr) => {
                            ctx.cursor = ParseCursor::RegParseBegin;
                            // parse c%d_%s (c773_mtvec)
                            let pairs: Vec<&str> = csr[1..].split("_").collect();
                            let Ok(index): Result<u32, _> = pairs[0].parse() else {
                                ctx.cursor = to_error(
                                    "csr number",
                                    pairs[0],
                                    "csr number is not valid digit",
                                );
                                return ctx;
                            };
                            let name = pairs[1].to_string();

                            let Ok(value): Result<u32, _> =
                                u32::from_str_radix(elem.trim_start_matches("0x"), 16)
                            else {
                                ctx.cursor =
                                    to_error("csr value", elem, "csr value is not valid digit");
                                return ctx;
                            };
                            ctx.state
                                .commits
                                .push(LoadStoreType::CSR { index, name, value });

                            ctx
                        }
                        // passthrough error
                        _ => ctx,
                    }
                });

        ctx.try_parse()
    }
}

#[test]
fn test_parsing_spike_log_ast() {
    let mut d = std::path::PathBuf::from(env!("CARGO_MANIFEST_DIR"));
    d.push("assets/example.spike.log");
    let sample_log = std::fs::read(d).unwrap();
    assert!(!sample_log.is_empty());
    let raw = String::from_utf8_lossy(&sample_log);
    let ast = parse_spike_log(&raw);
    assert!(!ast.is_empty());

    let expect = SpikeLog(vec![
        SpikeLogSyntax {
            core: 0,
            privilege: 3,
            pc: 0x800000ac,
            instruction: 0x30529073,
            commits: LoadStoreCommits(vec![LoadStoreType::CSR {
                index: 0x305,
                name: "mtvec".to_string(),
                value: 0x8000000c,
            }]),
        },
        SpikeLogSyntax {
            core: 0,
            privilege: 3,
            pc: 4096,
            instruction: 663,
            commits: LoadStoreCommits(vec![LoadStoreType::Register {
                index: 5,
                value: 4096,
            }]),
        },
        SpikeLogSyntax {
            core: 0,
            privilege: 3,
            pc: 4100,
            instruction: 33719699,
            commits: LoadStoreCommits(vec![LoadStoreType::Register {
                index: 11,
                value: 4128,
            }]),
        },
        SpikeLogSyntax {
            core: 0,
            privilege: 3,
            pc: 4108,
            instruction: 25342595,
            commits: LoadStoreCommits(vec![
                LoadStoreType::Register {
                    index: 5,
                    value: 2147483700,
                },
                LoadStoreType::MemoryRead { address: 4120 },
            ]),
        },
        SpikeLogSyntax {
            core: 0,
            privilege: 3,
            pc: 4112,
            instruction: 163943,
            commits: LoadStoreCommits(Vec::new()),
        },
        SpikeLogSyntax {
            core: 0,
            privilege: 3,
            pc: 2147483860,
            instruction: 1129507,
            commits: LoadStoreCommits(vec![LoadStoreType::MemoryWrite {
                address: 2684354552,
                value: 2147483840,
            }]),
        },
    ]);
    assert_eq!(ast, expect);
}
