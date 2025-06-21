func Step()
begin
  if FFI_is_reset() then
    Reset();
  end

  let instruction = FFI_instruction_fetch(PC);
  Execute(instruction);
end

// export
func ASL_Step()
begin
  Step();
end
