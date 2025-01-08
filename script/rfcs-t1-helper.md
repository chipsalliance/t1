# Summary
[summary]: #summary

This directory contains multiple shell scripts designed to assist developers
and users in easily utilizing all T1 modules across various configurations.

The concept behind these scripts is inspired by `direnv` and the Python `venv`
activation script.

Our goal is to minimize the terminal input required from developers as much as
possible.

# Motivation
[motivation]: #motivation

The T1 project supports multiple RTL configurations and various simulator
backends. We have divided the modules that comprise the entire project into
multiple build objects, such as MLIR output, System Verilog, and Rust
emulator...etc. This creates a challenge: the intersection of configurations
and module outputs becomes too extensive for developers to efficiently select
and build. Additionally, since we are using Nix as the top-level build system,
the command-line interface (CLI) input scheme can also be difficult for
community users to understand and utilize effectively.

We have already released two versions of the scripts to tackle these issues:

* **env-helper**: A fully Python `venv` inspired activation script that is
written in Bash. It caches essential information, such as IP name and
configuration names, as environment variables, allowing developers to avoid
repetitive input.
* **t1-helper**: A naive CLI wrapper written in Scala, assembled and
linked by GraalVM.

However, both scripts have their own limitations.

The **env-helper**, being written in Bash, integrates with Nix using
`shellHook`. Unfortunately, this reliance on Bash can be a barrier for users
who do not use it as their login shell. We aim to enhance the developer
experience without imposing Bash as a requirement, so this script
has been deprecated since most of our developers prefer using `fish` shell.

While we can learn from Python’s *venv*, which offers an alternative
`activate.fish` for `fish` users, this approach complicates maintenance and can
lead to inconsistent behavior across different scripts.

The **t1-helper** was initially designed as an executable wrapper but has
undergone modifications to support additional features over time. However, its
original design does not accommodate these features well, resulting in a
chaotic codebase and a subpar user experience. Furthermore, because it is
written in Scala and managed by Mill, the script is slow to maintain:
developers often face a compilation delay of 15-20 seconds before they can see
the effects of their changes.

Even more concerning is our introduction of GraalVM to compile the Scala code
into native code for generating native ELF files. As a result, anyone
attempting to use the T1 emulator must endure a compilation wait time of up to
two minutes for a script wrapper.

After two years of development, our requirement to simplify development has stabilized and can be concluded into the following parts:

* Provide a straightforward user interface to obtain build targets.
* Cache user input as much as possible.
* Ensure cross-shell support.
* Use a readable and easy-to-learn scripting language for writing the script.

# Detailed design
[design]: #detailed-design

## Jobs

We have the following jobs that needs to be handled by this script:

1. **An external project fetcher**. It will read the commit ID that Nix is using and
automatically use git to clone and checkout the project.
2. **A Nix derivation manager**. It will provide query and build functionalities that
help user search and build the build targets across multiple configuration.
3. **An emulator exeutor**. It will build and run test cases and collect
emulator output to specific place.
4. **A GitHub CI dispatcher**. It will resolve all the test targets and provide
necessary information for GitHub Runner to run.

To have a clear and concise binary name, the script will be shipped as a single binary with name `t1`.
All the jobs are provided as subcommands like `t1 submodules` or `t1 run-emu`.

### External Project Fetcher

Most of the time T1 only needs a read-only source of the external projects.
However, it becomes inconvenient when developers have some debug usage.

This external project fetcher will provide the following features to help improve the external project management.

* `t1 submodules sync`: read commit ID specified in nvfetcher and use *git* to clone **all** project and checkout to that SHA ID.
* `t1 submodules repo <dir> [git command options]`: a git wrapper that do git operation to specific repository without changing dir.
* `t1 submodules clean`: remove all submodules at local disk.
* `t1 submodules list`: list all manually checkout repositories.

### Nix Derivation Manager

We have a enormous T1 project specific build artifacts managed by Nix.
The general Nix CLI tools is enough for developers that familiar with Nix.
However it is not straightforward and also confusing for users who never touch Nix before.

The Nix Derivation Manager act as an abstract layer that hide the query scheme at background and provides
an simple interface for user to query and build Nix derivation.

* `t1 list-configs`: list all the available configuration and their metadata
* `t1 search-tests <pattern>`: search through all the available test cases using the user speicified PCRE compatible regexp pattern.
* `t1 build`: build derivation and links the result to an unique path.

### Emulator Exeuctor

Run an simulation requires resolving RTL, emulator libraries, test case ELF...etc.
The terminal input is tedious for developer to start a simulation.

The Emulator Exeutor hide all the details in the scripts and do as what developer wants.
All the `(cachable)` label marks that the action cache the current information under `$XDG_CACHE_DIRS`.
This allow developers run multiple cases without input same option each time.

* `t1 sim [options] <test case name>`: run the emulator and store metadata and output to an unique path
  - `-t/--top`: (cachable) specify the T1 top name, for example: `t1rocketemu`, `t1emu`
  - `-e/--emu`: (cachable) specify the emulator library, for example: `vcs`, `verilator`
  - `-c/--config`: (cachable) specify the RTL config, for example: `benchmark`, `blastoise`
  - `<test case name>`: test case name to get the ELF for emulator to run, for example: `mlir.hello`, `rvv_bench.byteswap`
* `t1 sim-check [options]`: run the difftest for previous simulation. Start a new simulation if no simulation outputs was found.

### GitHub CI Dispatcher

It is hard to maintain bash script in YAML for our CI test workflow.
We need to build multiple targets to provide a parallel strategy for GitHub to run.

The *GitHub CI Dispatcher* will generate *GitHub Matrix*, dispatch multiple
emulator to run test cases, collect all results and transform all results to
readable reports.

* `t1 ci gen-test-plan`: generate test plan for later matrix generation
* `t1 ci gen-matrix`: generate *GitHub Matrix*.
* `t1 ci run-tests [options]`: dispatch emulator to run multiple test cases.
* `t1 ci report [options]`: collect cycle update and urg report

## Language

* Python: Most of the developers should be familiar with Python so it is not
hard to find a developer to maintain the script. However writing Python in
readable and maintainable style is hard. We don't have a compiler to help us
limit the developers to write great Python code. But at least we have
[ruff](https://docs.astral.sh/ruff/linter/#rule-selection) to try handle that.
* Ruby: A great language that have FP-like expression, with fast and reliable
JIT interpreter. It doesn't comes with a all-in-one stdlib, but as we are mainly
using Nix, this is not a big problem.
* [Bun](https://bun.sh/): bun have built-in TypeScript support, so we can have
full typeclass support in a scripting language. Also it is really fast, even
faster than Deno.
* Bash: We can use bash to write the script like what *pacman* do for us.
And with shellcheck support, the writing style can also be enforced.
But we might lost all our sanity when we have to deal with data structure
or math calculation.

# Drawbacks
[drawbacks]: #drawbacks

We have an nix derivation that wraps the emulator execution and collect it's
output. This is doing the same job of the script wrapper which introduced
possible inconsistency between our script and the derivaiton build script.
