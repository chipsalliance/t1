func Execute_ECALL(instruction: bits(32)) => Result
begin
  // FIXME : depend on current privilege
  return Exception(CAUSE_MACHINE_ECALL, Zeros(32));
end
