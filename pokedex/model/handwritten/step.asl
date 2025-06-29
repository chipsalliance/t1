func Step()
begin
  let has_interrupt : boolean = CheckInterrupt();
  if has_interrupt then
    return;
  end

  let fetch_result : FFI_ReadResult(32) = FFI_instruction_fetch(PC);
  if !fetch_result.success then
    FFI_print_str("instruction fetch fail");
    TrapException(CAUSE_FETCH_ACCESS, PC);
    return;
  end

  let exec_result : Result = DecodeAndExecute(fetch_result.data);
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
