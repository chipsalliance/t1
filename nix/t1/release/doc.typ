#import "@preview/codly:0.2.0": *
#show: codly-init.with()
#codly(languages: (
  bash: (name: "Bash", icon: none, color: rgb("#CE412B")),
))

#let config = json("./config.json")

= T1 Docker Image Manual

== Released IP configs

#{
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

#show raw.where(lang: "t1-docker"): it => {
  raw(lang: "bash", it.text.replace("${config}", config.name))
}
```t1-docker
# Load the image into docker registry
docker pull ghcr.io/chipsalliance/t1-${config}:latest
# Start the bash shell in t1/release:latest image, and bind the current path to /workspace
docker run --name t1 -it -v $PWD:/workspace --rm ghcr.io/chipsalliance/t1-${config}:latest /bin/bash
```

> It is recommended to build ELF outside of the docker image and bind mount the ELF location into the image.

== What is inside

+ IP emulator: `/bin/ip-emulator`
+ IP emulator with trace functionality: `/bin/ip-emulator-trace`
+ Softmax & Linear Normalization & Matmul test cases: `/workspace/cases`

== How to run some workload using IP emulator

```bash
# There are three cases under the /workspace/cases directory
ls /workspace/cases

# Choose one of the case to run
ip-emulator --case cases/intrinsic-matmul/bin/intrinsic.matmul.elf

# Get waveform trace file
ip-emulator-trace --case cases/intrinsic-linear_normalization/bin/intrinsic.linear_normalization.elf
```