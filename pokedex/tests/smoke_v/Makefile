# Install codegen from https://github.com/chipsalliance/riscv-vector-tests
POKEDEX_COMPILE_STUBS ?=
VLEN ?= 128
XLEN := 32
MARCH ?= rv32imafc_zve32x_zvl$(VLEN)b
ABI ?= ilp32f

BUILD := build
PREFIX := build/install

CC ?= $(RISCV_PREFIX)gcc
OBJDUMP ?= $(RISCV_PREFIX)objdump

COMMON_CFLAGS := -march=$(MARCH) -mabi=$(ABI) -O0 -static -mcmodel=medany -fvisibility=hidden -nostdlib -nostartfiles -fno-PIC
OBJDUMP_FLAGS := --disassemble-all --disassemble-zeroes --section=.text --section=.text.startup --section=.text.init --section=.data

default: install

SMOKE_V_C := $(wildcard *.c)
SMOKE_V_S := $(wildcard *.S)
SMOKE_V_TARGETS := $(SMOKE_V_C:%.c=%.elf) $(SMOKE_V_S:%.S=%.elf)

$(BUILD)/%.elf:: %.c
	@mkdir -p $(@D)
	$(CC) $(COMMON_CFLAGS) -T $(POKEDEX_COMPILE_STUBS)/script.ld $(POKEDEX_COMPILE_STUBS)/main.S $< -o $@
	$(OBJDUMP) $(OBJDUMP_FLAGS) -d $@ > $(patsubst %.elf,%.objdump,$@)

$(BUILD)/%.elf:: %.S
	@mkdir -p $(@D)
	$(CC) $(COMMON_CFLAGS) -T $(POKEDEX_COMPILE_STUBS)/script.ld $(POKEDEX_COMPILE_STUBS)/main.S $< -o $@
	$(OBJDUMP) $(OBJDUMP_FLAGS) -d $@ > $(patsubst %.elf,%.objdump,$@)

build_targets := $(addprefix $(BUILD)/,$(SMOKE_V_TARGETS))
.PHONY: compile
compile: $(build_targets)

.PHONY: install
install: compile
	@mkdir -p $(PREFIX)/bin $(PREFIX)/share
	# Copy all *.elf to share directory
	cp -rv $(build_targets) $(PREFIX)/bin
	# Copy all *.objdump to share directory
	cp -rv $(foreach elf,$(build_targets),$(patsubst %.elf,%.objdump,$(elf))) $(PREFIX)/share
