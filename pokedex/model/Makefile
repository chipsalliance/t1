.PHONY: all install clean

PROJECT_NAME := pokedex-sim

BUILD_DIR := ./build
MODEL_DIR := .

CODEGEN_CLI := codegen
ASLI := asli
ASL2C := asl2c
PREFIX := $(BUILD_DIR)/simlib

PRJ_SRCS := project.json asl2c.prj $(wildcard $(MODEL_DIR)/handwritten/*.asl)

# RISCV Opcodes
_RISCV_OPCODES_SRC = ${RISCV_OPCODES_SRC}
RISCV_OPCODES_SRC ?= $(_RISCV_OPCODES_SRC)

CODEGEN_ASL_SRCS := $(shell find $(MODEL_DIR)/{csr,extensions} -mindepth 2 -name '*.asl')

# Due to multiple codegen phrases, following targets fails to depends on concreate filename.
# However using directory as dependencies will cause makefile rebuild in each run, so here is
# a marker file to help makefile cache build result.
FINISH_MARKER = .stage_finish


all: install

install: $(PREFIX)/lib $(PREFIX)/include

$(PREFIX)/lib $(PREFIX)/include: $(BUILD_DIR)/2-cgen/makefile
	mkdir -p $(PREFIX)
	cd $(<D) && $(MAKE) CFLAGS="$(shell $(ASLI) --print-c-flags)" TARGET=libpokedex_sim.a PREFIX=build
	cp -r $(<D)/build/* $(PREFIX)/

# ---------------------------------------
# Stage 0: generate ASL for code snippets
# ---------------------------------------
$(BUILD_DIR)/0-aslgen/$(FINISH_MARKER): $(CODEGEN_ASL_SRCS) $(RISCV_OPCODES_SRC)
	mkdir -p $(@D)
	$(CODEGEN_CLI) \
		--riscv-opcodes-src-dir $(RISCV_OPCODES_SRC) \
		--input-model-dir $(MODEL_DIR) \
		--output-dir $(@D)
	@touch $@


# -------------------------------------------
# Stage 1: group all ASL file into one folder
# -------------------------------------------
$(BUILD_DIR)/1-project/$(FINISH_MARKER): $(BUILD_DIR)/0-aslgen/$(FINISH_MARKER) $(PRJ_SRCS)
	mkdir -p $(@D)
	# Copy project related source to rvcore
	cp $(PRJ_SRCS) $(@D)/
	# Copy stage 0 generated file to rvcore
	cp $(wildcard $(<D)/*.asl) $(@D)/
	@touch $@

.PHONY: project
project: $(BUILD_DIR)/1-project/$(FINISH_MARKER)


# -------------------
# Stage 2: generate C
# -------------------
$(BUILD_DIR)/2-cgen/makefile: $(BUILD_DIR)/1-project/$(FINISH_MARKER) $(MODEL_DIR)/libpokedex.makefile
	mkdir -p $(@D)/dumps
	sed -i 's|@CGEN_OUTPUT@|$(@D)|g' $(BUILD_DIR)/1-project/asl2c.prj
	$(ASLI) \
		--nobanner --batchmode \
		--configuration=$(<D)/project.json \
		--project=$(<D)/asl2c.prj \
		--check-exception-markers \
		--check-call-markers \
		--check-constraints \
		--runtime-checks \
		$(wildcard $(<D)/*.asl)
	cp -T $(MODEL_DIR)/libpokedex.makefile $(@D)/makefile

.PHONY: asl2c
asl2c: $(BUILD_DIR)/2-cgen/makefile


# -----
# Chore
# -----
clean:
	@rm -rf $(BUILD_DIR)
