bert.S:
	@buddy-opt ./bert-with-weights.mlir \
		--linalg-bufferize \
		--convert-linalg-to-loops \
		--func-bufferize \
		--arith-bufferize \
		--tensor-bufferize \
		--finalizing-bufferize \
		--convert-vector-to-scf \
		--convert-scf-to-cf \
		--expand-strided-metadata \
		--lower-affine \
		--convert-vector-to-llvm \
		--memref-expand \
		--arith-expand \
		--convert-arith-to-llvm \
		--finalize-memref-to-llvm \
		--convert-math-to-llvm \
		--llvm-request-c-wrappers \
		--convert-func-to-llvm \
		--reconcile-unrealized-casts | \
	buddy-translate --mlir-to-llvmir | \
	buddy-llc -O3 -mtriple riscv32 -target-abi ilp32 \
		-mattr=+m,+d,+v -filetype=asm -riscv-v-vector-bits-min=128 \
		-o ./bert.S

TOOLCHAIN := $(shell nix build '.#rv32-gnu-toolchain')

sys.o:
		./result/bin/riscv32-unknown-elf-gcc -mabi=ilp32f -march=rv32gcv -c ../lib/sys.c -o sys.o

bert.elf: sys.o bert.S
	@rv32-g++ -nostartfiles -nostdlib \
		sys.o bert.S ./entry.cpp  ../main.S -mno-relax -static -mcmodel=medany \
		-lc -lstdc++ -lgcc -lm -lg  \
		-fvisibility=hidden -Wl,--entry=start -fno-PIC -o bert.elf

.PHONY: clean
clean:
	rm bert.o
	rm sys.o
	rm bert.elf
