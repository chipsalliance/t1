func Execute_C_EBREAK(instruction: bits(16)) => Result
begin
  return ExceptionEbreak();
end
