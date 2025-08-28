var __FPR : array[32] of bits(32);

type freg_index of integer{0..31};

getter F[i : freg_index] => bits(32)
begin
  return __FPR[i];
end

setter F[i : freg_index] = value : bits(32)
begin
  __FPR[i] = value;
end

func __reset_fpr()
begin
  for i = 0 to 31 do
    __FPR[i] = Zeros(32);
  end
end

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
  RM_RMM,
  // In instruction's rm field, selects dynamic rounding mode; In Rounding Mode register, reserved.
  RM_DYN
};

record RM_Result {
  mode : RM;
  valid : boolean;
};

func RM_from_bits(b : bits(3)) => RM_Result
begin
  var result = RM_Result {
    mode = RM_RNE,
    valid = FALSE
  };

  case b of
    when '000' => result.mode = RM_RNE;
    when '001' => result.mode = RM_RTZ;
    when '010' => result.mode = RM_RDN;
    when '011' => result.mode = RM_RUP;
    when '100' => result.mode = RM_RMM;
    when '111' => result.mode = RM_DYN;
    otherwise => result.valid = FALSE;
  end

  return result;
end

func RM_to_bits(rm : RM) => bits(3)
begin
  case rm of
    when RM_RNE => return '000';
    when RM_RTZ => return '001';
    when RM_RDN => return '010';
    when RM_RUP => return '011';
    when RM_RMM => return '100';
    when RM_DYN => return '111';
  end
end

var __frm : RM;

getter FRM => bits(3)
begin
  return RM_to_bits(__frm);
end

setter FRM = value : bits(3)
begin
  let result : RM_Result = RM_from_bits(value);
  // User can only pass non-reserved RM value, any invalid input are consider as impl bug
  assert result.valid;

  __frm = result.mode;
end

// invalid operation
var F_XCPT_NV : boolean;
// divide by zero
var F_XCPT_DZ : boolean;
// overflow
var F_XCPT_OF : boolean;
// underflow
var F_XCPT_UF : boolean;
// inexact
var F_XCPT_NX : boolean;

func __reset_fcsr()
begin
  // Specification doesn't clarify the default rounding mode, RNE is chosen because it is zero.
  // Software should never assume this is the default rounding mode.
  __frm = RM_RNE;

  F_XCPT_NV = FALSE;
  F_XCPT_DZ = FALSE;
  F_XCPT_OF = FALSE;
  F_XCPT_UF = FALSE;
  F_XCPT_NX = FALSE;
end

func __reset_f_state()
begin
  __reset_fpr();
  __reset_fcsr();
end
