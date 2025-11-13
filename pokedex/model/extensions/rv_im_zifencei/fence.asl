func Execute_FENCE(instruction: bits(32)) => Result
begin
  // NOTE: impl defined behavior
  //
  // fence is no-op in instruction

  PC = PC + 4;
  return Retired();
end
