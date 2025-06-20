# This file is auto generated.
#
# This makefile helps the main makefile to search all the codegen sources and
# build them with only static pattern rules

CGEN_DIR := .

CFLAGS :=

CC := gcc
AR := ar

SRCS := $(wildcard $(CGEN_DIR)/*.c)
OBJS := $(SRCS:.c=.o)
HEADERS := $(wildcard $(CGEN_DIR)/*.h)

TARGET := libpokedex_sim.a

PREFIX := build

.PHONY: all
all: $(TARGET) $(HEADERS)
	mkdir -p $(PREFIX)/lib $(PREFIX)/include
	cp $< $(PREFIX)/lib/
	cp $(filter %.h,$(HEADERS)) $(PREFIX)/include

%.o: %.c
	$(CC) $(CFLAGS) -c $< -o $@

$(TARGET): $(OBJS)
	$(AR) rcs $@ $?
