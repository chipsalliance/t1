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
  //
  // Keeping sew/lmul unchanged and then setting a new sew,
  // lmul may overflow but never underflow.
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

// return (-, FALSE) if lmul*2 is too large
// NOTE: this function does not check sew
func getAlignWiden(vtype: VType) => (integer{1, 2, 4, 8}, boolean)
begin
  assert(!vtype.ill);

  case vtype.lmul of
    when 0 => return (2, TRUE);
    when 1 => return (4, TRUE);
    when 2 => return (8, TRUE);
    when 3 => return (8, FALSE);
    when -1 => return (1, TRUE);
    when -2 => return (1, TRUE);
    when -3 => return (1, TRUE);
  end
end


// NOTE: this function does not check sew
//
// NOTE: impl defined behavior
//   assuming VType is valid and sew/2 is valid,
//   lmul/2 will also be valid. See notes in VTYPE_from_bits
func getAlignNarrow2(vtype: VType) => integer{1, 2, 4, 8}
begin
  assert(!vtype.ill);
  case vtype.lmul of
    when -3 => return 1;
    when -2 => return 1;
    when -1 => return 1;
    when 0 => return 1;
    when 1 => return 1;
    when 2 => return 2;
    when 3 => return 4;
  end
end

// NOTE: this function does not check sew
//
// NOTE: impl defined behavior
//   assuming VType is valid and sew/4 is valid,
//   lmul/4 will also be valid. See notes in VTYPE_from_bits
func getAlignNarrow4(vtype: VType) => integer{1, 2, 4, 8}
begin
  assert(!vtype.ill);
  case vtype.lmul of
    when -3 => return 1;
    when -2 => return 1;
    when -1 => return 1;
    when 0 => return 1;
    when 1 => return 1;
    when 2 => return 1;
    when 3 => return 2;
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

// VLMAX = VLEN * VLMUL / VSEW
func __compute_vlmax(vtype: VType) => integer
begin
  assert !vtype.ill;

  return __mul_lmul(VLEN DIV vtype.sew, vtype.lmul);
end

func logWrite_VREG_1(vd: VRegIdx)
begin
  var vd_mask = Zeros(32);
  vd_mask[vd] = '1';
  FFI_write_VREG_hook(vd_mask);
end

// to support segmented load, elmul is not restricted to 1,2,4,8
func logWrite_VREG_elmul(vd: VRegIdx, elmul: integer{1..8})
begin
  assert vd + elmul <= 32;

  var vd_mask = Zeros(32);
  for i = 0 to elmul - 1 do
    vd_mask[vd + i] = '1';
  end
  FFI_write_VREG_hook(vd_mask);
end