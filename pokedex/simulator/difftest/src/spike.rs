use std::fmt::Display;
use std::ops::{Deref, DerefMut};

#[derive(Debug, PartialEq)]
pub struct SpikeLogs(Vec<SpikeInsnCommit>);

impl FromIterator<SpikeInsnCommit> for SpikeLogs {
    fn from_iter<T: IntoIterator<Item = SpikeInsnCommit>>(iter: T) -> Self {
        Self(Vec::from_iter(iter))
    }
}

impl SpikeLogs {
    pub fn iter(&self) -> std::slice::Iter<'_, SpikeInsnCommit> {
        self.0.iter()
    }
}

impl SpikeLogs {
    pub fn parse_from(log: &str) -> SpikeLogs {
        log.lines()
            .enumerate()
            .map(|(line_number, line)| match SpikeInsnCommit::parse(line) {
                Err(err) => {
                    panic!(
                        "fail parsing line at line {line_number}: {err}. Original line: '{line}'"
                    )
                }
                Ok(ast) => ast,
            })
            .collect()
    }

    pub fn is_empty(&self) -> bool {
        self.0.is_empty()
    }

    pub fn has_memory_write_commits(&self) -> impl Iterator<Item = &'_ SpikeInsnCommit> {
        self.0
            .iter()
            .filter(|log| !log.events.is_empty() && log.events.iter().any(|c| c.is_mem_write()))
    }
}

/// Describe all load store behavior occurs at Spike side
#[derive(Debug, PartialEq)]
pub enum ChangedState {
    XReg {
        index: u8,
        value: u32,
    },
    FReg {
        index: u8,
        value: u32,
    },
    Csr {
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

impl Display for ChangedState {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::XReg { index, value } => {
                write!(f, "write integer register x{index} with {value:#010x}")
            }
            Self::FReg { index, value } => {
                write!(f, "write float point register x{index} with {value:#010x}")
            }
            Self::MemoryWrite { address, value } => {
                write!(f, "write memory {address:#010x} with {value:#010x}")
            }
            Self::MemoryRead { address } => write!(f, "read memory {address:#010x}"),
            Self::Csr { index, name, value } => {
                write!(f, "write CSR {index:#010x}({name}) with {value}")
            }
        }
    }
}

impl ChangedState {
    pub fn is_mem_write(&self) -> bool {
        matches!(self, Self::MemoryWrite { .. })
    }

    pub fn get_mem_write(&self) -> Option<(u32, u32)> {
        match self {
            Self::MemoryWrite { address, value } => Some((*address, *value)),
            _ => None,
        }
    }
}

#[derive(Debug, Default, PartialEq)]
pub struct StateCommits(Vec<ChangedState>);

impl StateCommits {
    pub fn get_mem_write(&self) -> Option<(u32, u32)> {
        self.0.iter().find_map(|commit| commit.get_mem_write())
    }

    pub fn have_state_changed(&self) -> bool {
        self.0.iter().any(|commit| {
            !matches!(
                commit,
                // Mem and CSR is platform related and hard to replay
                ChangedState::MemoryRead { .. }
                    | ChangedState::MemoryWrite { .. }
                    | ChangedState::Csr { .. }
            )
        })
    }
}

impl Deref for StateCommits {
    type Target = Vec<ChangedState>;

    fn deref(&self) -> &Self::Target {
        &self.0
    }
}

impl DerefMut for StateCommits {
    fn deref_mut(&mut self) -> &mut Self::Target {
        &mut self.0
    }
}

#[derive(Debug, Default, PartialEq)]
pub struct SpikeInsnCommit {
    pub core: u8,
    pub privilege: u8,
    pub pc: u32,
    pub instruction: u32,
    pub events: StateCommits,
}

impl Display for SpikeInsnCommit {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let SpikeInsnCommit {
            core,
            privilege,
            pc,
            instruction,
            events,
        } = self;

