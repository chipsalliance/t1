func GetRS1(inst : bits(32)) => bits(5)
begin
  return inst[19:15];
end

func GetRS2(inst : bits(32)) => bits(5)
begin
  return inst[24:20];
end

func GetRS3(inst : bits(32)) => bits(5)
begin
  return inst[31:27];
end

func GetRD{N : integer{16, 32}}(inst : bits(N)) => bits(5)
begin
  return inst[11:7];
end

func GetShamt5(inst : bits(32)) => bits(5)
begin
  return inst[24:20];
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

func GetVM(inst : bits(32)) => bit
begin
  return inst[25];
end

func GetNF(inst : bits(32)) => bits(3)
begin
  return inst[31:29];
end

func GetCLWSP_IMM(inst : bits(16)) => bits(6)
begin
  return [inst[3:2], inst[12], inst[6:4]];
end

func GetNZIMM(inst : bits(16)) => bits(6)
begin
  return [inst[12], inst[6:2]];
end

func GetCADDI16SP_IMM(inst : bits(16)) => bits(6)
begin
  return [inst[12], inst[4:3], inst[5], inst[2], inst[6]];
end

func GetCSWSP_IMM(inst : bits(16)) => bits(6)
begin
  return [inst[8:7], inst[12:9]];
end

func GetCSS_RS2(inst : bits(16)) => bits(5)
begin
  return inst[6:2];
end

func GetCLW_IMM(inst : bits(16)) => bits(5)
begin
  return [inst[5], inst[12:10], inst[6]];
end

func GetCL_RS1(inst : bits(16)) => bits(3)
begin
  return inst[9:7];
end

func GetCL_RD(inst : bits(16)) => bits(3)
begin
  return inst[4:2];
end

func GetCSW_IMM(inst : bits(16)) => bits(5)
begin
  return [inst[5], inst[12:10], inst[6]];
end

func GetCS_RS1(inst : bits(16)) => bits(3)
begin
  return inst[9:7];
end

func GetCS_RS2(inst : bits(16)) => bits(3)
begin
  return inst[4:2];
end

func GetCJ_IMM(inst : bits(16)) => bits(11)
begin
  return [inst[12], inst[8], inst[10:9], inst[6], inst[7], inst[2], inst[11], inst[5:3]];
end

func GetCR_RS1(inst : bits(16)) => bits(5)
begin
  return inst[11:7];
end

func GetCR_RS2(inst : bits(16)) => bits(5)
begin
  return inst[6:2];
end

func GetCB_IMM(inst : bits(16)) => bits(8)
begin
  return [
    inst[12],
    inst[6:5],
    inst[2],
    inst[11:10],
    inst[4:3]
  ];
end

func GetCB_RS1(inst : bits(16)) => bits(3)
begin
  return inst[9:7];
end

func GetCI_IMM(inst : bits(16)) => bits(6)
begin
  return [inst[12], inst[6:2]];
end

func GetCIW_IMM(inst : bits(16)) => bits(8)
begin
  return [
    inst[10:7],
    inst[12:11],
    inst[6],
    inst[5]
  ];
end

func GetCIW_RD(inst : bits(16)) => bits(3)
begin
  return inst[4:2];
end

func GetC_SHAMT(inst : bits(16)) => bits(6)
begin
  return [inst[12], inst[6:2]];
end

func GetC_ADDI_IMM(inst : bits(16)) => bits(6)
begin
  return [inst[12], inst[6:2]];
end

func GetCA_RS1(inst : bits(16)) => bits(3)
begin
  return inst[9:7];
end

func GetCA_RS2(inst : bits(16)) => bits(3)
begin
  return inst[4:2];
end

func IsRelease(inst : bits(32)) => boolean
begin
  return inst[25] == '1';
end

func IsAcquire(inst : bits(32)) => boolean
begin
  return inst[26] == '1';
end

func GetRM(inst : bits(32)) => bits(3)
begin
  return inst[14:12];
end
