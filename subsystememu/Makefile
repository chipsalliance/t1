.PHONY: obj_dir/VTestHarness clean
obj_dir/VTestHarness:
	verilator --build --trace-fst -cc --exe --timing --main --CFLAGS "-O0 -g" --LDFLAGS "-g" -Wno-fatal \
	../out/vectorsubsystem/runFirtool.dest/ref_ClockGen_ClockGen_*.sv \
	../out/vectorsubsystem/runFirtool.dest/*.sv \
	src/dpic.cc \
	--top TestHarness -j `nproc`

clean:
	rm -rf obj_dir