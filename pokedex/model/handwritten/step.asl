func Step()
begin
  let has_interrupt : boolean = CheckInterrupt();
  if has_interrupt then
    return;
  end

  let least_significant_half : FFI_ReadResult(16) = FFI_instruction_fetch_half(PC);
  if !least_significant_half.success then
    FFI_print_str("instruction fetch LSH fail");
    TrapException(CAUSE_FETCH_ACCESS, PC);
    return;
  end

  var exec_result : Result;
  // PC will get modified after execution, thus log will have incorrect PC offset
  let current_pc = PC;
  if least_significant_half.data[1:0] == '11' then
    let most_significant_half : FFI_ReadResult(16) = FFI_instruction_fetch_half(PC + 2);
    if !most_significant_half.success then
      FFI_print_str("instruction fetch MSH fail");
      TrapException(CAUSE_FETCH_ACCESS, PC);
      return;
    end

    let instruction : bits(32) = [most_significant_half.data, least_significant_half.data];
    exec_result = DecodeAndExecute(instruction);

    ffi_commit_insn(current_pc, instruction, FALSE);
  else
    exec_result = DecodeAndExecute_CEXT(least_significant_half.data);

    ffi_commit_insn(current_pc, ZeroExtend(least_significant_half.data, 32), TRUE);
  end

  if !exec_result.is_ok then
    TrapException(exec_result.cause, exec_result.value);

    if exec_result.cause == CAUSE_BREAKPOINT then
      FFI_ebreak();
    end
  end
end

// export
func ASL_Step()
begin
  Step();
end
