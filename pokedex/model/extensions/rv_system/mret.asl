if CURRENT_PRIVILEGE != PRIV_MACHINE_MODE then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, instruction);
end

PC = MEPC;

MSTATUS_MIE = MSTATUS_MPIE;
// define in privilege spec chapter mstatus
MSTATUS_MPIE = '1';
CURRENT_PRIVILEGE = MSTATUS_MPP;
MSTATUS_MPP = PRIV_MACHINE_MODE;

return Retired();
