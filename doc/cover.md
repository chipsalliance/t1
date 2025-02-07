# Coverage

## Introduction

The collection of T1 coverage involves several stages:

1. Describe coverpoints using JSON and write to a file. A simple JSON format is as follows:
tests/mlir/hello.json:
```json
{
  "assert": [
    {
      "name": "vmv_v_i",
      "description": "single instruction vmv.v.i"
    }
  ],
  "tree": [],
  "module": []
}
```
The `assert`, `tree`, and `module` fields describe three types of coverage collection methods, each containing multiple coverpoints. The `name` is the coverpoint's name, and the `description` is its description.

2. Convert the file into an assertion hierarchical file acceptable by VCS.
This step is completed in the T1 tests' Nix script. When writing tests, use the Nix script to call `jq` to generate the corresponding assertion hierarchical file. If no file is specified, it defaults to not covering any points. For example, in T1's tests/mlir/default.nix:
```shell
if [ -f ${caseName}.json ]; then
  ${jq}/bin/jq -r '[.assert[] | "+assert " + .name] + [.tree[] | "+tree " + .name] + [.module[] | "+module " + .name] | .[]' \
      ${caseName}.json > $pname.cover
else 
  echo "-assert *" > $pname.cover
fi
```

You can see the generated file when running the test:
```shell
nix build .#t1.blastoise.t1rocketemu.cases.mlir.hello
```

The generated assertion hierarchical file format is as follows:
result/mlir.hello.cover:
```
+assert vmv_v_i
```

3. Specify the types of coverage the simulator can generate using the `cm` parameter when compiling the simulator with VCS.

4. Generate coverage and output the `cm.vdb` file using the `cm` parameter and hierarchical file when running the simulator with VCS.
These parameters are specified in the `coverType` in nix/t1/run/run-vcs-emu.nix:
```shell
coverType = "line+cond+fsm+tgl+branch+assert";

"-cm"
"${coverType}"
"-assert"
"hier=${testCase}/${testCase.pname}.cover"
```

5. Generate a coverage report using URG from the `cm.vdb` file.
nix/t1/run/default.nix:
```shell
${vcs-emu.snps-fhs-env}/bin/snps-fhs-env -c "urg -dir $emuOutput/*/cm.vdb -format text -metric line+cond+fsm+tgl+branch+assert -show summary"
```

## Adding New Coverpoints

From the introduction, adding new coverpoints involves adding the corresponding coverpoints in the JSON file and generating the corresponding assertion hierarchical file in the Nix script. For example, to add a new `assert`:

1. Add a new `assert` in the JSON file:
tests/mlir/hello.json:
```json
{
  "assert": [
    {
      "name": "vmv_v_i",
      "description": "single instruction vmv.v.i"
    },
    {
      "name": "vmv_v_v",
      "description": "single instruction vmv.v.v"
    }
  ],
  "tree": [],
  "module": []
}
```

2. Generate the corresponding assertion hierarchical file in the Nix script:
tests/mlir/default.nix:
```shell
if [ -f ${caseName}.json ]; then
  ${jq}/bin/jq -r '[.assert[] | "+assert " + .name] + [.tree[] | "+tree " + .name] + [.module[] | "+module " + .name] | .[]' \
      ${caseName}.json > $pname.cover
else 
  echo "-assert *" > $pname.cover
fi
```

3. Run the coverage for a single test case:
```shell
nix build .#t1.blastoise.t1rocketemu.run.mlir.hello.vcs-emu-cover-full --impure
```
Then view the coverage report:
```shell
cat ./result/urgReport/tests.txt
```

4. Get the coverage for all test cases (ensure you are on the correct branch):
```shell
nix build .#t1.blastoise.t1rocketemu.run._vcsEmuResult --impure
urg -dir ./result/*/cm.vdb -full64 -dbname merged -show summary -parallel
```

5. View the coverage using Verdi:
```shell
verdi -cov -covdir merged.vdb
```

6. To view the coverage in HTML/text format, use URG:
```shell
urg -dir ./result/*/cm.vdb -full64 -format text -show summary -parallel
cat ./urgReport/tests.txt
```