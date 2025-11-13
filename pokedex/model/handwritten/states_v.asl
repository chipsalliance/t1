// Architectural Congifurations
constant VLEN : integer = 256;
constant ELEN : integer = 32;

constant LOG2_VLEN : integer = 8;

//////////////////////////
// Architectural States //
//////////////////////////

// all vector registers are concatenated
var __VRF : bits(32 * VLEN);

var VTYPE : VType;

var VL : integer;

var VSTART : bits(LOG2_VLEN);

var VXRM : bits(2);
var VXSAT : bit;

func resetVectorState()
begin
  __VRF = Zeros(32 * VLEN);
  VTYPE = VTYPE_ILL;
  VL = 0;
  VSTART = Zeros(LOG2_VLEN);
  VXRM = '00';
  VXSAT = '0';
end

//////////////////////////////
// Architectural State Type //
//////////////////////////////

record VType {
  ill : boolean;
  ma : bit;
  ta : bit;
  sew : integer{8, 16, 32, 64};
  lmul : integer{-3..3};
};

type SEW_TYPE of integer{8, 16, 32, 64};
type LOG2_VLMUL_TYPE of integer{-3..3};

type VRegIdx of integer{0..31};
type VRegIdxLmul2 of integer{0,2,4,6,8,10,12,14,16,18,20,22,24,26,28,30};
type VRegIdxLmul4 of integer{0,4,8,12,16,20,24,28};
type VRegIdxLmul8 of integer{0,8,16,24};

// deprecated, use VRegIdx instead
type VREG_TYPE of integer{0..31};

constant VXRM_RNU : bits(2) = '00'; // round to nearest up
constant VXRM_RNE : bits(2) = '01'; // round to nearest even
constant VXRM_RDN : bits(2) = '10'; // round down
constant VXRM_ROD : bits(2) = '11'; // round to odd

/////////////////////////////////
// Architectural State Helpers //
/////////////////////////////////

getter V0_MASK[idx: integer] => boolean
begin
  return (__VRF[idx]) as boolean;
end

getter VRF_MASK[vreg: VRegIdx, idx: integer] => bit
begin
  return __VRF[vreg * VLEN + idx];
end

setter VRF_MASK[vreg: VRegIdx, idx: integer] = value : bit
begin
  __VRF[vreg * VLEN + idx] = value;
end

getter VRF_8[vreg: VRegIdx, idx: integer] => bits(8)
begin
  return __VRF[vreg * VLEN + idx * 8 +: 8];
end

setter VRF_8[vreg: VRegIdx, idx: integer] = value : bits(8)
begin
  __VRF[vreg * VLEN + idx * 8 +: 8] = value;
end

getter VRF_16[vreg: VRegIdx, idx: integer] => bits(16)
begin
  return __VRF[vreg * VLEN + idx * 16 +: 16];
end

setter VRF_16[vreg: VRegIdx, idx: integer] = value : bits(16)
begin
  __VRF[vreg * VLEN + idx * 16 +: 16] = value;
end

getter VRF_32[vreg: VRegIdx, idx: integer] => bits(32)
begin
  return __VRF[vreg * VLEN + idx * 32 +: 32];
end

setter VRF_32[vreg: VRegIdx, idx: integer] = value : bits(32)
begin
  __VRF[vreg * VLEN + idx * 32 +: 32] = value;
end

constant VTYPE_ILL : VType = VType {
  ill=TRUE,
  ma='0',
  ta='0',
  sew=8,        // '000'
  lmul=0        // '000'
};

