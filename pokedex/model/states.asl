// Program Counter
var PC : bits(32)

// General Propose Register
var __GPR : array[31] of bits(32)

getter X[i : integer] => bits(32)
begin
  assert i >= 0 && i <= 31;

  if i == 0 then
    return Zeros(32);
  else
    return __GPR[i - 1];
end

setter X[i : integer] = value : bit(32)
begin
  assert i >= 0 && i <= 31;

  if i > 0 then
    __GPR[i - 1] = value;
end

// Architecture States

// General States Reset
func Reset()
begin
  for i = 1 to 31 do
    X[i] = Zeros(32);
  end
end
