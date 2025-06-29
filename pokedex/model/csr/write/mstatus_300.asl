MSTATUS_MIE = value[3];
MSTATUS_MPIE = value[7];

// level 2 is reserved, so we just keep the previous privilege mode
// todo: support writing S mode when implementing supervisor mode;
if value[12:11] == '11' then
  MSTATUS_MPP_BITS = value[12:11];
end

return Retired();
