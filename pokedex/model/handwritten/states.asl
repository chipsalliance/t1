// Program Counter
var __pc : bits(32);

getter PC => bits(32) begin return __pc; end

setter PC = pc : bits(32)
begin
  assert pc[0] == '0';
  // todo: test IALIGN before assert after C extension implmented
  assert pc[1] == '0';
  __pc = pc;
end

// General Propose Register
var __GPR : array[31] of bits(32);

// Don't use the setter "X" here to avoid invoke the FFI_ hook.
// Non-software reset should be operated quietly.
func __Reset_GPR()
begin
  for i = 0 to 30 do
    __GPR[i] = Zeros(32);
  end
end

// Global getter setter functions for register: developers should never use the private __GPR variable
getter X[i : integer {0..31}] => bits(32)
begin
  if i == 0 then
    return Zeros(32);
  else
    return __GPR[i - 1];
  end
end

setter X[i : integer {0..31}] = value : bits(32)
begin
  if i > 0 then
    __GPR[i - 1] = value;
  end

  // notify emulator that a write to GPR occur
  FFI_write_GPR_hook(i, value);
end

// -------------------
// Architecture States
// -------------------
enumeration PRIVILEGE_LEVEL {
  PRIV_MACHINE_MODE
};

func __priv_lvl_to_bits(priv : PRIVILEGE_LEVEL, N: integer) => bits(N)
begin
  case priv of
    when PRIV_MACHINE_MODE => return ZeroExtend('11', N);
  end
end

func __bits_to_priv_lvl(value : bits(N)) => PRIVILEGE_LEVEL
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
  return __priv_lvl_to_bits(CURRENT_PRIVILEGE, 2);
end

setter CURRENT_PRIVILEGE_BITS = value : bits(2)
begin
  CURRENT_PRIVILEGE = __bits_to_priv_lvl(value);
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

func __Reset_MTVEC()
begin
  MTVEC_BASE = Zeros(30);
  MTVEC_MODE = MTVEC_DIRECT_MODE;
end


/// mtval can hold any value, it is not possible to have constraint
var MTVAL : bits(32);
func __Reset_MTVAL()
begin
  MTVAL = Zeros(32);
end


/// mie and mpie is by default a switch value, no need to add extra constraint
var MSTATUS_MIE : bit;
var MSTATUS_MPIE : bit;
var MSTATUS_MPP : PRIVILEGE_LEVEL;

getter MSTATUS_MPP_BITS => bits(2)
begin
  return __priv_lvl_to_bits(MSTATUS_MPP, 2);
end

setter MSTATUS_MPP_BITS = value : bits(2)
begin
  MSTATUS_MPP = __bits_to_priv_lvl(value);
end

func __Reset_MSTATUS()
begin
  MSTATUS_MIE = '0';
  MSTATUS_MPIE = '0';
  MSTATUS_MPP = PRIV_MACHINE_MODE;
end


let MIP_ADDR : integer = 0x344;

var __mip : bits(32);
let MIP_MSIP = 3;
let MIP_MTIP = 7;
let MIP_MEIP = 11;

getter MIP => bits(32)
begin
  assert __mip[31:16] == Zeros(16);
  assert __mip[15:12] == '0000';
  assert __mip[10:8] == '000';
  assert __mip[6:4] == '000';
  assert __mip[2:0] == '000';

  return __mip;
end

setter MIP = value : bits(32)
begin
  __mip = value;
end

func __Reset_MIP()
begin
  MIP = Zeros(32);
end


var __mie : bits(32);
let MIE_MSIE = 3;
let MIE_MTIE = 7;
let MIE_MEIE = 11;

getter MIE => bits(32)
begin
  assert __mie[31:16] == Zeros(16);
  assert __mie[15:12] == '0000';
  assert __mie[10:8] == '000';
  assert __mie[6:4] == '000';
  assert __mie[2:0] == '000';

  return __mie;
end

setter MIE = value : bits(32)
begin
  __mie = value;
end

func __Reset_MIE()
begin
  __mie = Zeros(32);
end


var __MEPC : bits(32);
getter MEPC => bits(32)
begin
  return __MEPC;
end

setter MEPC = pc : bits(32)
begin
  assert pc[0] == '0';
  // todo: test IALIGN before assert after C extension implmented
  assert pc[1] == '0';
  __MEPC = pc;
end

func __Reset_MEPC()
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

func __Reset_MCAUSE()
begin
  MCAUSE_IS_INTERRUPT = FALSE;
  MCAUSE_XCPT_CODE = Zeros(31);
end


// General States Reset
func Reset()
begin
  __ResetCurrentPrivilege();

  __Reset_GPR();

  __Reset_MTVEC();
  __Reset_MTVAL();
  __Reset_MSTATUS();
  __Reset_MIP();
  __Reset_MIE();
  __Reset_MEPC();
  __Reset_MCAUSE();
end

// export to simulator
func ASL_Reset()
begin
  Reset();
end
