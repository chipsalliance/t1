RISCV_PREFIX := riscv32-linux-gnu-

CC ?= $(RISCV_PREFIX)gcc
OBJDUMP ?= $(RISCV_PREFIX)objdump
CFLAGS := -O0 -march=rv32i -nostdlib -fno-PIC -mno-relax -static

BUILD := build

PREFIX := $(BUILD)/install

.PHONY: all
all: prepare build

.PHONY: prepare
prepare:
	mkdir $(BUILD)

.PHONY: install
install: $(BUILD)/test.elf
	install -d $(PREFIX)/bin $(PREFIX)/src
	install $< $(PREFIX)/bin/test.elf
	$(OBJDUMP) -d $< > $(PREFIX)/src/test.elf.objdump

$(BUILD)/test.elf: main.S test.c script.ld
	$(CC) $(CFLAGS) -T script.ld main.S test.c -o $@
