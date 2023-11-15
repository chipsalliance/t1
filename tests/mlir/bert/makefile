entry.o:
	@rv32-g++ -c ./entry.cpp

bert.o:
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
	buddy-llc -O3 -mtriple riscv32 -target-abi ilp32f \
		-mattr=+m,+v -filetype=obj -riscv-v-vector-bits-min=128 \
		-o ./bert.o

bert: entry.o bert.o
	@rv32-g++ -lc -lm bert.o entry.o ../lib/sys.c ../../intrinsic/main.S -mno-relax -static -mcmodel=medany \
		-fvisibility=hidden -Wl,--entry=start -fno-PIC -o bert

.PHONY: clean
clean:
	rm bert.o entry.o
