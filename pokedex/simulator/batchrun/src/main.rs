use clap::Parser;
use miette::{Context, IntoDiagnostic};
use serde::Deserialize;
use std::path::{Path, PathBuf};
use std::time::Duration;
use tracing::{event, Level};
use wait_timeout::ChildExt;

pub fn run_process<I, S>(
    exec: &str,
    args: I,
    timeout: Duration,
    capture_stderr: bool,
) -> miette::Result<Option<String>>
where
    I: IntoIterator<Item = S>,
    S: AsRef<str>,
{
    let exec = which::which(exec)
        .into_diagnostic()
        .with_context(|| format!("executable '{exec}' not found"))?;

    let args = args
        .into_iter()
        .map(|s| s.as_ref().to_string())
        .collect::<Vec<_>>();
    let mut child = std::process::Command::new(&exec)
        .args(&args)
        .stderr(std::process::Stdio::piped())
        .spawn()
        .into_diagnostic()
        .with_context(|| format!("fail start {} with args {}", exec.display(), args.join(" "),))?;

    let Some(status) = child.wait_timeout(timeout).unwrap() else {
        // child hasn't exited yet
        child.kill().unwrap();
        miette::bail!(
            "timeout executing {} with args {}",
            exec.display(),
            args.join(" "),
        );
    };

    if !status.success() {
        miette::bail!(
            "executable {} with args {} return non-zero code {}",
            exec.display(),
            args.join(" "),
            status.code().unwrap()
        );
    }

    let output = child
        .wait_with_output()
        .into_diagnostic()
        .with_context(|| {
            format!(
                "fail waiting stdout output from executable {} with args {}",
                exec.display(),
                args.join(" "),
            )
        })?;

    let raw_str = String::from_utf8_lossy(&output.stderr);
    if capture_stderr {
        Ok(Some(raw_str.to_string()))
    } else {
        println!("{}", raw_str);
        Ok(None)
    }
}

#[derive(Debug, knuffel::Decode)]
struct BatchRunConfig {
    #[knuffel(child, unwrap(argument))]
    elf_path_glob: String,
    #[knuffel(child)]
    spike: ProcessConfig,
    #[knuffel(child)]
    pokedex: ProcessConfig,
    #[knuffel(child, unwrap(argument))]
    mmio_end_addr: String,
}

#[derive(Debug, knuffel::Decode)]
struct ProcessConfig {
    #[knuffel(child, unwrap(argument))]
    timeout: u64,
    #[knuffel(child, unwrap(arguments))]
    cli_args: Vec<String>,
}

#[derive(clap::Parser, Debug)]
#[command(version, about, long_about = None)]
struct CliArg {
    #[arg(short = 'c', long)]
    config_path: String,
}

#[derive(Debug, Deserialize)]
struct DiffMeta {
    is_same: bool,
    context: String,
}

fn run_pokedex(elf_name: &str, elf: &Path, cfg: &ProcessConfig) -> miette::Result<PathBuf> {
    let timeout = Duration::from_secs(cfg.timeout);

    let output_event_path = std::env::current_dir()
        .into_diagnostic()?
        .join(format!("{}-pokedex-trace-log.jsonl", elf_name));

    let mut args = cfg.cli_args.clone();
    args.extend_from_slice(&[
        "-vvv".to_string(),
        "--elf-path".to_string(),
        elf.to_string_lossy().to_string(),
        "--output-log-path".to_string(),
        output_event_path.to_string_lossy().to_string(),
    ]);

    run_process("pokedex", args, timeout, false)?;

    Ok(output_event_path)
}

fn run_spike(elf_name: &str, elf: &Path, cfg: &ProcessConfig) -> miette::Result<PathBuf> {
    let output_event_path = std::env::current_dir()
        .into_diagnostic()?
        .join(format!("{}-spike-commits.log", elf_name));

    let mut args = cfg.cli_args.clone();
    args.push(elf.to_string_lossy().to_string());

    let spike_log = run_process("spike", args, Duration::from_secs(cfg.timeout), true)?.unwrap();

    std::fs::write(&output_event_path, spike_log)
        .into_diagnostic()
        .with_context(|| "while writing spike event log to disk")?;

    Ok(output_event_path)
}

fn main() -> miette::Result<()> {
    let arg = CliArg::try_parse()
        .into_diagnostic()
        .with_context(|| "while parsing args")?;
    let raw = std::fs::read_to_string(&arg.config_path)
        .into_diagnostic()
        .with_context(|| format!("while reading {}", arg.config_path))?;
    let cfg: BatchRunConfig = knuffel::parse(&arg.config_path, &raw)?;

    tracing_subscriber::fmt()
        .pretty()
        .with_file(false)
        .with_line_number(false)
        .without_time()
        .init();

    let elfs = glob::glob(&cfg.elf_path_glob)
        .into_diagnostic()
        .with_context(|| format!("while extending path glob: {}", cfg.elf_path_glob))?;

    let mut context = Vec::new();
    for path in elfs {
        let path = path
            .into_diagnostic()
            .with_context(|| "extended unreadable path")?;

        let elf_name = path
            .file_name()
            .ok_or_else(|| miette::miette!("elf {} have no filename", path.display()))?
            .to_string_lossy();
        let pokedex_log_path = run_pokedex(&elf_name, &path, &cfg.pokedex)?
            .to_string_lossy()
            .to_string();
        let spike_log_path = run_spike(&elf_name, &path, &cfg.spike)?
            .to_string_lossy()
            .to_string();
        let difftest_result_path = std::env::current_dir()
            .into_diagnostic()?
            .join(format!("{}-difftest-result.json", elf_name))
            .to_string_lossy()
            .to_string();

        run_process(
            "difftest",
            &[
                "--spike-log-path",
                &spike_log_path,
                "--pokedex-log-path",
                &pokedex_log_path,
                "--mmio-address",
                &cfg.mmio_end_addr,
                "--output-path",
                &difftest_result_path,
            ],
            Duration::from_secs(40),
            false,
        )?;

        let s = std::fs::read(&difftest_result_path).into_diagnostic()?;
        let meta: DiffMeta = serde_json::from_slice(&s).into_diagnostic()?;
        if !meta.is_same {
            event!(Level::ERROR, "FAIL: {}", path.display());
            event!(Level::ERROR, meta.context);
            context.push(path);
        } else {
            event!(Level::INFO, "PASS: {}", path.display());
        }
    }

    if !context.is_empty() {
        let rjson = serde_json::to_string(&context).into_diagnostic()?;
        std::fs::write("batch-run-result.json", rjson)
            .into_diagnostic()
            .with_context(|| "while writing batch run result")?;
        miette::bail!("difftest fail on some tests");
    }

    Ok(())
}
