func Execute_FENCE_I(instruction: bits(32)) => Result
begin
  // TODO : fence_i is no-op,
  // since we do not modeling cache in functional emulator
  PC = PC + 4;
  return Retired();
end
