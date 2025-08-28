let frm_bits : bits(3) = value[2:0];
// ignore reserved rounding mode
if frm_bits == '101' || frm_bits == '110' then
  return Retired();
end

FRM = frm_bits;

return Retired();
