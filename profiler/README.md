# Usage

## Prepare Prof Input Vcd

This step is integrated into nix build flow.

e.g., the following command will generate prof vcd for case 'codegen.vadd_vv' to directory 'result-prof-vcd'
```
nix build --impure .#t1.blastoise.t1rocketemu.run.codegen.vadd_vv.vcs-prof-vcd -o result-prof-vcd
```

NOTE: only 't1rocketemu' is supported

## Run profiler

```
nix run .#t1.profiler <INPUT_VCD> <OUTPUT_DIR>
```

e.g. run `nix run .#t1.profiler result-prof-vcd/*.vcd prof-result`,
the profiler will generate various analysis results in 'prof-result' directory.

### Disassemble Support

The profiler depends on `spike-dasm` to do disassemble and
you need to set path of 'spike-dasm' to environment variable `SPIKE_DASM`

```
export SPIKE_DASM=$(nix build --print-out-paths .#spike)/bin/spike-dasm
```

If `SPIKE_DASM` is not set, all disassembles in `inst_map.txt` become '<unavail>'.