func VTYPE_from_bits(value: bits(32)) => VType
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
  var log2_lmul : LOG2_VLMUL_TYPE;

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
    when '000' => log2_lmul = 0;
    when '001' => log2_lmul = 1;
    when '010' => log2_lmul = 2;
    when '011' => log2_lmul = 3;
    when '100' => return VTYPE_ILL;
    when '111' => log2_lmul = -1;
    when '110' => log2_lmul = -2;
    when '101' => log2_lmul = -3;
  end

  // NOTE: impl define behavior:
  // 
  // We always assume SEW_MIN = 8.
  // We follow the must strict rule to check the validity for sew & lmul,
  // ELEN = 32:
  //   sew = 8  =>  lmul >= 1/4
  //   sew = 16 =>  lmul >= 1/2
  //   sew = 32 =>  lmul >= 1
  // ELEN = 64:
  //   sew = 8  =>  lmul >= 1/8
  //   sew = 16 =>  lmul >= 1/4
  //   sew = 32 =>  lmul >= 1/2
  //   sew = 64 =>  lmul >= 1
  if log2_lmul < 0 && sew << (-log2_lmul) > ELEN then
    return VTYPE_ILL;
  end

  return VType {
    ill=FALSE,
    ma=ma,
    ta=ta,
    sew=sew,
    lmul=log2_lmul
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

      case VTYPE.lmul of
        when 0 => lmul_bits = '000';
        when 1 => lmul_bits = '001';
        when 2 => lmul_bits = '010';
        when 3 => lmul_bits = '011';
        when -1 => lmul_bits = '111';
        when -2 => lmul_bits = '110';
        when -3 => lmul_bits = '101';
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

func __mul_lmul(x: integer, log2_lmul: LOG2_VLMUL_TYPE) => integer
begin
  case log2_lmul of
    when 0 => return x;
    when 1 => return x * 2;
    when 2 => return x * 4;
    when 3 => return x * 8;
    when -1 => return x DIV 2;
    when -2 => return x DIV 4;
    when -3 => return x DIV 8;
  end
end

func __log2_sew(sew: integer{8, 16, 32, 64}) => integer{3..6}
begin
  case sew of
    when 8 => return 3;
    when 16 => return 4;
    when 32 => return 5;
    when 64 => return 6;
  end
end

// compute vreg alignemnt based on lmul
func vreg_alignment(lmul: LOG2_VLMUL_TYPE) => integer{1, 2, 4, 8}
begin
  case lmul of
    when 0 => return 1;
    when 1 => return 2;
    when 2 => return 4;
    when 3 => return 8;
    when -1 => return 1;
    when -2 => return 1;
    when -3 => return 1;
  end
end

// compute vreg alignemt when emul=2*lmul, 0 means invalid
func vreg_double_alignment(lmul: LOG2_VLMUL_TYPE) => integer{0, 1, 2, 4, 8}
begin
  case lmul of
    when 0 => return 2;
    when 1 => return 4;
    when 2 => return 8;
    when 3 => return 0;
    when -1 => return 1;
    when -2 => return 1;
    when -3 => return 1;
  end
end

// compute vreg alignemnt based on lmul
func getAlign(vtype: VType) => integer{1, 2, 4, 8}
begin
  assert(!vtype.ill);

  case vtype.lmul of
    when 0 => return 1;
    when 1 => return 2;
    when 2 => return 4;
    when 3 => return 8;
    when -1 => return 1;
    when -2 => return 1;
    when -3 => return 1;
  end
end

func getEewAlign(vtype: VType, eew: integer{8, 16, 32, 64}) => (integer{1, 2, 4, 8}, boolean)
begin
  assert(!vtype.ill);

  let log2_emul : integer = vtype.lmul + __log2_sew(eew) - __log2_sew(vtype.sew);
  case log2_emul of
    when 0 => return (1, TRUE);
    when 1 => return (2, TRUE);
    when 2 => return (4, TRUE);
    when 3 => return (8, TRUE);
    when -1 => return (1, TRUE);
    when -2 => return (1, TRUE);
    when -3 => return (1, TRUE);
    otherwise => return (1, FALSE);
  end
end

func vreg_eew_alignment(vtype: VType, eew: integer{8, 16, 32, 64}) => integer{0, 1, 2, 4, 8}
begin
  let log2_emul : integer = vtype.lmul + __log2_sew(eew) - __log2_sew(vtype.sew);
  case log2_emul of
    when 0 => return 1;
    when 1 => return 2;
    when 2 => return 4;
    when 3 => return 8;
    when -1 => return 1;
    when -2 => return 1;
    when -3 => return 1;
    otherwise => return 0;
  end 
end

func invalid_vreg(lmul: LOG2_VLMUL_TYPE, x: VRegIdx) => boolean
begin
  return x MOD vreg_alignment(lmul) != 0;
end

// eew(x) = 2 * sew
func invalid_double_lmul(lmul: LOG2_VLMUL_TYPE) => boolean
begin
  return vreg_double_alignment(lmul) != 0;
end

// eew(x) = 2 * sew
func invalid_vreg_2sew(lmul: LOG2_VLMUL_TYPE, x: VRegIdx) => boolean
begin
  return x MOD vreg_double_alignment(lmul) != 0;
end

// eew(vd) = 2*sew, eew(vs) = sew
// return TRUE iff they have overlap and the overlap is invalid
// assuming vd/vs already checked by invalid_vreg_2sew/invalid_reg
func invalid_overlap_dst_src_2_1(lmul: LOG2_VLMUL_TYPE, vd: VRegIdx, vs: VRegIdx) => boolean
begin
  // 1. when lmul < 1:
  //   they have overlap when (vd == vs),
  //   and the overlap is invalid
  //
  // 2. when lmul >= 1:
  //   they have overlap when (vd == vs) or (vs == vd + lmul),
  //     (vs == vd) is invalid overlap
  //     (vs == vd + lmul) is valid overlap

  return vd == vs;
end

// eew(vw) = 2 * sew, eew(vn) = sew
// return TRUE iff they have overlap
// assuming vw/vn already checked by invalid_vreg_2sew/invalid_reg
func invalid_overlap_src_2_1(lmul: LOG2_VLMUL_TYPE, vw: VRegIdx, vn: VRegIdx) => boolean
begin
  case lmul of
    when 0 => return vw >> 1 == vn >> 1;
    when 1 => return vw >> 2 == vn >> 2;
    when 2 => return vw >> 3 == vn >> 3;
    when 3 => Unreachable();
    when -3 => return vw == vn;
    when -2 => return vw == vn;
    when -1 => return vw == vn;
  end
end

// eew(vm) = 1, eew(vs) = sew
// return TRUE iff they have overlap
// assuming vs already checked by invalid_reg
func invalid_overlap_src_m_1(lmul: LOG2_VLMUL_TYPE, vm: VRegIdx, vs: VRegIdx) => boolean
begin
  case lmul of
    when 0 => return vm == vs;
    when 1 => return vm >> 1 == vs >> 1;
    when 2 => return vm >> 2 == vs >> 2;
    when 3 => return vm >> 3 == vs >> 3;
    when -1 => return vm == vs;
    when -2 => return vm == vs;
    when -3 => return vm == vs;
  end
end

// eew(vd) = sew, eew(vs) = 2*sew
// return TRUE iff they have overlap and the overlap is invalid
// assuming vd/vs already checked by invalid_vreg_2sew/invalid_reg
func invalid_overlap_dst_src_1_2(lmul: LOG2_VLMUL_TYPE, vd: VRegIdx, vs: VRegIdx) => boolean
begin
  // 1. when lmul < 1:
  //   they have overlap when (vd == vs),
  //   and the overlap is valid
  //
  // 2. when lmul >= 1:
  //   they have overlap when (vd == vs) or (vd == vs + lmul),
  //     (vd == vs) is valid overlap
  //     (vd == vs + lmul) is invalid overlap

  case lmul of
    when 0 => return vd == vs + 1;
    when 1 => return vd == vs + 2;
    when 2 => return vd == vs + 4;
    when 3 => Unreachable();
    when -1 => return FALSE;
    when -2 => return FALSE;
    when -3 => return FALSE;
  end
end

// eew(vd) = 1, eew(vs) = sew, vd is mask
// return TRUE iff they have overlap and the overlap is invalid
// assuming vs already checked by invalid_reg
func invalid_overlap_dst_src_m_1(lmul: LOG2_VLMUL_TYPE, vd: VRegIdx, vs: VRegIdx) => boolean
begin
  // 1. when lmul < 1:
  //   they have overlap when (vd == vs),
  //   and the overlap is valid
  //
  // 2. when lmul >= 1:
  //     (vd == vs) is valid overlap
  //     (vd in vs+1 ..= vs + lmul) is invalid overlap

  case lmul of
    when 0 => return FALSE;
    when 1 => return (vd >> 1 == vs >> 1) && (vd != vs);
    when 2 => return (vd >> 2 == vs >> 2) && (vd != vs);
    when 3 => return (vd >> 3 == vs >> 3) && (vd != vs);
    when -1 => return FALSE;
    when -2 => return FALSE;
    when -3 => return FALSE;
  end
end

// VLMAX = VLEN * VLMUL / VSEW
func __compute_vlmax(vtype: VType) => integer
begin
  assert !vtype.ill;

  return __mul_lmul(VLEN DIV vtype.sew, vtype.lmul);
end

func logWrite_VREG_1(vd: VRegIdx)
begin
  FFI_write_VREG_hook(vd, __VRF[vd * VLEN  +: VLEN]);
end

func logWrite_VREG_elmul(vd: VRegIdx, elmul: integer{1,2,4,8})
begin
  case elmul of
    when 1 => logWrite_VREG_1(vd);
    when 2 => begin
      logWrite_VREG_1(vd);
      logWrite_VREG_1((vd+1) as VRegIdx);
    end
    when 4 => begin
      logWrite_VREG_1(vd);
      logWrite_VREG_1((vd+1) as VRegIdx);
      logWrite_VREG_1((vd+2) as VRegIdx);
      logWrite_VREG_1((vd+3) as VRegIdx);
    end
    when 8 => begin
      logWrite_VREG_1(vd);
      logWrite_VREG_1((vd+1) as VRegIdx);
      logWrite_VREG_1((vd+1) as VRegIdx);
      logWrite_VREG_1((vd+2) as VRegIdx);
      logWrite_VREG_1((vd+3) as VRegIdx);
      logWrite_VREG_1((vd+4) as VRegIdx);
      logWrite_VREG_1((vd+5) as VRegIdx);
      logWrite_VREG_1((vd+6) as VRegIdx);
      logWrite_VREG_1((vd+7) as VRegIdx);
    end
  end
end