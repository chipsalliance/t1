func Execute_MRET(instruction: bits(32)) => Result
begin
  if !IsPrivAtLeast_M() then
    return IllegalInstruction();
  end

  PC = MEPC;

  CURRENT_PRIVILEGE = MSTATUS_MPP;

  MSTATUS_MPP = PRIV_MODE_LEAST;
  // TODO: set MPRV when U and S is supported
  MSTATUS_MIE = MSTATUS_MPIE;
  MSTATUS_MPIE = '1';

  logWrite_MSTATUS();

  return Retired();
end
