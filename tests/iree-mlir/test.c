#include <stdio.h>

#include "iree/base/api.h"

extern iree_status_t Run(void);

void test(void) {
  printf("Running simple_embedding...\n");

  const iree_status_t result = Run();
  if (!iree_status_is_ok(result)) {
    iree_status_fprint(stderr, result);
    iree_status_free(result);
  } else {
    printf("Execution successful!\n");
  }
  printf("simple_embedding done\n");
}
