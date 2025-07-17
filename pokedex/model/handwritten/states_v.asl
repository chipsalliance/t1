// Architectural Congifurations
constant VLEN : integer = 256;
constant ELEN : integer = 32;

constant LOG2_VLEN : integer = 8;

//////////////////////////
// Architectural States //
//////////////////////////

// all vector registers are concatenated
var __VRF : bits(32 * VLEN);

var VTYPE : VTYPE_TYPE;

var VL : integer;

var VSTART : bits(LOG2_VLEN);

var VXRM : bits(2);
var VXSAT : bit;

//////////////////////////////
// Architectural State Type //
//////////////////////////////

record VTYPE_TYPE {
  ill : boolean;
  ma : bit;
  ta : bit;
  sew : integer{8, 16, 32, 64};
  lmul : VLMUL_TYPE;
};

enumeration VLMUL_TYPE {
  VLMUL_1,
  VLMUL_2,
  VLMUL_4,
  VLMUL_8,
  VLMUL_1_2,
  VLMUL_1_4,
  VLMUL_1_8
};

type VREG_TYPE of integer{0..31};

/////////////////////////////////
// Architectural State Helpers //
/////////////////////////////////

getter V0_MASK[idx: integer] => boolean
begin
  return (__VRF[idx]) as boolean;
end

getter VRF_8[vreg: VREG_TYPE, idx: integer] => bits(8)
begin
  return __VRF[vreg * VLEN + idx * 8 +: 8];
end

setter VRF_8[vreg: VREG_TYPE, idx: integer] = value : bits(8)
begin
  __VRF[vreg * VLEN + idx * 8 +: 8] = value;
end

getter VRF_16[vreg: VREG_TYPE, idx: integer] => bits(16)
begin
  return __VRF[vreg * VLEN + idx * 16 +: 16];
end

setter VRF_16[vreg: VREG_TYPE, idx: integer] = value : bits(16)
begin
  __VRF[vreg * VLEN + idx * 16 +: 16] = value;
end

getter VRF_32[vreg: VREG_TYPE, idx: integer] => bits(32)
begin
  return __VRF[vreg * VLEN + idx * 32 +: 32];
end

setter VRF_32[vreg: VREG_TYPE, idx: integer] = value : bits(32)
begin
  __VRF[vreg * VLEN + idx * 32 +: 32] = value;
end

func ClearVSTART()
begin
  VSTART = Zeros(LOG2_VLEN);
end

constant VTYPE_ILL : VTYPE_TYPE = VTYPE_TYPE {
  ill=TRUE,
  ma='0',
  ta='0',
  sew=8,        // '000'
  lmul=VLMUL_1  // '000'
};

func VTYPE_from_bits(value: bits(32)) => VTYPE_TYPE
begin
  // vtype[31] is `vill`
  // vtype[30:8] is reserved
  // for a valid vtype, both fields must be zero
  if value[31:8] != Zeros(24) then
    return VTYPE_ILL;
  end

  let ma : bit = value[7];
  let ta : bit = value[6];
  let sew_bits : bits(3) = value[5:3];
  let lmul_bits : bits(3) = value[2:0];

  var sew : integer{8, 16, 32, 64};
  var lmul : VLMUL_TYPE;

  // check sew
  case sew_bits of
    when '000' => sew = 8;
    when '001' => sew = 16;
    when '010' => sew = 32;
    when '011' => sew = 64;
    otherwise => return VTYPE_ILL;
  end

  // check lmul
  case lmul_bits of
    when '000' => lmul = VLMUL_1;
    when '001' => lmul = VLMUL_2;
    when '010' => lmul = VLMUL_4;
    when '011' => lmul = VLMUL_8;
    when '100' => return VTYPE_ILL;
    when '101' => lmul = VLMUL_1_2;
    when '110' => lmul = VLMUL_1_4;
    when '111' => lmul = VLMUL_1_8;
  end

  return VTYPE_TYPE {
    ill=FALSE,
    ma=ma,
    ta=ta,
    sew=sew,
    lmul=lmul
  };
end

getter VTYPE_BITS => bits(32)
  begin
    if VTYPE.ill then
      return ['1', Zeros(31)];
  else
      var sew_bits : bits(3);
      var lmul_bits : bits(3);

      case VTYPE.sew of
        when 8 => sew_bits = '000';
        when 16 => sew_bits = '001';
        when 32 => sew_bits = '010';
        when 64 => sew_bits = '011';
      end

      return [
          Zeros(24),
          VTYPE.ma,   // [7]
          VTYPE.ta,   // [6]
          sew_bits,   // [5:3]
          lmul_bits   // [2:0]
      ];
  end
end

// VLMAX = LMUL * VLEN / SEW
getter VLMAX => integer
begin
  return __compute_vlmax(VTYPE);
end

func __ResetVectorState()
begin
  __VRF = Zeros(32 * VLEN);

  VTYPE = VTYPE_ILL;
  VL = 0;
  ClearVSTART();
  VXRM = '00';
  VXSAT = '0';
end

///////////////////////
// Utility Functions //
///////////////////////

