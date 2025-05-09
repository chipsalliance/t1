#pragma once

#include <stddef.h>
#include <stdint.h>

void place_counter(int i);

void* dram_malloc(size_t size);
void* dram_realloc(void *ptr, size_t size);
void dram_free(void *ptr);
