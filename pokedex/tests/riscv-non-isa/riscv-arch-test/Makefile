SUITE_SRC_DIR ?= ./riscv-test-suite
SUITE_ARCH := rv32i_m
SUITE_EXTS := I M A F C F_Zcf Zifencei

BUILD_DIR := build

RISCV_PREFIX := riscv32-linux-gnu-

CC ?= $(RISCV_PREFIX)gcc
OBJDUMP ?= $(RISCV_PREFIX)objdump

COMMON_CFLAGS := -march=rv32imafc_zvl256b_zve32x -mabi=ilp32f -O3 -static -mcmodel=medany -fvisibility=hidden -nostdlib -nostartfiles -fno-PIC
OBJDUMP_FLAGS := --disassemble-all --disassemble-zeroes --section=.text --section=.text.startup --section=.text.init --section=.data

find_source = $(shell find $(dir) -type f -name '*.S')
all_suites := $(addprefix $(SUITE_SRC_DIR)/$(SUITE_ARCH)/, $(SUITE_EXTS))
all_sources := $(foreach dir,$(all_suites),$(find_source))
all_virt_srcs := $(subst $(SUITE_SRC_DIR),$(BUILD_DIR),$(all_sources))
targets := $(patsubst %.S,%.elf,$(all_virt_srcs))

default: compile

$(BUILD_DIR)/$(SUITE_ARCH)/%.elf: $(SUITE_SRC_DIR)/$(SUITE_ARCH)/%.S
	@mkdir -p $(@D)
	$(CC) -T./link.ld -I. -I$(SUITE_SRC_DIR)/env $(COMMON_CFLAGS) $< -o $@
	$(OBJDUMP) $(OBJDUMP_FLAGS) -d $@ > $(patsubst %.elf,%.objdump,$@)

compile: $(targets)

clean:
	rm -rf $(BUILD_DIR)
