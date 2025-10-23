constant XLEN : integer = 32;

// Program Counter
var __PC : bits(32);

getter PC => bits(32) begin return __PC; end

setter PC = npc : bits(32)
begin
  assert npc[0] == '0';
  __PC = npc;
end

// General Propose Register
var __GPR : array[31] of bits(32);

// Don't use the setter "X" here to avoid invoke the FFI_ hook.
// Non-software reset should be operated quietly.
func __ResetGPR()
begin
  for i = 0 to 30 do
    __GPR[i] = Zeros(32);
  end
end

type XREG_TYPE of integer{0..31};

// Global getter setter functions for register: developers should never use the private __GPR variable
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
    FFI_write_GPR_hook(i, value);
  end
end

enumeration PRIVILEGE_LEVEL {
  PRIV_MACHINE_MODE
};

func is_valid_privilege(value : bits(2)) => boolean
begin
  return value == '11';
end

func __PrivLevelToBits(priv : PRIVILEGE_LEVEL, N: integer) => bits(N)
begin
  case priv of
    when PRIV_MACHINE_MODE => return ZeroExtend('11', N);
  end
end

func __BitsToPrivLevel(value : bits(N)) => PRIVILEGE_LEVEL
begin
  let mode : integer = UInt(value);
  case mode of
    when 3 => return PRIV_MACHINE_MODE;
    otherwise => assert FALSE;
  end
end

// record current privilege level
var CURRENT_PRIVILEGE : PRIVILEGE_LEVEL;

getter CURRENT_PRIVILEGE_BITS => bits(2)
begin
  return __PrivLevelToBits(CURRENT_PRIVILEGE, 2);
end

setter CURRENT_PRIVILEGE_BITS = value : bits(2)
begin
  CURRENT_PRIVILEGE = __BitsToPrivLevel(value);
end

func __ResetCurrentPrivilege()
begin
  CURRENT_PRIVILEGE = PRIV_MACHINE_MODE;
end


/// There are only two possible mtvec mode
enumeration MTVEC_MODE_TYPE {
  MTVEC_DIRECT_MODE,
  MTVEC_VECTORED_MODE
};

var MTVEC_BASE : bits(30);
var MTVEC_MODE : MTVEC_MODE_TYPE;

getter MTVEC_MODE_BITS => bits(2)
begin
  case MTVEC_MODE of
    when MTVEC_DIRECT_MODE => return '00';
    when MTVEC_VECTORED_MODE => return '01';
  end
end

setter MTVEC_MODE_BITS = value : bits(2)
begin
  case value of
    when '00' => MTVEC_MODE = MTVEC_DIRECT_MODE;
    when '01' => MTVEC_MODE = MTVEC_VECTORED_MODE;
    otherwise => assert FALSE;
  end
end

func __ResetMTVEC()
begin
  MTVEC_BASE = Zeros(30);
  MTVEC_MODE = MTVEC_DIRECT_MODE;
end


/// mtval can hold any value, it is not possible to have constraint
var MTVAL : bits(32);
func __ResetMTVAL()
begin
  MTVAL = Zeros(32);
end


/// mie and mpie is by default a switch value, no need to add extra constraint
let MSTATUS_IDX : bits(12) = 0x300[11:0];
let MSTATUS_H_IDX : bits(12) = 0x310[11:0];
let MSTATUS_MIE_IDX = 3;
let MSTATUS_MPIE_IDX = 7;
let MSTATUS_MPP_HI = 12;
let MSTATUS_MPP_LO = 11;
let MSTATUS_FS_HI = 14;
let MSTATUS_FS_LO = 13;

var MSTATUS_MIE : bit;
var MSTATUS_MPIE : bit;
var MSTATUS_MPP : PRIVILEGE_LEVEL;
var MSTATUS_FS : bits(2);

getter MSTATUS_MPP_BITS => bits(2)
begin
  return __PrivLevelToBits(MSTATUS_MPP, 2);
end

setter MSTATUS_MPP_BITS = value : bits(2)
begin
  MSTATUS_MPP = __BitsToPrivLevel(value);
end

func __ResetMSTATUS()
begin
  MSTATUS_MIE = '0';
  MSTATUS_MPIE = '0';
  MSTATUS_MPP = PRIV_MACHINE_MODE;
  MSTATUS_FS = '00';
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

getter MEIP => bit
begin
  return FFI_machine_external_interrupt_pending();
end

getter MTIP => bit
begin
  return FFI_machine_time_interrupt_pending();
end

var MEIE : bit;
var MTIE : bit;

func __ResetMIE()
begin
  MEIE = '0';
  MTIE = '0';
end

var __MEPC : bits(32);
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

func __ResetMEPC()
begin
  __MEPC = Zeros(32);
end


var MCAUSE_IS_INTERRUPT : boolean;

getter MCAUSE_IS_INTERRUPT_BIT => bit
begin
  if MCAUSE_IS_INTERRUPT then
    return '1';
  else
    return '0';
  end
end

setter MCAUSE_IS_INTERRUPT_BIT = b : bit
begin
  MCAUSE_IS_INTERRUPT = (b == '1');
end

var MCAUSE_XCPT_CODE : bits(31);

func __ResetMCAUSE()
begin
  MCAUSE_IS_INTERRUPT = FALSE;
  MCAUSE_XCPT_CODE = Zeros(31);
end


// General States Reset
func Reset()
begin
  __ResetCurrentPrivilege();

  __ResetGPR();

  __ResetVectorState();

  __reset_f_state();

  __ResetMTVEC();
  __ResetMTVAL();
  __ResetMSTATUS();
  __ResetMIE();
  __ResetMEPC();
  __ResetMCAUSE();
end

// export to simulator
func ASL_Reset()
begin
  Reset();
end
