# Install codegen from https://github.com/chipsalliance/riscv-vector-tests
CODEGEN_INSTALL_DIR ?=
POKEDEX_COMPILE_STUBS ?=
RISCV_TESTS_SRC ?=
VLEN ?= 128
XLEN := 32
MARCH ?= rv32imafc_zve32x_zvl$(VLEN)b
ABI ?= ilp32f

CODEGEN_CONFIG_DIR := $(CODEGEN_INSTALL_DIR)/configs
CODEGEN_SINGLE := $(CODEGEN_INSTALL_DIR)/bin/single

CODEGEN_TARGETS_DEFINE := case_list.txt
CODEGEN_TARGETS := $(shell grep -v "^#" $(CODEGEN_TARGETS_DEFINE) | xargs | sed 's/\./_/g')
CODEGEN_ELFS := $(addsuffix .elf,$(CODEGEN_TARGETS))

BUILD := build
PREFIX := build/install

CC ?= $(RISCV_PREFIX)gcc
OBJDUMP ?= $(RISCV_PREFIX)objdump

COMMON_CFLAGS := -march=$(MARCH) -mabi=$(ABI) -O0 -static -mcmodel=medany -fvisibility=hidden -nostdlib -nostartfiles -fno-PIC
OBJDUMP_FLAGS := --disassemble-all --disassemble-zeroes --section=.text --section=.text.startup --section=.text.init --section=.data

default: install

$(BUILD)/%.S:
	@mkdir -p $(@D)
	$(CODEGEN_SINGLE) \
		-VLEN $(VLEN) -XLEN $(XLEN) -float16=false -repeat 16 -testfloat3level 2 \
		-configfile $(CODEGEN_CONFIG_DIR)/v/$(shell printf $* | sed 's/_/\./g').toml \
		-outputfile "$@"

$(BUILD)/%.elf: $(BUILD)/%.S
	@mkdir -p $(@D)
	$(CC) $(COMMON_CFLAGS) \
		-T $(POKEDEX_COMPILE_STUBS)/script.ld \
		-I $(POKEDEX_COMPILE_STUBS)/include \
		-I $(RISCV_TESTS_SRC)/isa/macros/scalar \
		$< -o $@
	$(OBJDUMP) $(OBJDUMP_FLAGS) -d $@ > $(patsubst %.elf,%.objdump,$@)

build_targets := $(addprefix $(BUILD)/,$(CODEGEN_ELFS))
.PHONY: compile
compile: $(build_targets)

.PHONY: install
install: compile
	@mkdir -p $(PREFIX)/bin $(PREFIX)/share
	# Copy all *.elf to share directory
	cp -rv $(build_targets) $(PREFIX)/bin
	# Copy all *.objdump to share directory
	cp -rv $(foreach elf,$(build_targets),$(patsubst %.elf,%.objdump,$(elf))) $(PREFIX)/share
