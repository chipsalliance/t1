RISCV_PREFIX := riscv32-linux-gnu-

CC ?= $(RISCV_PREFIX)gcc
OBJDUMP ?= $(RISCV_PREFIX)objdump
CFLAGS := -O0 -march=rv32i -nostdlib -fno-PIC -mno-relax -static

BUILD := build

PREFIX := install

SMOKE_CASES := $(wildcard ./smoke/*.c)
UTILS := utils/main.S utils/script.ld

.PHONY: all
all: install

.PHONY: smoke
smoke: $(SMOKE_CASES:./smoke/%.c=$(BUILD)/smoke/%.elf)

.PHONY: build
build: smoke

.PHONY: clean
clean:
	rm -rf $(BUILD)
	rm -rf $(PREFIX)

.PHONY: install
install: build
	install -d $(PREFIX)
	cp -r $(BUILD)/* $(PREFIX)/

$(BUILD)/smoke/%.elf: smoke/%.c $(UTILS)
	@mkdir -p $(@D)
	$(CC) $(CFLAGS) -T utils/script.ld utils/main.S $< -o $@
	$(OBJDUMP) -d $@ > $@.objdump
