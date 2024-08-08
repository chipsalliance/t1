#ifndef MEMREF_H
#define MEMREF_H

// Generate a new struct with T-type, N-dimension memref with name
// MemRef_T_dimN.
//
// Example:
//
//   NEW_MEMREF(float, 2);
//   // Equals to
//   struct MemRef_float_dim2 {
//     float *allocatedPtr;
//     float *alignedPtr;
//     int offset;
//     int sizes[2];
//     int strides[2];
//   };
//
#define NEW_MEMREF(T, N)                                                       \
  struct MemRef_##T##_dim##N {                                                 \
    T *allocatedPtr;                                                           \
    T *alignedPtr;                                                             \
    int offset;                                                                \
    int sizes[N];                                                              \
    int strides[N];                                                            \
  }

#endif
