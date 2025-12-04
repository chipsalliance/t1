func Todo(message: string)
begin
  FFI_debug_print(message);
  Unreachable();
end

func DivCeil(x: integer, y: integer) => integer
begin
  if x MOD y == 0 then
    return x DIVRM y;
  else
    return (x DIVRM y) + 1;
  end
end
