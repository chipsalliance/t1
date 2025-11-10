func Execute_EBREAK(instruction: bits(32)) => Result
begin
  return ExceptionEbreak();
end
