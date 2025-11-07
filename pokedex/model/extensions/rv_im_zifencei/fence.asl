func Execute_FENCE(instruction: bits(32)) => Result
begin
  FFI_emulator_do_fence();

  PC = PC + 4;
  return Retired();
end
