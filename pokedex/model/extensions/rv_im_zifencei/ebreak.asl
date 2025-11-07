func Execute_EBREAK(instruction: bits(32)) => Result
begin
  return Exception(CAUSE_BREAKPOINT, Zeros(32));
end
