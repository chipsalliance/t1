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