func __log2_sew_lmul(sew: bits(3), lmul: bits(3)) => integer
begin
  var log2_sew : integer;

  var log2_lmul : integer;

  return log2_sew - log2_lmul;
end

func __is_sew_valid(sew: bits(3)) => boolean
begin
  case sew of
    when '000' => return TRUE;
    when '001' => return TRUE;
    when '010' => return TRUE;
    when '011' => return TRUE;
    otherwise => return FALSE;
  end
end

func __decode_sew(sew: bits(3)) => integer
begin
  case sew of
    when '000' => return 8;
    when '001' => return 16;
    when '010' => return 32;
    when '011' => return 64;
    otherwise => assert FALSE;
  end
end

func __mul_lmul(x: integer, lmul: VLMUL_TYPE) => integer
begin
  case lmul of
    when VLMUL_1 => return x;
    when VLMUL_2 => return x * 2;
    when VLMUL_4 => return x * 4;
    when VLMUL_8 => return x * 8;
    when VLMUL_1_2 => return x DIV 2;
    when VLMUL_1_4 => return x DIV 4;
    when VLMUL_1_8 => return x DIV 8;
  end
end

func __div_lmul(x: integer, lmul: VLMUL_TYPE) => integer
begin
  case lmul of
    when VLMUL_1 => return x;
    when VLMUL_2 => return x DIV 2;
    when VLMUL_4 => return x DIV 4;
    when VLMUL_8 => return x DIV 8;
    when VLMUL_1_2 => return x * 2;
    when VLMUL_1_4 => return x * 4;
    when VLMUL_1_8 => return x * 8;
  end
end

func invalid_vreg(lmul: VLMUL_TYPE, x: VREG_TYPE) => boolean
begin
  case lmul of
    when VLMUL_1 => return FALSE;
    when VLMUL_2 => return x MOD 2 != 0;
    when VLMUL_4 => return x MOD 4 != 0;
    when VLMUL_8 => return x MOD 8 != 0;
    when VLMUL_1_2 => return FALSE;
    when VLMUL_1_4 => return FALSE;
    when VLMUL_1_8 => return FALSE;
  end
end

func invalid_double_lmul(lmul: VLMUL_TYPE) => boolean
begin
  case lmul of
    when VLMUL_1 => return FALSE;
    when VLMUL_2 => return FALSE;
    when VLMUL_4 => return FALSE;
    when VLMUL_8 => return TRUE;
    when VLMUL_1_2 => return FALSE;
    when VLMUL_1_4 => return FALSE;
    when VLMUL_1_8 => return FALSE;
  end
end

// eew(x) = 2 * sew
func invalid_vreg_2sew(lmul: VLMUL_TYPE, x: VREG_TYPE) => boolean
begin
  case lmul of
    when VLMUL_1 => return x MOD 2 != 0;
    when VLMUL_2 => return x MOD 4 != 0;
    when VLMUL_4 => return x MOD 8 != 0;
    when VLMUL_8 => assert FALSE;
    when VLMUL_1_2 => return FALSE;
    when VLMUL_1_4 => return FALSE;
    when VLMUL_1_8 => return FALSE;
  end
end

// eew(vd) = 2*sew, eew(vs) = sew
// return TRUE iff they have overlap and the overlap is invalid
// assuming vd/vs already checked by invalid_vreg_2sew/invalid_reg
func invalid_overlap_dst_src_2_1(lmul: VLMUL_TYPE, vd: VREG_TYPE, vs: VREG_TYPE) => boolean
begin
  // 1. when lmul < 1:
  //   they have overlap when (vd == vs),
  //   and the overlap is illegal
  //
  // 2. when lmul < 1:
  //   they have overlap when (vd == vs) or (vs == vd + lmul),
  //     (vs == vd) is invalid overlap
  //     (vs == vd + lmul) is valid overlap

  return vd == vs;
end

// eew(vw) = 2 * sew, eew(vn) = sew
// return TRUE iff they have overlap
// assuming vw/vn already checked by invalid_vreg_2sew/invalid_reg
func invalid_overlap_src_2_1(lmul: VLMUL_TYPE, vw: VREG_TYPE, vn: VREG_TYPE) => boolean
begin
  case lmul of
    when VLMUL_1 => return vw >> 1 == vn >> 1;
    when VLMUL_2 => return vw >> 2 == vn >> 2;
    when VLMUL_4 => return vw >> 3 == vn >> 3;
    when VLMUL_8 => assert FALSE;
    when VLMUL_1_2 => return vw == vn;
    when VLMUL_1_4 => return vw == vn;
    when VLMUL_1_8 => return vw == vn;
  end
end

// VLMAX = VLEN * VLMUL / VSEW
func __compute_vlmax(vtype: VTYPE_TYPE) => integer
begin
  assert !vtype.ill;

  return __mul_lmul(VLEN DIV vtype.sew, vtype.lmul);
end

// compute VSEW / VLMUL
func __compute_sew_div_lmul(vtype: VTYPE_TYPE) => integer
begin
  assert !vtype.ill;

  return __div_lmul(vtype.sew, vtype.lmul);
end
