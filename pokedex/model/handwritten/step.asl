func Step() => FFI_StepResult
begin
  let has_interrupt : boolean = CheckInterrupt();
  if has_interrupt then
    // interrupt alreay handled in checkInterrupt
    return FFI_StepResult {
      code = FFI_STEPCODE_INTERRUPT,
      inst = Zeros(32)
    };
  end

  let current_pc = PC;

  let least_significant_half : FFI_ReadResult(16) = FFI_instruction_fetch_half(current_pc);
  if !least_significant_half.success then
    FFI_debug_print("instruction fetch LSH fail");
    handleException(
      current_pc,
      Zeros(XLEN),
      XCPT_CODE_FETCH_ACCESS,
      current_pc
    );
    return FFI_StepResult {
      code = FFI_STEPCODE_FETCH_XCPT,
      inst = Zeros(32)
    };
  end

  var exec_result : Result;
  
  if least_significant_half.data[1:0] == '11' then
    // execute non-compressed instruction
    let most_significant_half : FFI_ReadResult(16) = FFI_instruction_fetch_half(current_pc + 2);
    if !most_significant_half.success then
      FFI_debug_print("instruction fetch MSH fail");
      handleException(
        current_pc,       // MEPC points to current pc
        Zeros(XLEN),
        XCPT_CODE_FETCH_ACCESS,
        current_pc + 2    // MTVAL points to the portion of fetch
      );
      return FFI_StepResult {
        code = FFI_STEPCODE_FETCH_XCPT,
        inst = Zeros(32)
      };
    end

    let instruction : bits(32) = [most_significant_half.data, least_significant_half.data];

    FFI_debug_issue(PC, instruction);
    exec_result = DecodeAndExecute(instruction);

    if exec_result.is_ok then
      return FFI_StepResult {
        code = FFI_STEPCODE_INST_COMMIT,
        inst = instruction
      };
    else
      // if the inst is not commited, PC should not be modified
      assert(current_pc == PC);

      handleException(
        current_pc,
        ZeroExtend(instruction, 32),
        exec_result.cause,
        exec_result.payload
      );
      return FFI_StepResult {
        code = FFI_STEPCODE_INST_XCPT,
        inst = instruction
      };
    end
  else
    // execute compressed instruction

    let instruction: bits(16) = least_significant_half.data;
    FFI_debug_issue_c(PC, instruction);
    exec_result = DecodeAndExecute_CEXT(instruction);

    if exec_result.is_ok then
      return FFI_StepResult {
        code = FFI_STEPCODE_INST_C_COMMIT,
        inst = ZeroExtend(instruction, 32)
      };
    else
      // if the inst is not commited, PC should not be modified
      assert(current_pc == PC);

      handleException(
        current_pc,
        ZeroExtend(instruction, 32),
        exec_result.cause,
        exec_result.payload
      );
      return FFI_StepResult {
        code = FFI_STEPCODE_INST_C_XCPT,
        inst = ZeroExtend(instruction, 32)
      };
    end
  end
end

func handleException(pc: bits(XLEN), inst: bits(XLEN), cause: integer{0..31}, payload: bits(XLEN))
begin
  let mcause: bits(XLEN) = ['0', cause[30:0]];
  var mtval: bits(XLEN) = Zeros(XLEN);

  case cause of
    when {XCPT_CODE_ILLEGAL_INSTRUCTION} => begin
      mtval = inst;
    end

    when {
      XCPT_CODE_FETCH_ACCESS,
      XCPT_CODE_MISALIGNED_LOAD,
      XCPT_CODE_LOAD_ACCESS,
      XCPT_CODE_MISALIGNED_STORE,
      XCPT_CODE_STORE_ACCESS,
      XCPT_CODE_FETCH_PAGE_FAULT,
      XCPT_CODE_LOAD_PAGE_FAULT,
      XCPT_CODE_STORE_PAGE_FAULT
    } => begin
      mtval = payload;
    end

    // FIXME: when asl2c supports exhanstion check,
    // replace otherwise to an explicit list.
    //
    // For unspecified cause, MTVAL will be written zero
    otherwise => begin end
  end

  // mepc
  MEPC = PC;
  // mcause
  MCAUSE = mcause;
  // mtval
  MTVAL = mtval;
  // mstatus
  MSTATUS_MPIE = MSTATUS_MIE;
  MSTATUS_MIE = '0';
  MSTATUS_MPP = CURRENT_PRIVILEGE;

  CURRENT_PRIVILEGE = PRIV_MODE_M;

  PC = [MTVEC_BASE, '00'];
end

func checkInterrupt() => boolean
begin
  // if machine mode interrupt bit is not enabled, just return
  if MSTATUS_MIE == '0' then
    return FALSE;
  end

  let machine_trap_timer : bit = getExternal_MTIP AND MTIE;
  if machine_trap_timer == '1' then
    handleInterrupt(INTR_CODE_MTI);
    return TRUE;
  end

  let machine_trap_external : bit = getExternal_MEIP AND MEIE;
  if machine_trap_external == '1' then
    handleInterrupt(INTR_CODE_MEI);
    return TRUE;
  end

  return FALSE;
end

func handleInterrupt(intr_code : integer{3,7,11})
begin
  // mepc
  MEPC = ['1', intr_code[30:0]];
  // mcause
  MCAUSE = ['0', intr_code[30:0]];
  // mtval is written when a trap (including interrupt) is taken
  MTVAL = Zeros(32);
  // mstatus
  MSTATUS_MPIE = MSTATUS_MIE;
  MSTATUS_MIE = '0';
  MSTATUS_MPP = CURRENT_PRIVILEGE;

  CURRENT_PRIVILEGE = PRIV_MODE_M;

  case MTVEC_MODE of
    when MTVEC_MODE_DIRECT =>
      PC = [MTVEC_BASE, '00'];

    when MTVEC_MODE_VECTORED =>
      PC = [MTVEC_BASE, '00'] + (4 * intr_code);
  end
end

// export
func ASL_Step() => FFI_StepResult
begin
  return Step();
end

record FFI_StepResult {
  code: bits(8);
  inst: bits(32);
};

let FFI_STEPCODE_FETCH_XCPT = 1[7:0];
let FFI_STEPCODE_INST_XCPT = 2[7:0];
let FFI_STEPCODE_INST_COMMIT = 4[7:0];
let FFI_STEPCODE_INST_C_XCPT = 10[7:0];
let FFI_STEPCODE_INST_C_COMMIT = 12[7:0];
let FFI_STEPCODE_INTERRUPT = 16[7:0];
