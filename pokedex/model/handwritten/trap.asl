func TrapException(cause : integer, trap_value : bits(32))
begin
  // mepc
  MEPC = PC;

  // mcause
  MCAUSE_IS_INTERRUPT = FALSE;
  // convert integer to 31bits length bit vector
  MCAUSE_XCPT_CODE = asl_cvt_int_bits(cause, 31);

  // mstatus
  MSTATUS_MPIE = MSTATUS_MIE;
  MSTATUS_MIE = '0';
  MSTATUS_MPP = CURRENT_PRIVILEGE;

  // mtval
  MTVAL = trap_value;

  PC = [ MTVEC_BASE, '00' ];
end

// we only support limited machien mode interrupt now
func TrapInterrupt(interrupt_code : integer{3,7,11})
begin

  // save current context
  MSTATUS_MPIE = MSTATUS_MIE;
  MSTATUS_MIE = '0';
  MSTATUS_MPP = CURRENT_PRIVILEGE;
  MEPC = PC;

  CURRENT_PRIVILEGE = PRIV_MACHINE_MODE;

  MCAUSE_IS_INTERRUPT = TRUE;
  MCAUSE_XCPT_CODE = asl_cvt_int_bits(interrupt_code, 31);

  if MTVEC_MODE == MTVEC_DIRECT_MODE then
    PC = [ MTVEC_BASE, '00' ];
  else
    PC = [ MTVEC_BASE, '00' ] + (4 * interrupt_code);
  end
end

func CheckInterrupt() => boolean
begin
  // if machine mode interrupt bit is not enabled, just return
  if MSTATUS_MIE == '0' then
    return FALSE;
  end

  let machine_trap_timer : bit = MTIP AND MTIE;
  if machine_trap_timer == '1' then
    TrapInterrupt(MACHINE_TIMER_INTERRUPT);
    return TRUE;
  end

  let machine_trap_external : bit = MEIP AND MEIE;
  if machine_trap_external == '1' then
    TrapInterrupt(MACHINE_EXTERNAL_INTERRUPT);
    return TRUE;
  end

  return FALSE;
end
