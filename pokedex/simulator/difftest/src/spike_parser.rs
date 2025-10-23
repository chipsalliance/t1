use std::iter::Peekable;
use std::num::ParseIntError;
use std::slice::Iter;

#[derive(Debug, PartialEq, Eq, Clone)]
pub enum Token<'a> {
    CoreLiteral,
    CoreId(&'a str),
    MemLiteral,
    NumberLiteral(&'a str),
    // Instruction is wrapped with parenthese '(', ')'
    Instruction(&'a str),
    // Hex number is of unknown bit width which might exceed 64-bit, parse it later
    Hexadecimal(&'a str),
    XReg(&'a str),
    FReg(&'a str),
    VReg(&'a str),
    Sew(&'a str),
    Lmul(&'a str),
    Vl(&'a str),
    Csr(&'a str),
    Unknown {
        line_num: usize,
        raw_line: &'a str,
        raw_token: &'a str,
    },
}

fn is_digit(s: &str) -> bool {
    !s.is_empty() && s.chars().all(|c| c.is_ascii_digit())
}

fn is_hex(s: &str) -> bool {
    s.starts_with("0x") && !s[2..].is_empty() && s[2..].chars().all(|c| c.is_ascii_hexdigit())
}

/// Tokenizes a raw string from a Spike commit log.
pub fn tokenize_spike_log(raw_str: &str) -> Vec<Vec<Token>> {
    raw_str
        .lines()
        .enumerate()
        .map(|(line_num, raw_line)| {
            raw_line
                .split_whitespace()
                .map(move |raw_token| match raw_token {
                    "core" => Token::CoreLiteral,
                    "mem" => Token::MemLiteral,
                    tok if tok.ends_with(":") => {
                        if let Some(id) = tok.strip_suffix(':') {
                            if is_digit(id) {
                                return Token::CoreId(id);
                            }
                        }

                        Token::Unknown {
                            line_num,
                            raw_line,
                            raw_token,
                        }
                    }
                    tok if tok.starts_with("(0x") && tok.ends_with(')') => {
                        if let Some(hex_unstrip) = tok.strip_prefix("(0x") {
                            if is_hex(&tok[1..tok.len() - 1]) {
                                return Token::Instruction(&hex_unstrip[..hex_unstrip.len() - 1]);
                            }
                        }
                        Token::Unknown {
                            line_num,
                            raw_line,
                            raw_token,
                        }
                    }
                    tok if is_hex(tok) => Token::Hexadecimal(&tok[2..]),
                    tok if is_digit(tok) => Token::NumberLiteral(tok),
                    tok if tok.len() > 1 => match &tok[0..1] {
                        "x" if is_digit(&tok[1..]) => Token::XReg(&tok[1..]),
                        "f" if is_digit(&tok[1..]) => Token::FReg(&tok[1..]),
                        "v" if is_digit(&tok[1..]) => Token::VReg(&tok[1..]),
                        "e" if is_digit(&tok[1..]) => Token::Sew(&tok[1..]),
                        "l" if is_digit(&tok[1..]) => Token::Vl(&tok[1..]),
                        "m" => Token::Lmul(tok), // Lmul is special, can be 'm' or 'mf'
                        "c" if tok.contains('_')
                            && tok
                                .chars()
                                .skip(1)
                                .take_while(|c| *c != '_')
                                .all(|c| c.is_ascii_digit()) =>
                        {
                            Token::Csr(tok)
                        }

                        _ => Token::Unknown {
                            line_num,
                            raw_line,
                            raw_token,
                        },
                    },
                    _ => Token::Unknown {
                        line_num,
                        raw_line,
                        raw_token,
                    },
                })
                .collect()
        })
        .collect()
}

#[derive(Debug, PartialEq, Eq)]
pub enum Modification {
    WriteXReg {
        rd: u8,
        bits: u64,
    },
    WriteFReg {
        rd: u8,
        bits: u64,
    },
    WriteCSR {
        rd: u32,
        name: String,
        bits: u64,
    },
    WriteVecCtx {
        sew: u32,
        lmul: u32,
        is_flmul: bool,
        vl: u32,
    },
    WriteVReg {
        idx: u8,
        bytes: Vec<u8>,
    },
    Load {
        addr: u64,
    },
    Store {
        addr: u64,
        bits: u64,
    },
}

#[derive(Debug, PartialEq, Eq)]
pub struct Commit {
    pub core_id: u8,
    pub privilege: u8,
    pub pc: u64,
    pub instruction: u32,
    pub state_changes: Vec<Modification>,
}

impl crate::replay::IsInsnCommit for Commit {
    fn get_pc(&self) -> u32 {
        self.pc as u32
    }

    fn write_cpu_state(
        &self,
        state: &mut crate::replay::CpuState,
    ) -> crate::replay::StateCheckType {
        state.pc = self.pc as u32;

        let mut check_ty = crate::replay::StateCheckType::default();

        let mut has_frf_write = false;
        let mut has_csr_write = false;
        self.state_changes.iter().for_each(|write| {
            use crate::replay::CsrCheckType;
            use Modification::*;

            match write {
                WriteXReg { rd, bits } => {
                    if state.write_gpr((*rd) as usize, (*bits) as u32).is_some() {
                        check_ty.gpr_rd = Some((*rd) as usize);
                    }
                }
                WriteFReg { rd, bits } => {
                    if state.write_fpr((*rd) as usize, (*bits) as u32).is_some() {
                        check_ty.fpr_rd = Some((*rd) as usize);
                    }
                    has_frf_write = true;
                }
                WriteCSR { rd, name, bits } => {
                    if state
                        .write_csr(name, (*rd) as u16, (*bits) as u32)
                        .is_some()
                    {
                        check_ty.csr_mask = CsrCheckType::AllCsr;
                    }
                    has_csr_write = true;
                }
                _ => (),
            }

            if has_frf_write && has_csr_write {
                check_ty.csr_mask = CsrCheckType::FpCsrOnly;
            }
        });

        check_ty
    }
}

#[derive(Debug, thiserror::Error)]
pub enum ParseError<'a> {
    #[error("Unexpected token at position {pos}: expected {expected}, found {found:?}")]
    UnexpectedToken {
        pos: usize,
        expected: &'static str,
        found: Option<Token<'a>>,
    },
    #[error("Invalid value at position {pos} for {kind}: '{value}'. Reason: {reason}")]
    InvalidValue {
        pos: usize,
        kind: &'static str,
        value: String,
        reason: String,
    },
    #[error("An unknown token was found on line {line_num}: '{raw_token}' in line '{raw_line}'")]
    UnknownToken {
        line_num: usize,
        raw_line: &'a str,
        raw_token: &'a str,
    },
}

// Helper struct to manage parsing state
struct Parser<'a, 'b> {
    tokens: &'b mut Peekable<Iter<'a, Token<'a>>>,
    cursor: usize,
}

impl<'a, 'b> Parser<'a, 'b> {
    fn new(tokens: &'b mut Peekable<Iter<'a, Token<'a>>>) -> Self {
        Self { tokens, cursor: 0 }
    }

    // Peek at the next token without consuming it
    fn peek(&mut self) -> Option<&'a Token<'a>> {
        self.tokens.peek().copied()
    }

    // Consume the next token and advance the cursor
    fn consume(&mut self) -> Option<&'a Token<'a>> {
        self.cursor += 1;
        self.tokens.next()
    }

    // Expect a specific kind of token, returning an error if it's not found
    fn expect<F, T>(&mut self, expected_str: &'static str, f: F) -> Result<T, ParseError<'a>>
    where
        F: FnOnce(&'a Token<'a>) -> Option<T>,
    {
        let token = self.peek().ok_or(ParseError::UnexpectedToken {
            pos: self.cursor,
            expected: expected_str,
            found: None,
        })?;

        if let Some(val) = f(token) {
            self.consume(); // Only consume if it matches
            Ok(val)
        } else {
            Err(ParseError::UnexpectedToken {
                pos: self.cursor,
                expected: expected_str,
                found: Some(token.clone()),
            })
        }
    }
}

/// Parses a single line of tokens into a Commit struct.
pub fn parse_single_commit<'a>(tokens: &'a [Token<'_>]) -> Result<Commit, ParseError<'a>> {
    // Check for any Unknown tokens before starting.
    for token in tokens {
        if let Token::Unknown {
            line_num,
            raw_line,
            raw_token,
        } = token
        {
            return Err(ParseError::UnknownToken {
                line_num: *line_num,
                raw_line,
                raw_token,
            });
        }
    }

    let mut token_iter = tokens.iter().peekable();
    let mut p = Parser::new(&mut token_iter);

    p.expect("core literal", |t| {
        matches!(t, Token::CoreLiteral).then_some(())
    })?;
    let core_id_str = p.expect("core id", |t| match t {
        Token::CoreId(s) => Some(*s),
        _ => None,
    })?;
    let core_id = core_id_str
        .parse()
        .map_err(|e: ParseIntError| ParseError::InvalidValue {
            pos: 1,
            kind: "core id",
            value: core_id_str.to_string(),
            reason: e.to_string(),
        })?;

    let priv_str = p.expect("privilege level", |t| match t {
        Token::NumberLiteral(s) => Some(*s),
        _ => None,
    })?;
    let privilege = priv_str
        .parse()
        .map_err(|e: ParseIntError| ParseError::InvalidValue {
            pos: 2,
            kind: "privilege",
            value: priv_str.to_string(),
            reason: e.to_string(),
        })?;

    let pc_str = p.expect("pc", |t| match t {
        Token::Hexadecimal(s) => Some(*s),
        _ => None,
    })?;
    let pc = u64::from_str_radix(pc_str, 16).map_err(|e| ParseError::InvalidValue {
        pos: 3,
        kind: "pc",
        value: format!("0x{}", pc_str),
        reason: e.to_string(),
    })?;

    let insn_str = p.expect("instruction", |t| match t {
        Token::Instruction(s) => Some(*s),
        _ => None,
    })?;
    let instruction = u32::from_str_radix(insn_str, 16).map_err(|e| ParseError::InvalidValue {
        pos: 4,
        kind: "instruction",
        value: format!("(0x{})", insn_str),
        reason: e.to_string(),
    })?;

    let mut state_changes = Vec::new();

    while let Some(token) = p.peek() {
        let modification = match token {
            Token::XReg(rd_str) => {
                let rd = rd_str
                    .parse()
                    .map_err(|e: ParseIntError| ParseError::InvalidValue {
                        pos: p.cursor,
                        kind: "X reg index",
                        value: rd_str.to_string(),
                        reason: e.to_string(),
                    })?;
                p.consume(); // Consume XReg

                let bits_str = p.expect("hex value for X register", |t| match t {
                    Token::Hexadecimal(s) => Some(s),
                    _ => None,
                })?;
                let bits =
                    u64::from_str_radix(bits_str, 16).map_err(|e| ParseError::InvalidValue {
                        pos: p.cursor,
                        kind: "X reg value",
                        value: bits_str.to_string(),
                        reason: e.to_string(),
                    })?;
                Modification::WriteXReg { rd, bits }
            }
            Token::FReg(rd_str) => {
                let rd = rd_str
                    .parse()
                    .map_err(|e: ParseIntError| ParseError::InvalidValue {
                        pos: p.cursor,
                        kind: "F reg index",
                        value: rd_str.to_string(),
                        reason: e.to_string(),
                    })?;
                p.consume(); // Consume FReg

                let bits_str = p.expect("hex value for F register", |t| match t {
                    Token::Hexadecimal(s) => Some(s),
                    _ => None,
                })?;
                let bits =
                    u64::from_str_radix(bits_str, 16).map_err(|e| ParseError::InvalidValue {
                        pos: p.cursor,
                        kind: "F reg value",
                        value: bits_str.to_string(),
                        reason: e.to_string(),
                    })?;
                Modification::WriteFReg { rd, bits }
            }
            Token::Csr(csr_str) => {
                let mut parts = csr_str.splitn(2, '_');
                let rd_str = &parts.next().unwrap()[1..];
                let rd = rd_str
                    .parse()
                    .map_err(|e: ParseIntError| ParseError::InvalidValue {
                        pos: p.cursor,
                        kind: "csr index",
                        value: rd_str.to_string(),
                        reason: e.to_string(),
                    })?;
                let name = parts.next().unwrap_or("").to_string();
                p.consume(); // Consume Csr

                let bits_str = p.expect("hex value for csr", |t| match t {
                    Token::Hexadecimal(s) => Some(s),
                    _ => None,
                })?;
                let bits =
                    u64::from_str_radix(bits_str, 16).map_err(|e| ParseError::InvalidValue {
                        pos: p.cursor,
                        kind: "csr value",
                        value: bits_str.to_string(),
                        reason: e.to_string(),
                    })?;

                Modification::WriteCSR { rd, name, bits }
            }
            Token::MemLiteral => {
                p.consume(); // Consume MemLiteral

                let addr_str = p.expect("memory address", |t| match t {
                    Token::Hexadecimal(s) => Some(s),
                    _ => None,
                })?;
                let addr =
                    u64::from_str_radix(addr_str, 16).map_err(|e| ParseError::InvalidValue {
                        pos: p.cursor,
                        kind: "mem address",
                        value: addr_str.to_string(),
                        reason: e.to_string(),
                    })?;

                if let Some(Token::Hexadecimal(bits_str)) = p.peek() {
                    p.consume(); // Consume store data if it exists
                    let bits = u64::from_str_radix(bits_str, 16).map_err(|e| {
                        ParseError::InvalidValue {
                            pos: p.cursor,
                            kind: "store value",
                            value: bits_str.to_string(),
                            reason: e.to_string(),
                        }
                    })?;
                    Modification::Store { addr, bits }
                } else {
                    Modification::Load { addr }
                }
            }
            Token::Sew(sew_str) => {
                let sew = sew_str
                    .parse()
                    .map_err(|e: ParseIntError| ParseError::InvalidValue {
                        pos: p.cursor,
                        kind: "sew",
                        value: sew_str.to_string(),
                        reason: e.to_string(),
                    })?;
                p.consume(); // Consume Sew

                let lmul_str = p.expect("lmul", |t| match t {
                    Token::Lmul(s) => Some(*s),
                    _ => None,
                })?;
                let (is_flmul, lmul) = if let Some(digits) = lmul_str.strip_prefix("mf") {
                    (
                        true,
                        digits
                            .parse::<u32>()
                            .map_err(|e| ParseError::InvalidValue {
                                pos: p.cursor,
                                kind: "flmul",
                                value: lmul_str.to_string(),
                                reason: e.to_string(),
                            })?,
                    )
                } else {
                    (
                        false,
                        lmul_str[1..]
                            .parse::<u32>()
                            .map_err(|e| ParseError::InvalidValue {
                                pos: p.cursor,
                                kind: "lmul",
                                value: lmul_str.to_string(),
                                reason: e.to_string(),
                            })?,
                    )
                };

                let vl_str = p.expect("vl", |t| match t {
                    Token::Vl(s) => Some(*s),
                    _ => None,
                })?;
                let vl = vl_str
                    .parse()
                    .map_err(|e: ParseIntError| ParseError::InvalidValue {
                        pos: p.cursor,
                        kind: "vl",
                        value: vl_str.to_string(),
                        reason: e.to_string(),
                    })?;

                Modification::WriteVecCtx {
                    sew,
                    lmul,
                    is_flmul,
                    vl,
                }
            }
            Token::VReg(vreg_str) => {
                let idx =
                    vreg_str
                        .parse()
                        .map_err(|e: ParseIntError| ParseError::InvalidValue {
                            pos: p.cursor,
                            kind: "vector RF index",
                            value: vreg_str.to_string(),
                            reason: e.to_string(),
                        })?;
                p.consume();

                let hex_string = p.expect("hex value for vector register", |t| match t {
                    Token::Hexadecimal(s) => Some(s),
                    _ => None,
                })?;
                let bytes_seq: Vec<char> = hex_string.chars().collect();
                if bytes_seq.len() % 8 != 0 {
                    return Err(ParseError::InvalidValue {
                        pos: p.cursor,
                        kind: "vrf value",
                        value: hex_string.to_string(),
                        reason: "unaligned hex value".to_string(),
                    });
                }
                let bytes = bytes_seq
                    .chunks(2)
                    .rev()
                    .map(|byte_char| {
                        let byte: String = byte_char.iter().collect();
                        u8::from_str_radix(&byte, 16).unwrap()
                    })
                    .collect();

                Modification::WriteVReg { idx, bytes }
            }
            _ => break, // Break if the next token doesn't start a modification
        };
        state_changes.push(modification);
    }

    Ok(Commit {
        core_id,
        privilege,
        pc,
        instruction,
        state_changes,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn get_test_log() -> String {
        let mut d = std::path::PathBuf::from(env!("CARGO_MANIFEST_DIR"));
        d.push("assets/example.spike.log");
        std::fs::read_to_string(d).unwrap()
    }

    #[test]
    fn test_tokenizer() {
        let raw_str = get_test_log();
        let tok_seq = tokenize_spike_log(&raw_str);

        assert_eq!(tok_seq[0][1], Token::CoreId("0"));
        assert_eq!(tok_seq[0][3], Token::Hexadecimal("800000ac"));
        assert_eq!(tok_seq[0][4], Token::Instruction("30529073"));
        println!("{:#?}", tok_seq);
    }

    #[test]
    fn test_parse() {
        let raw_str = get_test_log();
        let tok_seq = tokenize_spike_log(&raw_str);

        let all_commits: Vec<Commit> = tok_seq
            .iter()
            .map(|line| parse_single_commit(line).unwrap())
            .collect();

        use Modification::*;
        assert_eq!(
            all_commits,
            [
                Commit {
                    core_id: 0,
                    privilege: 3,
                    pc: 2147483820,
                    instruction: 810717299,
                    state_changes: vec![WriteCSR {
                        rd: 773,
                        name: "mtvec".to_string(),
                        bits: 2147483660,
                    },],
                },
                Commit {
                    core_id: 0,
                    privilege: 3,
                    pc: 4096,
                    instruction: 663,
                    state_changes: vec![WriteXReg { rd: 5, bits: 4096 },],
                },
                Commit {
                    core_id: 0,
                    privilege: 3,
                    pc: 4100,
                    instruction: 33719699,
                    state_changes: vec![WriteXReg { rd: 11, bits: 4128 },],
                },
                Commit {
                    core_id: 0,
                    privilege: 3,
                    pc: 4108,
                    instruction: 25342595,
                    state_changes: vec![
                        WriteXReg {
                            rd: 5,
                            bits: 2147483700,
                        },
                        Load { addr: 4120 },
                    ],
                },
                Commit {
                    core_id: 0,
                    privilege: 3,
                    pc: 4112,
                    instruction: 163943,
                    state_changes: vec![],
                },
                Commit {
                    core_id: 0,
                    privilege: 3,
                    pc: 2147483860,
                    instruction: 1129507,
                    state_changes: vec![Store {
                        addr: 2684354552,
                        bits: 2147483840,
                    },],
                },
                Commit {
                    core_id: 0,
                    privilege: 3,
                    pc: 2147483896,
                    instruction: 24840,
                    state_changes: vec![
                        WriteFReg {
                            rd: 10,
                            bits: 1075838976,
                        },
                        Load { addr: 2147484336 },
                    ],
                },
                Commit {
                    core_id: 0,
                    privilege: 3,
                    pc: 2147483892,
                    instruction: 1577070679,
                    state_changes: vec![
                        WriteVecCtx {
                            sew: 8,
                            lmul: 8,
                            is_flmul: false,
                            vl: 256,
                        },
                        WriteVReg {
                            idx: 0,
                            bytes: vec![
                                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                            ],
                        },
                        WriteVReg {
                            idx: 1,
                            bytes: vec![
                                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                            ],
                        },
                        WriteVReg {
                            idx: 2,
                            bytes: vec![
                                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                            ],
                        },
                        WriteVReg {
                            idx: 3,
                            bytes: vec![
                                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                            ],
                        },
                        WriteVReg {
                            idx: 4,
                            bytes: vec![
                                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                            ],
                        },
                        WriteVReg {
                            idx: 5,
                            bytes: vec![
                                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                            ],
                        },
                        WriteVReg {
                            idx: 6,
                            bytes: vec![
                                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                            ],
                        },
                        WriteVReg {
                            idx: 7,
                            bytes: vec![
                                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                            ],
                        },
                        WriteCSR {
                            rd: 8,
                            name: "vstart".to_string(),
                            bits: 0,
                        },
                    ],
                },
                Commit {
                    core_id: 0,
                    privilege: 3,
                    pc: 2147483904,
                    instruction: 1577073751,
                    state_changes: vec![
                        WriteVecCtx {
                            sew: 8,
                            lmul: 8,
                            is_flmul: false,
                            vl: 256,
                        },
                        WriteCSR {
                            rd: 8,
                            name: "vstart".to_string(),
                            bits: 0,
                        },
                        WriteVReg {
                            idx: 24,
                            bytes: vec![
                                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                            ],
                        },
                        WriteVReg {
                            idx: 25,
                            bytes: vec![
                                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                            ],
                        },
                        WriteVReg {
                            idx: 26,
                            bytes: vec![
                                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                            ],
                        },
                        WriteVReg {
                            idx: 27,
                            bytes: vec![
                                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                            ],
                        },
                        WriteVReg {
                            idx: 28,
                            bytes: vec![
                                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                            ],
                        },
                        WriteVReg {
                            idx: 29,
                            bytes: vec![
                                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                            ],
                        },
                        WriteVReg {
                            idx: 30,
                            bytes: vec![
                                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                            ],
                        },
                        WriteVReg {
                            idx: 31,
                            bytes: vec![
                                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                            ],
                        },
                    ],
                },
                Commit {
                    core_id: 0,
                    privilege: 3,
                    pc: 2147483928,
                    instruction: 33906695,
                    state_changes: vec![
                        WriteVecCtx {
                            sew: 32,
                            lmul: 1,
                            is_flmul: false,
                            vl: 8,
                        },
                        WriteVReg {
                            idx: 0,
                            bytes: vec![
                                204, 140, 103, 173, 98, 212, 179, 177, 238, 48, 2, 163, 122, 81, 3,
                                95, 172, 239, 101, 35, 237, 39, 207, 125, 39, 53, 186, 0, 248, 80,
                                103, 10
                            ],
                        },
                        WriteCSR {
                            rd: 8,
                            name: "vstart".to_string(),
                            bits: 0,
                        },
                        Load { addr: 2147556960 },
                        Load { addr: 2147556964 },
                        Load { addr: 2147556968 },
                        Load { addr: 2147556972 },
                        Load { addr: 2147556976 },
                        Load { addr: 2147556980 },
                        Load { addr: 2147556984 },
                        Load { addr: 2147556988 },
                    ],
                },
                Commit {
                    core_id: 0,
                    privilege: 3,
                    pc: 2147483982,
                    instruction: 34618711,
                    state_changes: vec![
                        WriteVecCtx {
                            sew: 8,
                            lmul: 4,
                            is_flmul: true,
                            vl: 0,
                        },
                        WriteCSR {
                            rd: 8,
                            name: "vstart".to_string(),
                            bits: 0,
                        },
                    ],
                }
            ]
        );
    }
}
