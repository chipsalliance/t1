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

var FRM : bits(3);

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
    valid = TRUE
  };

  case b of
    when '000' => result.mode = RM_RNE;
    when '001' => result.mode = RM_RTZ;
    when '010' => result.mode = RM_RDN;
    when '011' => result.mode = RM_RUP;
    when '100' => result.mode = RM_RMM;
    when '111' where FRM != '111' =>
      return RM_from_bits(FRM);
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
  FRM = '000';

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
