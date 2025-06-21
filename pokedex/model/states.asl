// Program Counter
var PC : bits(32);

// General Propose Register
var __GPR : array[31] of bits(32);

getter X[i : integer {0..31}] => bits(32)
begin
  if i == 0 then
    return Zeros(32);
  else
    return __GPR[i - 1];
  end
end

setter X[i : integer {0..31}] = value : bits(32)
begin
  if i > 0 then
    __GPR[i - 1] = value;
  end

  // notify emulator that a write to GPR occur
  FFI_write_GPR_hook(i, value);
end

// Architecture States

// General States Reset
func Reset()
begin
  for i = 1 to 31 do
    X[i] = Zeros(32);
  end
end
