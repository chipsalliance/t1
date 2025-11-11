# Install codegen from https://github.com/chipsalliance/riscv-vector-tests
POKEDEX_COMPILE_STUBS ?=
VLEN ?= 128
XLEN := 32
MARCH ?= rv32imafc_zve32x_zvl$(VLEN)b
ABI ?= ilp32f

RISCV_TESTS_SRC ?=
CASE_LIST := ./case_list.txt
TESTS_CASES := $(shell grep -v "^#" $(CASE_LIST) | xargs)

BUILD := build
PREFIX := build/install

CC ?= $(RISCV_PREFIX)gcc
OBJDUMP ?= $(RISCV_PREFIX)objdump

COMMON_CFLAGS := -march=$(MARCH) -mabi=$(ABI) -O0 -static -mcmodel=medany -fvisibility=hidden -nostdlib -nostartfiles -fno-PIC
OBJDUMP_FLAGS := --disassemble-all --disassemble-zeroes --section=.text --section=.text.startup --section=.text.init --section=.data

default: install

$(BUILD)/%.elf: $(RISCV_TESTS_SRC)/isa/%.S
	@mkdir -p $(@D)
	$(CC) $(COMMON_CFLAGS) \
		-T $(POKEDEX_COMPILE_STUBS)/script.ld \
		-I $(POKEDEX_COMPILE_STUBS)/include -I $(RISCV_TESTS_SRC)/isa/macros/scalar \
		$< -o $@
	$(OBJDUMP) $(OBJDUMP_FLAGS) -d $@ > $(patsubst %.elf,%.objdump,$@)

build_targets := $(addprefix $(BUILD)/,$(patsubst %.S,%.elf,$(TESTS_CASES)))
.PHONY: compile
compile: $(build_targets)

.PHONY: install
install: compile
	@mkdir -p $(PREFIX)/bin $(PREFIX)/share
	# Copy all *.elf to share directory
	cp -rv $(BUILD)/* $(PREFIX)/bin
	# Copy all *.objdump to share directory
	cp -rv $(foreach elf,$(build_targets),$(patsubst %.elf,%.objdump,$(elf))) $(PREFIX)/share
