func set_fflags()
begin
  let flag : integer = ffi_get_softfloat_exception_flags();
  clear_fflags();
  case flag of
    when softfloat_flag_inexact =>
      F_XCPT_NX = TRUE;
    when softfloat_flag_underflow =>
      F_XCPT_UF = TRUE;
    when softfloat_flag_overflow =>
      F_XCPT_OF = TRUE;
    when softfloat_flag_infinite =>
      F_XCPT_DZ = TRUE;
    when softfloat_flag_invalid =>
      F_XCPT_NV = TRUE;
    otherwise => assert FALSE;
  end
end

func f32_add(s1 : bits(32), s2 : bits(32), rm : RM) => bits(32)
begin
  ffi_set_rounding_mode(RM_to_bits(rm));
  let result : bits(32) = ffi_f32_add(s1, s2);
  set_fflags();
  return result;
end

func f32_sub(s1 : bits(32), s2 : bits(32), rm : RM) => bits(32)
begin
  ffi_set_rounding_mode(RM_to_bits(rm));
  return ffi_f32_sub(s1, s2);
end
