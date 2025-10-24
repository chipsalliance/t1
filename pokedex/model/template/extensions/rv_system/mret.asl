if CURRENT_PRIVILEGE != PRIV_MACHINE_MODE then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, instruction);
end

PC = MEPC;

let prev_status = ReadCSR(MSTATUS_IDX);
assert prev_status.is_ok;
// TODO: set MPRV when U and S is supported
var pval = prev_status.value;

pval[MSTATUS_MIE_IDX] = pval[MSTATUS_MPIE_IDX];
pval[MSTATUS_MPIE_IDX] = '1';

CURRENT_PRIVILEGE = PRIV_MACHINE_MODE; // TODO: in semantics this should be last MPP
pval[MSTATUS_MPP_HI:MSTATUS_MPP_LO] = '11'; // TODO: we have no U mode, reset to M mode

// Write
let result1 = WriteCSR(MSTATUS_IDX, pval);
assert result1.is_ok;
// NOTES: in semantics here we should not use Zeros but use upper 32-bit of the pval.
let result2 = WriteCSR(MSTATUS_H_IDX, Zeros(32));
assert result2.is_ok;

return Retired();
