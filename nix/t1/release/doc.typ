#import "@preview/codly:0.2.0": *
#show: codly-init.with()
#codly(languages: (
  bash: (name: "Bash", icon: none, color: rgb("#CE412B")),
))

= T1 Docker Image Manual

== Released IP configs

#let table-json(config) = {
  let name = config.name
  let param = config.parameter
  let floatSupport = if param.extensions.first() == "Zve32f" [ True ] else [ False ]
  let VRFRamType = param.vrfRamType.split(".").last()
  let VRF = [#param.vrfBankSize Bank, #VRFRamType]
  let lsuBankCnt = param.lsuBankParameters.len()
  let beatByteCnt = param.lsuBankParameters.first().beatbyte
  table(
    columns: 6,
    [*Config Name*], [*DLEN*],      [*VLEN*],      [*Float support*], [*VRF*], [*LSU*],
    [*#name*],       [#param.dLen], [#param.vLen], [#floatSupport],   [#VRF],  [#lsuBankCnt bank, #beatByteCnt beatbyte],
  )
}

#table-json(json("./config.json"))

== Address Range

#table(
  columns: 3,
  [*Range*],  [*Usage*],                      [*Address Range*],
  [0-1G],     [Scalar Bank],                  [0x20000000],
  [1-3G],     [DDR Bank (512M/bank)],         [0x40000000],
  [3G-3G+2M], [SRAM Bank (256K/bank 8Banks)], [0xc0000000]
)

Scalar core cannot access Vector DDR/SRAM, for, users need to access corresponding memory banks via vector load store instructions.

== How to use the Docker image

```bash
# Load the image into docker registry
docker pull ghcr.io/chipsalliance/t1:latest
# Run the t1/release:latest image with command /bin/bash, name the running container with name "t1", with [i]nterative shell and a working [t]ty.
# The directory `/workspace` will be bind mount on the current directory. The container will be automatically [r]e[m]ove at exit.
docker run --name t1 -it -v $PWD:/workspace --rm t1/release:latest /bin/bash
```

> It is recommended to build ELF outside of the docker image and bind mount the ELF location into the image.

== What is inside

+ IP emulator: `/bin/ip-emulator`
+ Softmax & Linear Normalization & Matmul test cases: `/workspace/cases`

== How to run some workload using IP emulator

```bash
# There are three cases under the /workspace/cases directory
ls /workspace/cases

# Choose one of the case to run
ip-emulator --case cases/intrinsic-matmul/bin/intrinsic.matmul.elf

# Get waveform trace file
ip-emulator --trace --case ...
```
