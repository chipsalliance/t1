#include "emurt.h"
#include <string.h>

#define EXIT_REG 0x10000000
#define UART_W_REG 0x10000010
#define PERF_REG 0x10000014

void t1_put_char(char c) { *(uint32_t volatile *)(UART_W_REG) = (uint8_t)c; }
void place_counter(int i) { *(int volatile *)(PERF_REG) = i; }

extern char *__drambegin;
char *t1_dram_top;
void *dram_sbrk(size_t size) {
  char *base;
  if (!t1_dram_top)
    t1_dram_top = (char *)&__drambegin;
  base = t1_dram_top;
  t1_dram_top += size;
  return base;
};

struct dram_blk {
  size_t size;
  struct dram_blk *next;
  int is_free;
};

#define DRAM_BLK_SIZE (sizeof(struct dram_blk))

void *dram_blk_list_head = NULL;

// Iterate from HEAD and find next available block. A block is consider
// available only when a node is mark as free and its size is appropriate for
// the requested size. Argument `visited` is used for recording the tail of
// current iteration, so we can create new block after the list.
struct dram_blk *_find_free_dram_blk(struct dram_blk **visited, size_t size) {
  struct dram_blk *current = dram_blk_list_head;

  while (current && !(current->is_free && current->size >= size)) {
    visited = &current;
    current = current->next;
  }

  return current;
}

// TODO align
struct dram_blk *_new_dram_blk(struct dram_blk *blk, size_t size) {
  struct dram_blk *new;

  // get DRAM top
  new = dram_sbrk(0);

  // TODO get return address and check it is valid
  dram_sbrk(size + DRAM_BLK_SIZE);

  if (blk) {
    blk->next = new;
  }

  new->size = size;
  new->next = NULL;
  new->is_free = 0;

  return new;
}

void *dram_malloc(size_t size) {
  struct dram_blk *blk;

  if (size <= 0) {
    return NULL;
  }

  if (!dram_blk_list_head) {
    blk = _new_dram_blk(NULL, size);
    if (!blk) {
      return NULL;
    }
    dram_blk_list_head = blk;
  } else {
    struct dram_blk *visited = dram_blk_list_head;
    blk = _find_free_dram_blk(&visited, size);
    if (!blk) {
      blk = _new_dram_blk(visited, size);
      if (!blk) {
        return NULL;
      }
    } else {
      blk->is_free = 0;
    }
  }

  // return the address after sizeof(struct dram_blk)
  return blk + 1;
}

void dram_free(void *ptr) {
  if (!ptr) {
    // TODO throw exception
    return;
  }

  // TODO need more safe cast
  struct dram_blk *blk = ((struct dram_blk *)ptr) - 1;
  if (blk->is_free) {
    // TODO throw exception
    return;
  }

  blk->is_free = 1;
}

void *dram_realloc(void *ptr, size_t size) {
  if (!ptr) {
    // TODO throw exception
    return dram_malloc(size);
  }

  struct dram_blk *blk = ((struct dram_blk *)ptr) - 1;
  if (blk->is_free) {
    // TODO internal error: throw exception
    return NULL;
  }

  if (blk->size >= size) {
    // TODO split size to save free space
    return ptr;
  }

  void *new;
  new = dram_malloc(size);
  if (!new) {
    // TODO throw
    return NULL;
  }

  memcpy(new, ptr, blk->size);
  dram_free(ptr);
  return new;
}

///////////////////////
// uart
///////////////////////
int _write(int file, char *ptr, int len) {
  int i = 0;
  for (; i < len; i++) {
    t1_put_char(ptr[i]);
  }
  return i;
}

void _exit(int code) {
  __asm__("li x1, 0x10000000");
  __asm__("li x2, 0xdeadbeef");
  __asm__("sw x2, 0(x1)");
  __asm__("j .");
  __builtin_unreachable();
}

///////////////////////
// allocation
///////////////////////

extern char *__heapbegin;
char *heap_top;

char *_sbrk(int nbytes) {
  char *base;

  if (!heap_top)
    heap_top = (char *)&__heapbegin;
  base = heap_top;
  heap_top += nbytes;

  return base;
}

// Magic symbols that should be provided by linker
extern void (*__preinit_array_start[])(void) __attribute__((weak));
extern void (*__preinit_array_end[])(void) __attribute__((weak));
extern void (*__init_array_start[])(void) __attribute__((weak));
extern void (*__init_array_end[])(void) __attribute__((weak));

void __t1_init_array(void) {
  int32_t count;
  int32_t i;

  count = __preinit_array_end - __preinit_array_start;
  for (i = 0; i < count; i++) {
    __preinit_array_start[i]();
  }

  count = __init_array_end - __init_array_start;
  for (i = 0; i < count; i++) {
    __init_array_start[i]();
  }
}

///////////////////////
// unimplemented
///////////////////////

// We don't support FS
int _isatty(int file) { return -1; }

// We don't support proc
int _kill(int pid, int sig) { return -1; }

// We don't support proc
int _getpid() { return -1; }

// We don't support FS
int _fstat(int file, struct stat *st) { return -1; }

// We don't support close
int _close(int file) { return -1; }

// We don't support lseek
int _lseek(int file, int ptr, int dir) { return -1; }

// TODO: We can support read
int _read(int file, char *ptr, int len) { return -1; }
