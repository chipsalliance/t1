constant FLEN : integer = 32;

var FRM : bits(3);
var FFLAGS : bits(5);
var MSTATUS_FS : bits(2);
var __FPR : array[32] of bits(32);

func __resetFPStates()
begin
  FRM = Zeros(3);
  FFLAGS = Zeros(5);
  MSTATUS_FS = '00';

  for i = 0 to 31 do
    __FPR[i] = Zeros(32);
  end
end

getter F[i: FRegIdx] => bits(32)
begin
  return __FPR[i];
end

// Instruction should use the `F` getter to update value, eg. `F[5] = Zeros(32);`
setter F[i: FRegIdx] = value : bits(32)
begin
  __FPR[i] = value;

  // notify emulator that a write to FPR occur
  FFI_write_FPR_hook(i as bits(5));

  makeDirty_FS();
end

// RM represents floating-point rounding mode
enumeration RM {
  // Round to Nearest, ties to Even
  RM_RNE,
  // Round towards Zero
  RM_RTZ,
  // Round Down, towards negative infinity
  RM_RDN,
  // Round Up, towards positive infinity
  RM_RUP,
  // Round to Neareast, ties to Max Magnitude
  RM_RMM
};

// RM_Result represent the rounding mode bits decode result
record RM_Result {
  mode : RM;
  valid : boolean;
};

// Decode three bits to `RM` rounding mode enumeration.
// '111' is treated as an invalid mode
func decodeFrmStatic(rm_bits: bits(3)) => (RM, boolean)
begin
  case rm_bits of
    when '000' => return (RM_RNE, TRUE);
    when '001' => return (RM_RTZ, TRUE);
    when '010' => return (RM_RDN, TRUE);
    when '011' => return (RM_RUP, TRUE);
    when '100' => return (RM_RMM, TRUE);

    otherwise => return (RM_RNE, FALSE);
  end
end

// Decode three bits to `RM` rounding mode enumeration.
// '111' is treated as an invalid mode
func resolveFrmDynamic(rm_bits : bits(3)) => (RM, boolean)
begin
  if rm_bits == '111' then
    let (rm, valid) = decodeFrmStatic(FRM);
    return (rm, valid);
  else
    let (rm, valid) = decodeFrmStatic(rm_bits);
    return (rm, valid);
  end
end

// accure fflags to FFLAGS 
func accureFFlags(fflags: bits(5))
begin
  FFLAGS = FFLAGS OR fflags;

  if !IsZero(fflags) then
    logWrite_FCSR();
  end
end

