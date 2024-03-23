## Current IP configs

| Config name   | Short summary                                                        |
|---------------|----------------------------------------------------------------------|
| **Blastoise** | `DLEN256 VLEN512;   FP; VRF p0rw,p1rw bank1; LSU bank8  beatbyte 8`  |

## Address Range

| Range | Usage                       |
|-------|-----------------------------|
| 0-1G  | Scalar bank                 |
| 1G-3G | DDR Bank (512M/bank)        |
| 3G+   | SRAM bank 256K/bank, 8banks |


## How to use the Docker image

```bash
# Confirm that docker is working properly
docker info
# Load the image into docker registry
gzip -d docker-image.tar.gz
docker load -i docker-image.tar
# Run it as a daemon
docker run --name t1 -it -d --rm -p 12222:22 chipsalliance/t1:latest
# There are two ways to access the shell:
# 1: using SSH
ssh root@localhost -p 12222
# 2: using docker exec shell
docker exec -it t1 /bin/bash
```

> There is also a script to help you retrieve public key from GitHub.
> For example, if you want to give Torvalds and Chris Lattner permission to this image, run:
> ```bash
> docker exec -it t1 /bin/add-gh-user-key "torvalds lattner"
> ```

## What is inside

- IP emulator: `/bin/emulator`
- Soc simulator: `/bin/soc-simulator`
- Simple workload demo: `/workspace/demo`
- RISCV 32 GCC ToolChain

## How to build and run demo on SOC simulator

```bash
# build demo
cd /workspace/demo
make

# Run demo
soc-simulator -cycle 10000 -init ./start.bin -trace trace.fst
```

## How to run workload using IP emulator

```bash
# There are three cases under the /workspace/cases directory
<build elf>

# Choose one of the case to run
ip-emulator output.elf
```
