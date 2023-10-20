extern char* heap_end;
char *heap_ptr;

char *_sbrk(int nbytes) {
  char *base;

  if (!heap_ptr)
    heap_ptr = (char *)&heap_end;
  base = heap_ptr;
  heap_ptr += nbytes;

  return base;
}

void _exit(int code) {
  __asm__("csrwi 0x7cc, 0");
  __builtin_unreachable();
}

int _close(int file) {
  return -1;
}

int _lseek(int file, int ptr, int dir) {
  return -1;
}

int _read(int file, char* ptr, int len) {
  return -1;
}

int _write(int file, char* ptr, int len) {
  return -1;
}
