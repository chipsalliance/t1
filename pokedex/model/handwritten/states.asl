constant XLEN : integer = 32;

constant FLEN : integer = 32;



constant PRIV_MODE_MOST : PrivMode = PRIV_MODE_M;
constant PRIV_MODE_LEAST : PrivMode = PRIV_MODE_M;

////////////////
//// CONFIG ////
////////////////

// Configs are fixed in hardware, but we make it configurable
// to avoid recompile the model every time.
//
// Shall not modify them after the first step.

var CFG_MHARTID : bits(32);
var CFG_MVENDORID : bits(32);
var CFG_MARCHID : bits(32);
var CFG_MIMPID : bits(32);
var CFG_MCONFIGPTR : bits(32);

func initConfigDefault()
begin
  CFG_MHARTID = Zeros(32);
  CFG_MVENDORID = Zeros(32);
  CFG_MARCHID = Zeros(32);
  CFG_MIMPID = Zeros(32);
  CFG_MCONFIGPTR = Zeros(32);
end

/////////////////////
//// Arch States ////
/////////////////////

// TODO : incorporate with states_v.asl

var __PC : bits(32);
var __GPR : array[31] of bits(32);

var __FPR : array[32] of bits(32);
var FRM : bits(3);
var FFLAGS : bits(5);

var CURRENT_PRIVILEGE : PrivMode;

var MSCRATCH : bits(32);
var __MEPC : bits(32);
var MCAUSE : bits(32);
var MTVAL : bits(32);

var MTVEC_BASE : bits(30);
var MTVEC_MODE : MtvecMode;

var MSTATUS_MIE : bit;
var MSTATUS_MPIE : bit;
var MSTATUS_MPP : PrivMode;
var MSTATUS_FS : bits(2);
var MSTATUS_VS : bits(2);

// MIE csr
var MEIE : bit;
var MTIE : bit;

func resetArchStateDefault()
begin
  __PC = Zeros(32);

  for i = 0 to 30 do
    __GPR[i] = Zeros(32);
  end

  for i = 0 to 31 do
    __FPR[i] = Zeros(32);
  end
  FRM = Zeros(3);
  FFLAGS = Zeros(5);

  CURRENT_PRIVILEGE = PRIV_MODE_M;

  MSCRATCH = Zeros(32);
  __MEPC = Zeros(32);
  MCAUSE = Zeros(32);
  MTVAL = Zeros(32);

  MTVEC_BASE = Zeros(30);
  MTVEC_MODE = MTVEC_MODE_VECTORED;

  MSTATUS_MIE = '0';
  MSTATUS_MPIE = '0';
  MSTATUS_MPP = PRIV_MODE_LEAST;
  MSTATUS_FS = '00';
  MSTATUS_VS = '00';

  MEIE = '0';
  MTIE = '0';
end

////////////////////////////////
//// State Type Definitions ////
////////////////////////////////

type XRegIdx of integer{0..31};
type FRegIdx of integer{0..31};

// TODO : remove deprecated aliases
type XREG_TYPE of integer{0..31};
type freg_index of integer{0..31};

enumeration PrivMode {
  PRIV_MODE_M
};

enumeration MtvecMode {
  MTVEC_MODE_DIRECT,
  MTVEC_MODE_VECTORED
};

////////////////////////
//// Access Helpers ////
////////////////////////

getter PC => bits(32) begin return __PC; end

setter PC = npc : bits(32)
begin
  assert npc[0] == '0';
  __PC = npc;
end

getter X[i : XREG_TYPE] => bits(32)
begin
  if i == 0 then
    return Zeros(32);
  else
    return __GPR[i - 1];
  end
end

setter X[i : XREG_TYPE] = value : bits(32)
begin
  if i > 0 then
    __GPR[i - 1] = value;

    // notify emulator that a write to GPR occur
    FFI_write_GPR_hook(i as bits(5));
  end
