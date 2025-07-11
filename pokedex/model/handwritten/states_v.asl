// Architectural Congifurations
constant VLEN : integer = 256;

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

record VTYPE_TYPE {
  ill : boolean;
  ma : bit;
  ta : bit;
  sew : bits(3);
  lmul : bits(3);
};

/////////////////////////////////
// Architectural State Helpers //
/////////////////////////////////

func ClearVSTART()
begin
  VSTART = Zeros(LOG2_VLEN);
end

constant VTYPE_ILL : VTYPE_TYPE = VTYPE_TYPE {
  ill=TRUE,
  ma='0',
  ta='0',
  sew='000',
  lmul='000'
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
  let sew : bits(3) = value[5:3];
  let lmul : bits(3) = value[2:0];

  if !(__is_sew_valid(sew) && __is_lmul_valid(lmul)) then
    return VTYPE_ILL;
  end

  return VTYPE_TYPE {
    ill=FALSE,
    ma=ma,
    ta=ta,
    sew=sew,
    lmul=lmul
  };
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

func __is_lmul_valid(lmul: bits(3)) => boolean
begin
  // '100' is the only reserved lmul in unpriv spec 20250508
  return lmul != '100';
end

func __mul_lmul(x: integer, lmul: bits(3)) => integer
begin
  case lmul of
    when '000' => return x;
    when '001' => return x * 2;
    when '010' => return x * 4;
    when '011' => return x * 8;
    when '100' => assert FALSE;
    when '101' => return x DIV 2;
    when '110' => return x DIV 4;
    when '111' => return x DIV 8;
  end
end

// VLMAX = VLEN * VLMUL / VSEW
func __compute_vlmax(vtype: VTYPE_TYPE) => integer
begin
  assert !vtype.ill;

  return __mul_lmul(VLEN DIV __decode_sew(vtype.sew), vtype.lmul);
end

// compute log2(VSEW / VLMUL)
func __compute_log2_sew_div_lmul(vtype: VTYPE_TYPE) => integer
begin
  assert !vtype.ill;

  var log2_sew : integer;
  case vtype.sew of
    when '000' => log2_sew = 3;
    when '001' => log2_sew = 4;
    when '010' => log2_sew = 5;
    when '011' => log2_sew = 6;
    otherwise => assert FALSE;
  end

  var log2_lmul : integer;
  case vtype.lmul of
    when '000' => return 0;
    when '001' => return 1;
    when '010' => return 2;
    when '011' => return 3;
    when '100' => assert FALSE;
    when '101' => return -1;
    when '110' => return -2;
    when '111' => return -3;
  end

  return log2_sew - log2_lmul;
end
