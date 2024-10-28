use std::{
    io::{BufRead as _, BufReader, Write as _},
    process::{ChildStdin, ChildStdout, Command, Stdio},
};

pub struct DisasmDefault {
    inner: Option<Disasm>,
}

impl DisasmDefault {
    pub fn new() -> DisasmDefault {
        // currently we utilize spike-dasm to disassemble RISCV instructions,
        // the path of spike-dasm is passed by env SPIKE_DASM.
        match std::env::var("SPIKE_DASM") {
            Ok(spike_dasm_path) => DisasmDefault {
                inner: Some(Disasm::new(&spike_dasm_path)),
            },
            Err(_e) => DisasmDefault { inner: None },
        }
    }

    pub fn disasm(&mut self, inst: u32) -> String {
        match &mut self.inner {
            Some(inner) => inner.disasm(inst),
            None => "<unavail>".into(),
        }
    }
}

pub struct Disasm {
    _child: std::process::Child,
    stdin: ChildStdin,
    stdout: BufReader<ChildStdout>,
}

impl Disasm {
    pub fn new(spike_dasm_path: &str) -> Self {
        let mut child = Command::new(spike_dasm_path)
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .spawn()
            .unwrap();
        let stdin = child.stdin.take().unwrap();
        let stdout = BufReader::new(child.stdout.take().unwrap());
        Disasm {
            _child: child,
            stdin,
            stdout,
        }
    }

    pub fn disasm(&mut self, inst: u32) -> String {
        let input = format!("DASM(0x{inst:08x})\n");
        self.stdin.write_all(input.as_bytes()).unwrap();
        self.stdin.flush().unwrap();

        let mut buf = String::new();
        self.stdout.read_line(&mut buf).unwrap();

        buf.trim().into()
    }
}
