constant XLEN : integer = 32;


constant PRIV_MODE_MOST : PrivMode = PRIV_MODE_M;
constant PRIV_MODE_LEAST : PrivMode = PRIV_MODE_U;

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
  MSTATUS_VS = '00';

  MEIE = '0';
  MTIE = '0';

  __resetFPStates();
end

////////////////////////////////
//// State Type Definitions ////
////////////////////////////////

type XRegIdx of integer{0..31};
type FRegIdx of integer{0..31};

enumeration PrivMode {
  PRIV_MODE_M,
  PRIV_MODE_S,
  PRIV_MODE_U
};

enumeration MtvecMode {
  MTVEC_MODE_DIRECT,
  MTVEC_MODE_VECTORED
};

////////////////////////
//// Access Helpers ////
////////////////////////

/// The *Program Counter (PC)* is a fundamental architectural state that stores
/// the address of the current instruction. It is defined as a 32-bit variable.
///
/// To access the program counter, developers should utilize the public `PC`
/// interface rather than the internal `__PC` variable. The `PC` setter enforces
/// validity checks, ensuring that every address stored remains properly aligned
/// (specifically, asserting that the least significant bit is zero).
///
/// *Example:*
///
/// ```asl
/// // Read is always OK
/// let result = FFI_fetch_instruction(PC);
///
/// // Write OK
/// PC = 0x8000_0000[31:0];
/// // Write Panic
/// PC = 0x8000_0003[31:0];
/// ```
getter PC => bits(32) begin return __PC; end

setter PC = npc : bits(32)
begin
  assert npc[0] == '0';
  __PC = npc;
end

/// The ASL Model currently supports the *RV32I* ISA. This defines 32 General
/// Purpose Registers (GPRs), each 32 bits wide (`XLEN=32`). Since register `x0`
/// is hardwired to `0` by definition, we optimize storage by allocating only 31
/// registers (`x1` through `x31`) within the model.
///
/// Registers is stored in an array of 31 32-bits elements by an internal variable
/// `__GPR`. Developers should exclusively use the public `X[i]` interface rather
/// than accessing the `__GPR` variable directly.
///
/// The `X[i]` interface performs three critical tasks:
///
/// - It handles the index offset (mapping logical register `x1` to array index `0`).
/// - It enforces the hardwired zero behavior for `x0`.
/// - It signals the FFI hook whenever a write operation occurs.
///
/// *Example:*
///
/// ```asl
/// assert(X[0] == Zeros(32));
///
/// X[5] = 0x8000_0000[31:0];
/// assert(X[5] == '10000000000000000000000000000000');
/// ```
getter X[i: XRegIdx] => bits(32)
begin
  if i == 0 then
    return Zeros(32);
  else
    return __GPR[i - 1];
  end
end

setter X[i: XRegIdx] = value : bits(32)
begin
  if i > 0 then
    __GPR[i - 1] = value;

    // notify emulator that a write to GPR occur
    FFI_write_GPR_hook(i as bits(5));
  end
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

func is_valid_privilege(value : bits(2)) => boolean
begin
  return value != '10';
end

func IsPrivAtLeast_M() => boolean
begin
  return IsPrivAtLeast(PRIV_MODE_M);
end

func IsPrivAtLeast(expect : PrivMode) => boolean
begin
  case expect of
    when PRIV_MODE_M => return CURRENT_PRIVILEGE == PRIV_MODE_M;
    when PRIV_MODE_S => return CURRENT_PRIVILEGE == PRIV_MODE_M || CURRENT_PRIVILEGE == PRIV_MODE_S;
    when PRIV_MODE_U => return TRUE;
  end
end

func privModeToBits(priv : PrivMode, N: integer) => bits(N)
begin
  case priv of
    when PRIV_MODE_M => return ZeroExtend('11', N);
    when PRIV_MODE_S => return ZeroExtend('01', N);
    when PRIV_MODE_U => return ZeroExtend('00', N);
  end
end

func privModeFromBits(value : bits(N)) => PrivMode
begin
  let mode : integer = UInt(value);
  case mode of
    when 3 => return PRIV_MODE_M;
    when 1 => return PRIV_MODE_S;
    when 0 => return PRIV_MODE_U;
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