end

getter F[i : freg_index] => bits(32)
begin
  return __FPR[i];
end

// Instruction should use the `F` getter to update value, eg. `F[5] = Zeros(32);`
setter F[i : FRegIdx] = value : bits(32)
begin
  __FPR[i] = value;

  // notify emulator that a write to FPR occur
  FFI_write_FPR_hook(i as bits(5));

  makeDirty_FS();
end

getter MEPC => bits(32)
begin
  return __MEPC;
end

setter MEPC = pc : bits(32)
begin
  assert pc[0] == '0';
  // we don't support runtime switch off "C" extension so there is
  // no need for misalign check at 1 bit.
  __MEPC = pc;
end

getter MTVEC_MODE_BITS => bits(2)
begin
  case MTVEC_MODE of
    when MTVEC_MODE_DIRECT => return '00';
    when MTVEC_MODE_VECTORED => return '01';
  end
end

setter MTVEC_MODE_BITS = value : bits(2)
begin
  case value of
    when '00' => MTVEC_MODE = MTVEC_MODE_DIRECT;
    when '01' => MTVEC_MODE = MTVEC_MODE_VECTORED;
    otherwise => assert FALSE;
  end
end

getter MSTATUS_MPP_BITS => bits(2)
begin
  return privModeToBits(MSTATUS_MPP, 2);
end

setter MSTATUS_MPP_BITS = value : bits(2)
begin
  MSTATUS_MPP = privModeFromBits(value);
end

getter getExternal_MEIP => bit
begin
  return FFI_machine_external_interrupt_pending();
end

getter getExternal_MTIP => bit
begin
  return FFI_machine_time_interrupt_pending();
end

///////////////////////////
//// Utility Functions ////
///////////////////////////

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

// TODO : deprecated, use resolveFrmDynamic instead
func RM_from_bits(rm_bits : bits(3)) => RM_Result
begin
  let (rm, valid) = resolveFrmDynamic(rm_bits);
  return RM_Result {
    mode = rm,
    valid = valid
  };
end

// accure fflags to FFLAGS 
func accureFFlags(fflags: bits(5))
begin
  FFLAGS = FFLAGS OR fflags;

  if !IsZero(fflags) then
    logWrite_FCSR();
  end
end

// deprecated
// set fflags base on the softfloat global exception flag
func set_fflags_from_softfloat(softfloat_xcpt : integer)
begin
  if softfloat_xcpt == 0 then
    return;
  end

  FFLAGS = FFLAGS OR softfloat_xcpt[4:0];

  logWrite_FCSR();
end


func is_valid_privilege(value : bits(2)) => boolean
begin
  return value == '11';
end

func IsPrivAtLeast_M() => boolean
begin
  return TRUE;
end

func privModeToBits(priv : PrivMode, N: integer) => bits(N)
begin
  case priv of
    when PRIV_MODE_M => return ZeroExtend('11', N);
  end
end

func privModeFromBits(value : bits(N)) => PrivMode
begin
  let mode : integer = UInt(value);
  case mode of
    when 3 => return PRIV_MODE_M;
    otherwise => assert FALSE;
  end
end

// MIP and MIE
//
// Since in current implementation, only machine mode will be supported, there
// is no LCOFIP, SEIP, STIP and SSIP states.
//
// MEIP is controlled by interrupt controller, no states will be stored
// and appeared to be read-only, we will use a FFI function for it.
//
// MTIP is controlled by external time interrupt controller in this model, no states will be stored.
//
// MSIP and MSIE is not implemented since we have support only one hart.
let MACHINE_TIMER_INTERRUPT = 7;
let MACHINE_EXTERNAL_INTERRUPT = 11;

// export to simulator
func ASL_ResetConfigAndState()
begin
  initConfigDefault();
  resetArchStateDefault();
end

// export to simulator
func ASL_ResetState()
begin
  resetArchStateDefault();
end
