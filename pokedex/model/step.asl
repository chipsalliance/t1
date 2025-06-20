func Step()
begin
  let instruction = FFI_instruction_fetch(PC);
  Execute(instruction);
end

// export
func ASL_Step()
begin
  Step();
end
