func Execute_WFI(instruction: bits(32)) => Result
begin
  // NOTE : impl define behavior
  // currently we treat wfi as no-op,
  // and can be executed in any priv mode
  PC = PC + 4;
  return Retired();
end
