func Step()
begin
  if IsReset() then
    Reset();
  end

  let instruction = InstructionFetch(PC);
  Execute(instruction);
end

// export
func ASL_Step()
begin
  Step();
end
