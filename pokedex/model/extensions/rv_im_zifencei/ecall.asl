func Execute_ECALL(instruction: bits(32)) => Result
begin
  return ExceptionEcall(CURRENT_PRIVILEGE);
end
