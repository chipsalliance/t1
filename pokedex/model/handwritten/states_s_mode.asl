var SSTATUS_SIE : bit;
var SSTATUS_SPIE : bit;
var SSTATUS_SPP : bit;
var SSTATUS_SUM : bit;
var SSTATUS_MXR : bit;

func resetArchStateSMode()
begin
  SSTATUS_SIE = 0;
  SSTATUS_SPIE = 0;
  SSTATUS_SPP = 0;
  SSTATUS_SUM = 0;
  SSTATUS_MXR = 0;
end
