// write to 00 and 01 is valid, write to 10 and 11 is no-op
if value[1] == '0' then
  MTVEC_MODE_BITS = value[1:0];
end

MTVEC_BASE = value[31:2];

return Retired();