        let display_commit = events.iter().fold(String::new(), |display, event| {
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
    commit: SpikeInsnCommit,
}

impl Default for ParseContext<'_> {
    fn default() -> Self {
        Self {
            cursor: ParseCursor::Core,
            commit: SpikeInsnCommit::default(),
        }
    }
}

impl ParseContext<'_> {
    fn new() -> Self {
        Self::default()
    }

    fn try_parse(self) -> Result<SpikeInsnCommit, String> {
        match self.cursor {
            ParseCursor::Error(err) => Err(err),
            _ => Ok(self.commit),
        }
    }
}

impl SpikeInsnCommit {
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
                                    ctx.commit.core = v;
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
                                ctx.commit.privilege = priv_id;
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
                                    ctx.commit.pc = pc;
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
                                    ctx.commit.instruction = insn;
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
                                'x' | 'f' => ctx.cursor = ParseCursor::RegParseName(elem),
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

                            let reg_typ = reg_name
                                .chars()
                                .nth(0)
                                .expect("register should have prefix");

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
                                    let load_store_ty = match reg_typ {
                                        'x' => ChangedState::XReg {
                                            index: reg_idx,
                                            value: reg_val,
                                        },
                                        'f' => ChangedState::FReg {
                                            index: reg_idx,
                                            value: reg_val,
                                        },
                                        _ => unreachable!("unhandle register type met"),
                                    };

                                    ctx.commit.events.push(load_store_ty);
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
                                        ctx.commit
                                            .events
                                            .push(ChangedState::MemoryRead { address });
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
                                    ctx.commit.events.push(ChangedState::MemoryWrite {
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
                            ctx.commit.events.push(ChangedState::MemoryRead { address });
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
                            ctx.commit
                                .events
                                .push(ChangedState::Csr { index, name, value });

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
    let ast = SpikeLogs::parse_from(&raw);
    assert!(!ast.is_empty());

    let expect = SpikeLogs(vec![
        SpikeInsnCommit {
            core: 0,
            privilege: 3,
            pc: 0x800000ac,
            instruction: 0x30529073,
            events: StateCommits(vec![ChangedState::Csr {
                index: 0x305,
                name: "mtvec".to_string(),
                value: 0x8000000c,
            }]),
        },
        SpikeInsnCommit {
            core: 0,
            privilege: 3,
            pc: 4096,
            instruction: 663,
            events: StateCommits(vec![ChangedState::XReg {
                index: 5,
                value: 4096,
            }]),
        },
        SpikeInsnCommit {
            core: 0,
            privilege: 3,
            pc: 4100,
            instruction: 33719699,
            events: StateCommits(vec![ChangedState::XReg {
                index: 11,
                value: 4128,
            }]),
        },
        SpikeInsnCommit {
            core: 0,
            privilege: 3,
            pc: 4108,
            instruction: 25342595,
            events: StateCommits(vec![
                ChangedState::XReg {
                    index: 5,
                    value: 2147483700,
                },
                ChangedState::MemoryRead { address: 4120 },
            ]),
        },
        SpikeInsnCommit {
            core: 0,
            privilege: 3,
            pc: 4112,
            instruction: 163943,
            events: StateCommits(Vec::new()),
        },
        SpikeInsnCommit {
            core: 0,
            privilege: 3,
            pc: 2147483860,
            instruction: 1129507,
            events: StateCommits(vec![ChangedState::MemoryWrite {
                address: 2684354552,
                value: 2147483840,
            }]),
        },
        SpikeInsnCommit {
            core: 0,
            privilege: 3,
            pc: 2147483896,
            instruction: 24840,
            events: StateCommits(vec![
                ChangedState::FReg {
                    index: 10,
                    value: 1075838976,
                },
                ChangedState::MemoryRead {
                    address: 2147484336,
                },
            ]),
        },
    ]);
    assert_eq!(ast, expect);
}
