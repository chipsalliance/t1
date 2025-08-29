// The F extension adds 32 floating-point registers, f0-f31, each 32 bits wide
var __FPR : array[32] of bits(32);

// freg_index is a new type to ensure bit to int conversion is constrainted
type freg_index of integer{0..31};

// Instruction should use the `F` getter to obtain value, eg. `let fv : bits(32) = F[1];`
getter F[i : freg_index] => bits(32)
begin
  return __FPR[i];
end

// Instruction should use the `F` getter to update value, eg. `F[5] = Zeros(32);`
setter F[i : freg_index] = value : bits(32)
begin
  __FPR[i] = value;
end

// Helper function to reset all the F register to Zeros
func __reset_fpr()
begin
  for i = 0 to 31 do
    __FPR[i] = Zeros(32);
  end
end

// The F extension adds a floating-point control and status register fcsr,
// which contains the operating mode and exception status of the floating-point
// unit.
//
// The floating-point control and status register, fcsr, is a RISC-V control
// and status register (CSR). It is a 32-bit read/write register that selects
// the dynamic rounding mode for floating-point arithmetic operations and holds
// the accrued exception flags

var FRM : bits(3);

// RM is a enumeration that contains all
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

// RM_Result represent the rounding mode bits decode result
record RM_Result {
  mode : RM;
  valid : boolean;
};

// Decode three bits to `RM` rounding mode enumeration.
// Return invalid result for patterns 0b101, 0b110 or when FRM csr is 0b111.
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
    // FRM with "DYNAMIC" is reserved
    when '111' where FRM != '111' =>
      return RM_from_bits(FRM);
    otherwise => result.valid = FALSE;
  end

  return result;
end

// Convert RM enumeration to bits representation
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

func __clear_fflags()
begin
  F_XCPT_NV = FALSE;
  F_XCPT_DZ = FALSE;
  F_XCPT_OF = FALSE;
  F_XCPT_UF = FALSE;
  F_XCPT_NX = FALSE;
end

// set fflags base on the softfloat global exception flag
//
// typedef enum {
//     softfloat_flag_inexact   =  1,
//     softfloat_flag_underflow =  2,
//     softfloat_flag_overflow  =  4,
//     softfloat_flag_infinite  =  8,
//     softfloat_flag_invalid   = 16
// } exceptionFlag_t;
func set_fflags_from_softfloat(softfloat_xcpt : integer)
begin
  case softfloat_xcpt of
    when 1 =>
      F_XCPT_NX = TRUE;
    when 2 =>
      F_XCPT_UF = TRUE;
    when 4 =>
      F_XCPT_OF = TRUE;
    when 8 =>
      F_XCPT_DZ = TRUE;
    when 16 =>
      F_XCPT_NV = TRUE;
    // implementation bug: unhandled softfloat exception
    otherwise => assert FALSE;
  end
end

func __reset_fcsr()
begin
  FRM = '000';

  __clear_fflags();
end

func __reset_f_state()
begin
  __reset_fpr();
  __reset_fcsr();
end
