func __op_add(rs1: bits(N), rs2: bits(N)) => bits(N)
begin
  return rs1 + rs2;
end

func __op_sub(rs1: bits(N), rs2: bits(N)) => bits(N)
begin
  return rs1 - rs2;
end

func __op_rsub(rs1: bits(N), rs2: bits(N)) => bits(N)
begin
  return rs2 - rs1;
end

func __op_and(rs1: bits(N), rs2: bits(N)) => bits(N)
begin
  return rs1 AND rs2;
end

func __op_or(rs1: bits(N), rs2: bits(N)) => bits(N)
begin
  return rs1 OR rs2;
end

func __op_xor(rs1: bits(N), rs2: bits(N)) => bits(N)
begin
  return rs1 XOR rs2;
end

func __op_min_s(rs1: bits(N), rs2: bits(N)) => bits(N)
begin
  if SInt(rs1) < SInt(rs2) then
    return rs1;
  else
    return rs2;
  end
end

func __op_max_s(rs1: bits(N), rs2: bits(N)) => bits(N)
begin
  if SInt(rs1) > SInt(rs2) then
    return rs1;
  else
    return rs2;
  end
end

func __op_min_u(rs1: bits(N), rs2: bits(N)) => bits(N)
begin
  if UInt(rs1) < UInt(rs2) then
    return rs1;
  else
    return rs2;
  end
end

func __op_max_u(rs1: bits(N), rs2: bits(N)) => bits(N)
begin
  if UInt(rs1) > UInt(rs2) then
    return rs1;
  else
    return rs2;
  end
end

func __op_mul(rs1: bits(N), rs2: bits(N)) => bits(N)
begin
  return rs1 * rs2;
end

func __op_mulh_ss(rs1: bits(N), rs2: bits(N)) => bits(N)
begin
  return (SInt(rs1) * SInt(rs2))[2*N-1:N];
end

func __op_mulh_su(rs1: bits(N), rs2: bits(N)) => bits(N)
begin
  return (SInt(rs1) * UInt(rs2))[2*N-1:N];
end

func __op_mulh_uu(rs1: bits(N), rs2: bits(N)) => bits(N)
begin
  return (UInt(rs1) * UInt(rs2))[2*N-1:N];
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

func __op_widen_mul_uu(rs1: bits(N), rs2: bits(N)) => bits(2*N)
begin
  return (UInt(rs1) * UInt(rs2))[2*N-1:0];
end
