RISCV_PREFIX := riscv32-linux-gnu-

CC ?= $(RISCV_PREFIX)gcc
OBJDUMP ?= $(RISCV_PREFIX)objdump

COMMON_CFLAGS := -march=rv32imac -mabi=ilp32 -O0 -static -mcmodel=medany -fvisibility=hidden -nostdlib -nostartfiles -fno-PIC
OBJDUMP_FLAGS := --disassemble-all --disassemble-zeroes --section=.text --section=.text.startup --section=.text.init --section=.data

BUILD := build

PREFIX := install

UTILS := utils/main.S utils/script.ld

.PHONY: all
all: install

# -------------------------------------------------------------------------------------------------
# SMOKE
# -------------------------------------------------------------------------------------------------
SMOKE_C := $(wildcard smoke/*.c)
SMOKE_S := $(wildcard smoke/*.S)
SMOKE_TARGETS := $(SMOKE_C:%.c=%.elf) $(SMOKE_S:%.S=%.elf)

$(BUILD)/smoke/%.elf:: smoke/%.c $(UTILS)
	@mkdir -p $(@D)
	$(CC) $(COMMON_CFLAGS) -T utils/script.ld utils/main.S $< -o $@
	$(OBJDUMP) $(OBJDUMP_FLAGS) -d $@ > $@.objdump

$(BUILD)/smoke/%.elf:: smoke/%.S $(UTILS)
	@mkdir -p $(@D)
	$(CC) $(COMMON_CFLAGS) -T utils/script.ld utils/main.S $< -o $@
	$(OBJDUMP) $(OBJDUMP_FLAGS) -d $@ > $@.objdump


# -------------------------------------------------------------------------------------------------
# SMOKE for vector
# -------------------------------------------------------------------------------------------------
SMOKE_V_C := $(wildcard smoke_v/*.c)
SMOKE_V_S := $(wildcard smoke_v/*.S)
SMOKE_V_TARGETS := $(SMOKE_V_C:%.c=%.elf) $(SMOKE_V_S:%.S=%.elf)
RVV_CFLAGS := -O0 -march=rv32im_zve32x_zvl256b -nostdlib -fno-PIC -mno-relax -static

$(BUILD)/smoke_v/%.elf:: smoke_v/%.c $(UTILS)
	@mkdir -p $(@D)
	$(CC) $(RVV_CFLAGS) -T utils/script.ld utils/main.S $< -o $@
	$(OBJDUMP) $(OBJDUMP_FLAGS) -d $@ > $@.objdump

$(BUILD)/smoke_v/%.elf:: smoke_v/%.S $(UTILS)
	@mkdir -p $(@D)
	$(CC) $(RVV_CFLAGS) -T utils/script.ld utils/main.S $< -o $@
	$(OBJDUMP) $(OBJDUMP_FLAGS) -d $@ > $@.objdump


# -------------------------------------------------------------------------------------------------
# RISCV TESTS: https://github.com/riscv-software-src/riscv-tests
# -------------------------------------------------------------------------------------------------
RISCV_TESTS_SRC ?=
RISCV_TESTS_ENV := riscv-tests-env
RISCV_TESTS_ISA := rv32ui rv32um rv32uc rv32ua
RISCV_TESTS_ISA_SRCS := $(foreach dir,$(RISCV_TESTS_ISA),$(wildcard $(RISCV_TESTS_SRC)/isa/$(dir)/*.S))
RISCV_TESTS_BUILD_TARGETS := $(patsubst $(RISCV_TESTS_SRC)/%.S,%.elf,$(RISCV_TESTS_ISA_SRCS))
$(BUILD)/riscv_tests/isa/%.elf: $(RISCV_TESTS_SRC)/isa/%.S $(RISCV_TESTS_ENV)/riscv_test.h
	@mkdir -p $(@D)
	$(CC) $(COMMON_CFLAGS) \
		-T utils/script.ld -I $(RISCV_TESTS_ENV) -I $(RISCV_TESTS_SRC)/isa/macros/scalar \
		$< -o $@
	$(OBJDUMP) $(OBJDUMP_FLAGS) -d $@ > $@.objdump


.PHONY: smoke smoke_v riscv_tests
smoke: $(addprefix $(BUILD)/,$(SMOKE_TARGETS))
smoke_v: $(addprefix $(BUILD)/,$(SMOKE_V_TARGETS))
riscv_tests: $(addprefix $(BUILD)/riscv_tests/,$(RISCV_TESTS_BUILD_TARGETS))

.PHONY: build
build: smoke smoke_v riscv_tests

.PHONY: clean
clean:
	rm -rf $(BUILD)
	rm -rf $(PREFIX)

.PHONY: install
install: build
	install -d $(PREFIX)
	cp -r $(BUILD)/* $(PREFIX)/
