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

  PC = [ MTVEC_BASE, '00' ] + (4 * interrupt_code);
end

func CheckInterrupt()
begin
  // update mip csr
  let result = WriteCSR(asl_cvt_int_bits(MIP_ADDR, 12), FFI_fetch_pending_interrupt());
  assert result.is_ok;

  // if machine mode interrupt bit is not enabled, just return
  if MSTATUS_MIE == '0' then
    return;
  end

  let machine_trap_software : bit = MIP[MIP_MSIP] AND MIE[MIE_MSIE];
  if machine_trap_software == '1' then
    TrapInterrupt(MIP_MSIP);
    return;
  end

  let machine_trap_timer : bit = MIP[MIP_MTIP] AND MIE[MIE_MTIE];
  if machine_trap_timer == '1' then
    TrapInterrupt(MIP_MTIP);
    return;
  end

  let machine_trap_external : bit = MIP[MIP_MEIP] AND MIE[MIE_MEIE];
  if machine_trap_external == '1' then
    TrapInterrupt(MIP_MEIP);
    return;
  end

  // if no pending interrupt then return
end
