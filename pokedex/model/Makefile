.PHONY: all install clean

PROJECT_NAME := pokedex_model

BUILD_DIR := ./build

RVOPCODE_CLI := rvopcode
JINJA_CLI := minijinja-cli
JINJA_FLAGS := --strict
ASLI := asli
ASL2C := asl2c
PREFIX := $(BUILD_DIR)/$(PROJECT_NAME)

# RISCV Opcodes
_RISCV_OPCODES_SRC = ${RISCV_OPCODES_SRC}
RISCV_OPCODES_SRC ?= $(_RISCV_OPCODES_SRC)

PROJECT_SRCS := aslbuild/project.json aslbuild/asl2c.prj
HANDWRITTEN_SRCS := $(wildcard handwritten/*.asl) $(wildcard csr/*.asl) $(wildcard extensions/*/*.asl) $(wildcard generated/*/*.asl)
ENABLED_EXTENSIONS := $(foreach dir,$(wildcard template/extensions/*),$(notdir $(dir)))

all: install

install: $(PREFIX)/lib $(PREFIX)/include

$(PREFIX)/lib $(PREFIX)/include: $(BUILD_DIR)/2-cgen/makefile
	mkdir -p $(PREFIX)
	cd $(<D) && $(MAKE) CFLAGS="$(shell $(ASLI) --print-c-flags) -O3 ${CFLAGS}" TARGET=lib$(PROJECT_NAME).a PREFIX=build
	cp -r $(<D)/build/* $(PREFIX)/

# ---------------------------------------
# Stage 0: generate ASL for code snippets
# ---------------------------------------
STAGE_0 := $(BUILD_DIR)/0-aslgen
RVV_INSTS := $(basename $(notdir $(wildcard template/extensions/rv_v/data_files/*.toml)))

# First copy template sources
$(STAGE_0)/template: $(shell find template -type f)
	rm -rf $(@D)
	mkdir -p $(@D)
	mkdir -p $(@D)/output
	cp -r template $(@D)/

# JSON data for instructions
$(STAGE_0)/rvopcodes.json: $(STAGE_0)/template $(RISCV_OPCODES_SRC)
	$(RVOPCODE_CLI) instructions \
		--output $@ \
		--riscv-opcodes-src-dir $(RISCV_OPCODES_SRC) \
		$(addprefix --enable-instruction-sets ,$(ENABLED_EXTENSIONS))

# Then generate inst_dispatch.asl
$(STAGE_0)/output/inst_dispatch.asl: $(STAGE_0)/rvopcodes.json
	$(JINJA_CLI) $(JINJA_FLAGS) -f json -o $@ \
		$(STAGE_0)/template/inst_dispatch.asl.j2 $(STAGE_0)/rvopcodes.json 2> >(head -n 30)

# generate inst_unimplemented.asl
$(STAGE_0)/output/inst_unimplemented.asl: unimplemented.json
	$(JINJA_CLI) $(JINJA_FLAGS) -f json -o $@ \
		template/inst_unimplemented.asl.j2 unimplemented.json

$(STAGE_0)/causes.json: $(STAGE_0)/template $(RISCV_OPCODES_SRC)
	$(RVOPCODE_CLI) causes \
		--output $@ \
		--riscv-opcodes-src-dir $(RISCV_OPCODES_SRC)

$(STAGE_0)/output/causes.asl: $(STAGE_0)/causes.json
	$(JINJA_CLI) $(JINJA_FLAGS) -f json -o $@ \
		$(STAGE_0)/template/causes.asl.j2 $(STAGE_0)/causes.json

$(STAGE_0)/csr_meta.json: $(STAGE_0)
	$(RVOPCODE_CLI) csr --dir csr --output $@

$(STAGE_0)/output/csr_operation.asl: $(STAGE_0)/csr_meta.json
	$(JINJA_CLI) $(JINJA_FLAGS) -f json -o $@ \
		template/csr_operation.asl.j2 $(STAGE_0)/csr_meta.json

# -------------------------------------------
# Stage 1: group all ASL file into one folder
# -------------------------------------------
CODEGEN_SRCS := inst_dispatch.asl inst_unimplemented.asl causes.asl csr_operation.asl
STAGE_1 := $(BUILD_DIR)/1-project
$(STAGE_1): $(PROJECT_SRCS) $(addprefix $(STAGE_0)/output/,$(CODEGEN_SRCS))
	mkdir -p $@
	# Copy project related source to rvcore
	cp $^ $@/

.PHONY: project
project: $(BUILD_DIR)/1-project

# -------------------
# Stage 2: generate C
# -------------------
$(BUILD_DIR)/2-cgen/makefile: $(STAGE_1) aslbuild/libpokedex.makefile $(HANDWRITTEN_SRCS)
	mkdir -p $(@D)/dumps
	sed -i 's|@CGEN_OUTPUT@|$(@D)|g' $(STAGE_1)/asl2c.prj
	$(ASLI) \
		--nobanner --batchmode \
		--configuration=$(STAGE_1)/project.json \
		--project=$(STAGE_1)/asl2c.prj \
		--check-exception-markers \
		--check-call-markers \
		--check-constraints \
		--runtime-checks \
		$(wildcard $(STAGE_1)/*.asl) $(HANDWRITTEN_SRCS)
	cp -T aslbuild/libpokedex.makefile $@

.PHONY: asl2c
asl2c: $(BUILD_DIR)/2-cgen/makefile

# -----
# Chore
# -----
clean:
	@rm -rf $(BUILD_DIR)
