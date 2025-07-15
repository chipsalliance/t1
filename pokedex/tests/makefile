RISCV_PREFIX := riscv32-linux-gnu-

CC ?= $(RISCV_PREFIX)gcc
OBJDUMP ?= $(RISCV_PREFIX)objdump
CFLAGS := -O0 -march=rv32im -nostdlib -fno-PIC -mno-relax -static

BUILD := build

PREFIX := install

SMOKE_C := $(wildcard smoke/*.c)
SMOKE_S := $(wildcard smoke/*.S)
SMOKE_TARGETS := $(SMOKE_C:%.c=%.elf) $(SMOKE_S:%.S=%.elf)

UTILS := utils/main.S utils/script.ld

.PHONY: all
all: install

.PHONY: smoke
smoke: $(addprefix $(BUILD)/,$(SMOKE_TARGETS))

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

$(BUILD)/smoke/%.elf:: smoke/%.c $(UTILS)
	@mkdir -p $(@D)
	$(CC) $(CFLAGS) -T utils/script.ld utils/main.S $< -o $@
	$(OBJDUMP) -d $@ > $@.objdump

$(BUILD)/smoke/%.elf:: smoke/%.S $(UTILS)
	@mkdir -p $(@D)
	$(CC) $(CFLAGS) -T utils/script.ld utils/main.S $< -o $@
	$(OBJDUMP) -d $@ > $@.objdump
