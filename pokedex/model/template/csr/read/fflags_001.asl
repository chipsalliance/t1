return OK([
  Zeros(27),
  bool_to_bits(F_XCPT_NV, 1),
  bool_to_bits(F_XCPT_DZ, 1),
  bool_to_bits(F_XCPT_OF, 1),
  bool_to_bits(F_XCPT_UF, 1),
  bool_to_bits(F_XCPT_NX, 1)
]);
