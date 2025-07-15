RISCV_PREFIX := riscv32-linux-gnu-

CC ?= $(RISCV_PREFIX)gcc
OBJDUMP ?= $(RISCV_PREFIX)objdump
CFLAGS := -O0 -march=rv32im -nostdlib -fno-PIC -mno-relax -static
RVV_CFLAGS := -O0 -march=rv32im_zve32x_zvl256b -nostdlib -fno-PIC -mno-relax -static

BUILD := build

PREFIX := install

SMOKE_C := $(wildcard smoke/*.c)
SMOKE_S := $(wildcard smoke/*.S)
SMOKE_TARGETS := $(SMOKE_C:%.c=%.elf) $(SMOKE_S:%.S=%.elf)

SMOKE_V_C := $(wildcard smoke_v/*.c)
SMOKE_V_S := $(wildcard smoke_v/*.S)
SMOKE_V_TARGETS := $(SMOKE_V_C:%.c=%.elf) $(SMOKE_V_S:%.S=%.elf)

UTILS := utils/main.S utils/script.ld

.PHONY: all
all: install

.PHONY: smoke smoke_v
smoke: $(addprefix $(BUILD)/,$(SMOKE_TARGETS))
smoke_v: $(addprefix $(BUILD)/,$(SMOKE_V_TARGETS))

.PHONY: build
build: smoke smoke_v

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

$(BUILD)/smoke_v/%.elf:: smoke_v/%.c $(UTILS)
	@mkdir -p $(@D)
	$(CC) $(RVV_CFLAGS) -T utils/script.ld utils/main.S $< -o $@
	$(OBJDUMP) -d $@ > $@.objdump

$(BUILD)/smoke_v/%.elf:: smoke_v/%.S $(UTILS)
	@mkdir -p $(@D)
	$(CC) $(RVV_CFLAGS) -T utils/script.ld utils/main.S $< -o $@
	$(OBJDUMP) -d $@ > $@.objdump
