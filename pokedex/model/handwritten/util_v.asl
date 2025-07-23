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