func GetRS1(inst : bits(32)) => bits(5)
begin
  return inst[19:15];
end

func GetRS2(inst : bits(32)) => bits(5)
begin
  return inst[24:20];
end

func GetRD(inst : bits(32)) => bits(5)
begin
  return inst[11:7];
end

func GetIMM(inst : bits(32)) => bits(12)
begin
  return inst[31:20];
end

func GetSIMM(inst : bits(32)) => bits(12)
begin
  let hi : bits(7) = inst[31:25];
  let lo : bits(5) = inst[11:7];
  return [hi, lo];
end

func GetBIMM(inst : bits(32)) => bits(12)
begin
  let imm12 : bit = inst[31];
  let imm10_5 : bits(6) = inst[30:25];
  let imm4_1 : bits(4) = inst[11:8];
  let imm11 : bit = inst[7];

  return [imm12, imm11, imm10_5, imm4_1];
end

func GetUIMM(inst : bits(32)) => bits(20)
begin
  return inst[31:12];
end

func GetJIMM(inst : bits(32)) => bits(20)
begin
  let imm20 : bit = inst[31];
  let imm10_1 : bits(10) = inst[30:21];
  let imm11 : bit = inst[20];
  let imm19_12 : bits(8) = inst[19:12];

  return [imm20, imm19_12, imm11, imm10_1];
end

func GetCSR(inst : bits(32)) => bits(12)
begin
  return inst[31:20];
end
