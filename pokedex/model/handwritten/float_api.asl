func f32_add(s1 : bits(32), s2 : bits(32), rm : RM) => bits(32)
begin
  ffi_set_rounding_mode(RM_to_bits(rm));
  return ffi_f32_add(s1, s2);
end

func f32_sub(s1 : bits(32), s2 : bits(32), rm : RM) => bits(32)
begin
  ffi_set_rounding_mode(RM_to_bits(rm));
  return ffi_f32_sub(s1, s2);
end
