CC := riscv32-unknown-elf-gcc

CFLAGS := -mabi=ilp32f -march=rv32gcv -fno-PIC
LDFLAGS := -nostartfiles -nostdlib -mno-relax -static -mcmodel=medany -Wl,--entry=start -lc -lstdc++ -lgcc -lm -lg

.PHONY: all
all: newlib-example.elf

sys.o: sys.c
	$(CC) $^ $(CFLAGS) -c -o $@

main.o: main.S
	$(CC) $^ $(CFLAGS) -c -o $@

newlib-example.elf: sys.o main.o newlib-example.c
	$(CC) $^ $(LDFLAGS) -o $@

.PHONY: clean
clean:
	rm -f *.o *.elf
