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

// follow RISC-V convention:
// divrem_s(x, 0) = (-1, x)
// divrem_s(SINT_MIN, -1) = (SINT_MIN, 0)
func __op_div_s(rs1: bits(N), rs2: bits(N)) => bits(N)
begin
  if IsZero(rs2) then
    // division by zero
    return Ones(N);
  elsif rs1 == ['1', Zeros(N-1)] && IsOnes(rs2) then
    return ['1', Zeros(N-1)];
  else
    // Division: rount to zero
    return (SInt(rs1) QUOT SInt(rs2))[N-1:0];
  end
end

func __op_rem_s(rs1: bits(N), rs2: bits(N)) => bits(N)
begin
  if IsZero(rs2) then
    // division by zero
    return rs1;
  elsif rs1 == ['1', Zeros(N-1)] && IsOnes(rs2) then
    // overflow
    return Zeros(N);
  else
    // Division: rount to zero
    return (SInt(rs1) REM SInt(rs2))[N-1:0];
  end
end

// follow RISC-V convention:
// divrem_u(x, 0) = (UINT_MAX , x)
func __op_div_u(rs1: bits(N), rs2: bits(N)) => bits(N)
begin
  if IsZero(rs2) then
    // division by zero
    return Ones(N);
  else
    // Division: rount to zero
    return (UInt(rs1) QUOT UInt(rs2))[N-1:0];
  end
end

func __op_rem_u(rs1: bits(N), rs2: bits(N)) => bits(N)
begin
  if IsZero(rs2) then
    // division by zero
    return rs1;
  else
    // Division: rount to zero
    return (UInt(rs1) REM UInt(rs2))[N-1:0];
  end
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
