MSTATUS_MIE = value[MSTATUS_MIE_IDX];
MSTATUS_MPIE = value[MSTATUS_MPIE_IDX];

// we just keep the previous privilege mode if value invalid
let new_mpp = value[MSTATUS_MPP_HI:MSTATUS_MPP_LO];
if is_valid_privilege(new_mpp) then
  MSTATUS_MPP_BITS = new_mpp;
end

MSTATUS_VS = value[MSTATUS_VS_HI:MSTATUS_VS_LO];
MSTATUS_FS = value[MSTATUS_FS_HI:MSTATUS_FS_LO];

return Retired();
