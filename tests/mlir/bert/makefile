CC := riscv32-unknown-elf-gcc
CXX := riscv32-unknown-elf-g++

CFLAGS := -mabi=ilp32f -march=rv32gcv -fno-PIC
LDFLAGS := -nostartfiles -nostdlib -mno-relax -static -mcmodel=medany -Wl,--entry=start -lc -lstdc++ -lgcc -lm -lg

.PHONY: all
all: bert.elf

bert.S: bert-with-weights.mlir
	@buddy-opt $^ \
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
		-o $@

sys.o: ../lib/sys.c 
	$(CC) $^ $(CFLAGS) -c -o $@

entry.o: entry.cpp
	$(CXX) $^ $(CFLAGS) -c -o $@

bert.elf: sys.o entry.o bert.S ../main.S
	$(CC)  $^ $(LDFLAGS) -o $@

.PHONY: clean
clean:
	rm -f *.o *.elf
