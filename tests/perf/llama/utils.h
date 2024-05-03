#pragma once

#include <stdlib.h>
#include <memory.h>
#include <stdio.h>

// provide FILE-like operations for on-memory data

typedef struct {
  const char *data_begin;
  size_t size;
  const char *cursor;
} BIN;

inline BIN *bopen(const char *data_begin, size_t size) {
  BIN *bin = (BIN *) malloc(sizeof(BIN));
  bin->cursor = bin->data_begin = data_begin;
  bin->size = size;
  return bin;
}

inline void bclose(BIN *bin) {
  free(bin);
}

inline size_t bread(void *buffer, size_t size, size_t count, BIN *stream) {
  memcpy(buffer, (void *) stream->cursor, size * count);
  stream->cursor += size * count;
  return count;
}

inline size_t bseek(BIN *bin, long offset, int origin) {
  switch (origin) {
    case SEEK_SET:
      bin->cursor = bin->data_begin + offset; break;
    case SEEK_CUR:
      bin->cursor += offset; break;
    case SEEK_END:
      bin->cursor = bin->data_begin + bin->size - offset; break;
    default:
      return 1;
  }
  return 0;
}

