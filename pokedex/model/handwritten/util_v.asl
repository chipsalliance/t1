func __op_bit_and(rs1: bit, rs2: bit) => bit
begin
  return rs1 AND rs2;
end

func __op_bit_andn(rs1: bit, rs2: bit) => bit
begin
  return rs1 AND (NOT rs2);
end

func __op_bit_nand(rs1: bit, rs2: bit) => bit
begin
  return NOT (rs1 AND rs2);
end

func __op_bit_or(rs1: bit, rs2: bit) => bit
begin
  return rs1 OR rs2;
end

func __op_bit_orn(rs1: bit, rs2: bit) => bit
begin
  return rs1 OR (NOT rs2);
end

func __op_bit_nor(rs1: bit, rs2: bit) => bit
begin
  return NOT (rs1 OR rs2);
end

func __op_bit_xor(rs1: bit, rs2: bit) => bit
begin
  return rs1 XOR rs2;
end

func __op_bit_xnor(rs1: bit, rs2: bit) => bit
begin
  return NOT (rs1 XOR rs2);
end

// + (rs1 * rs2) + rd
func __op_intmac_macc(rd: bits(N), rs1: bits(N), rs2: bits(N)) => bits(N)
begin
  return rd + rs1 * rs2;
end

// - (rs1 * rs2) + rd
func __op_intmac_nmsac(rd: bits(N), rs1: bits(N), rs2: bits(N)) => bits(N)
begin
  return rd - rs1 * rs2;
end

// + (rs1 * rd) + rs2
func __op_intmac_madd(rd: bits(N), rs1: bits(N), rs2: bits(N)) => bits(N)
begin
  return rs2 + rs1 * rd;
end

// - (rs1 * rd) + rs2
func __op_intmac_nmsub(rd: bits(N), rs1: bits(N), rs2: bits(N)) => bits(N)
begin
  return rs2 - rs1 * rd;
end

func __op_widen_add_ss(rs1: bits(N), rs2: bits(N)) => bits(2*N)
begin
  return (SInt(rs1) + SInt(rs2))[2*N-1:0];
end

func __op_widen_add_uu(rs1: bits(N), rs2: bits(N)) => bits(2*N)
begin
  return (UInt(rs1) + UInt(rs2))[2*N-1:0];
end

func __op_widen_add_ws(rs1: bits(2*N), rs2: bits(N)) => bits(2*N)
begin
  return rs1 + SInt(rs2)[2*N-1:0];
end

func __op_widen_add_wu(rs1: bits(2*N), rs2: bits(N)) => bits(2*N)
begin
  return rs1 + SInt(rs2)[2*N-1:0];
end

func __op_widen_sub_ss(rs1: bits(N), rs2: bits(N)) => bits(2*N)
begin
  return (SInt(rs1) - SInt(rs2))[2*N-1:0];
end

func __op_widen_sub_uu(rs1: bits(N), rs2: bits(N)) => bits(2*N)
begin
  return (UInt(rs1) - UInt(rs2))[2*N-1:0];
end

func __op_widen_sub_ws(rs1: bits(2*N), rs2: bits(N)) => bits(2*N)
begin
  return rs1 - SInt(rs2)[2*N-1:0];
end

func __op_widen_sub_wu(rs1: bits(2*N), rs2: bits(N)) => bits(2*N)
begin
  return rs1 - SInt(rs2)[2*N-1:0];
end

func __op_widen_mul_ss(rs1: bits(N), rs2: bits(N)) => bits(2*N)
begin
  return (SInt(rs1) * SInt(rs2))[2*N-1:0];
end

func __op_widen_mul_su(rs1: bits(N), rs2: bits(N)) => bits(2*N)
begin
  return (SInt(rs1) * UInt(rs2))[2*N-1:0];
end

func __op_widen_mul_us(rs1: bits(N), rs2: bits(N)) => bits(2*N)
begin
  return (UInt(rs1) * SInt(rs2))[2*N-1:0];
end

func __op_widen_mul_uu(rs1: bits(N), rs2: bits(N)) => bits(2*N)
begin
  return (UInt(rs1) * UInt(rs2))[2*N-1:0];
end

func __op_narrow_srlv(rs1: bits(2*N), rs2: bits(N)) => bits(N)
begin
  if N == 8 then
    return ShiftRightLogical(rs1, UInt(rs2[3:0]))[N-1:0];
  elsif N == 16 then
    return ShiftRightLogical(rs1, UInt(rs2[4:0]))[N-1:0];
  elsif N == 32 then
    return ShiftRightLogical(rs1, UInt(rs2[5:0]))[N-1:0];
  else
    Unreachable();
  end
end

func __op_narrow_srav(rs1: bits(2*N), rs2: bits(N)) => bits(N)
begin
  if N == 8 then
    return ShiftRightArithmetic(rs1, UInt(rs2[3:0]))[N-1:0];
  elsif N == 16 then
    return ShiftRightArithmetic(rs1, UInt(rs2[4:0]))[N-1:0];
  elsif N == 32 then
    return ShiftRightArithmetic(rs1, UInt(rs2[5:0]))[N-1:0];
  else
    Unreachable();
  end
end

////////////////////////////
// Fixed-point operations //
////////////////////////////

record WithSaturate(N) {
  value : bits(N);
  sat : boolean;
};

func __op_aadd_u(rs1: bits(N), rs2: bits(N), vxrm: bits(2)) => bits(N)
begin
  var res : bits(N) = ((UInt(rs1) + UInt(rs2)) DIVRM 2)[N-1:0];

  if rs1[0] != rs2[0] then
    case vxrm of
      when VXRM_RNU => res = res + 1;
      when VXRM_RNE => res[0] = '0';
      when VXRM_RDN => begin end // do nothing
      when VXRM_ROD => res[1] = '1';
    end
  end

  return res;
end

func __op_asub_u(rs1: bits(N), rs2: bits(N), vxrm: bits(2)) => bits(N)
begin
  var res : bits(N) = ((UInt(rs1) - UInt(rs2)) DIVRM 2)[N-1:0];

  if rs1[0] != rs2[0] then
    case vxrm of
      when VXRM_RNU => res = res + 1;
      when VXRM_RNE => res[0] = '0';
      when VXRM_RDN => begin end // do nothing
      when VXRM_ROD => res[1] = '1';
    end
  end

  return res;
end

func __op_aadd_s(rs1: bits(N), rs2: bits(N), vxrm: bits(2)) => bits(N)
begin
  var res : bits(N) = ((SInt(rs1) + SInt(rs2)) DIVRM 2)[N-1:0];

  if rs1[0] != rs2[0] then
    case vxrm of
      when VXRM_RNU => res = res + 1;
      when VXRM_RNE => res[0] = '0';
      when VXRM_RDN => begin end // do nothing
      when VXRM_ROD => res[1] = '1';
    end
  end

  return res;
end

func __op_asub_s(rs1: bits(N), rs2: bits(N), vxrm: bits(2)) => bits(N)
begin
  var res : bits(N) = ((SInt(rs1) - SInt(rs2)) DIVRM 2)[N-1:0];

  if rs1[0] != rs2[0] then
    case vxrm of
      when VXRM_RNU => res = res + 1;
      when VXRM_RNE => res[0] = '0';
      when VXRM_RDN => begin end // do nothing
      when VXRM_ROD => res[1] = '1';
    end
  end

  return res;
end