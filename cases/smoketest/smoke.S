.global _start
_start: 
  add x2, x1, x1
  vsetivli x0, 7, e8, m1, ta, ma
  vadd.vv v2, v1, v1
  vadd.vv v2, v1, v1
  vadd.vv v2, v1, v1
