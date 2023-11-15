#include <stdlib.h>
#include <string.h>
#include <stdio.h>

void test() {
  for (int n = 0; n < 2000; n++) {
    int *p1 = (int*)malloc(4 * sizeof(int));
    if(!p1) {
      exit(-1);
    }
    for (int i = 0; i < 4; i++) {
      p1[i] = i * i;
    }
    for (int i = 0; i < 4; i++) {
      printf("p1[%d] = %d\n", i, p1[i]);
    }
    free(p1);
  }

  char str1[] = "chipsalliance/t1";
  char len = strlen(str1) - 3;
  char *repo = (char*)malloc(len * sizeof(char));
  memcpy(repo, str1, len);
  printf("Checkout repo: %s\n", repo);
  exit(0);
}
