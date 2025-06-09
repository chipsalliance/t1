// Program Counter
var PC : bits(32)

// General Propose Register
var __GPR : array[31] of bits(32)

getter GPR[i : integer] => bits(32)
begin
  if i == 0 then
    return 32'x0;
  else
    return __GPR[i - 1];
end

setter GPR[i : integer] = value : bit(32)
begin
  if i > 0 then
    GPR[i - 1] = value;
end

// Architecture States

// General States Reset
func Reset()
begin
  for i = 1 to 31 do
    GPR[i] = 32'x0;
  end
end
