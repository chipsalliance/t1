let rs1 : freg_index = UInt(GetRS1(instruction));
let rd : XREG_TYPE = UInt(GetRD(instruction));

// Don't operate on RD directly
let src1 : bits(32) = F[rs1];
let sign : bit = src1[31];
let exp : bits(8) = src1[30:23];
let mantissa : bits(23) = src1[22:0];

var mask : bits(32) = Zeros(32);

let is_signed : boolean = sign == '1';
let is_infinite_or_nan : boolean = IsOnes(exp);
let is_mantissa_zero : boolean = IsZero(mantissa);
let is_subnormal_or_zero : boolean = IsZero(exp);
let is_nan : boolean = is_infinite_or_nan && (!is_mantissa_zero);
let is_sig_nan : boolean = f32_is_signal_nan(src1);

if is_signed && is_infinite_or_nan && is_mantissa_zero then
  // rs1 is negative infinite
  mask[0] = '1';
elsif is_signed && (!is_infinite_or_nan) && (!is_subnormal_or_zero) then
  // rs1 is negative normal number
  mask[1] = '1';
elsif is_signed && is_subnormal_or_zero && (!is_mantissa_zero) then
  // rs1 is negative subnormal number
  mask[2] = '1';
elsif is_signed && is_subnormal_or_zero && is_mantissa_zero then
  // rs1 is negative zero
  mask[3] = '1';
elsif (!is_signed) && is_subnormal_or_zero && is_mantissa_zero then
  // rs1 is positive zero
  mask[4] = '1';
elsif (!is_signed) && is_subnormal_or_zero && (!is_mantissa_zero) then
  // rs1 is positive subnormal number
  mask[5] = '1';
elsif (!is_signed) && (!is_infinite_or_nan) && (!is_subnormal_or_zero) then
  // rs1 is positive normal number
  mask[6] = '1';
elsif (!is_signed) && is_infinite_or_nan && is_mantissa_zero then
  // rs1 is positive infinite
  mask[7] = '1';
elsif is_nan && is_sig_nan then
  // rs1 is signaling NaN
  mask[8] = '1';
elsif is_nan && (!is_sig_nan) then
  // rs1 is quiet NaN
  mask[9] = '1';
else
  FFI_print_str("internal bug: input floating point number cannot be classify");
  assert FALSE;
end

X[rd] = mask;

PC = PC + 4;

return Retired();
