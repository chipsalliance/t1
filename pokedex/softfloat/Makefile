CC ?= gcc
AR ?= ar
CFLAGS ?= -O2

PREFIX ?= build

LIB := libsoftfloat_ext.a

all: $(LIB)

install: $(LIB)
	mkdir -p $(PREFIX)/lib
	cp $< $(PREFIX)/lib

$(LIB): wrapper.o
	$(AR) rcs $@ $<

%.o: %.c
	$(CC) $(CFLAGS) -c $< -o $@
