/// extracts bits [19:15] from the provided 32-bit bitvector.
func GetRS1(inst : bits(32)) => bits(5)
begin
  return inst[19:15];
end

/// extracts bits [24:20] from the provided 32-bit bitvector.
func GetRS2(inst : bits(32)) => bits(5)
begin
  return inst[24:20];
end

/// extracts bits [31:27] from the provided 32-bit bitvector.
func GetRS3(inst : bits(32)) => bits(5)
begin
  return inst[31:27];
end

/// extracts bits [11:7] from the provided 32-bit or 16-bit bitvector.
func GetRD{N : integer{16, 32}}(inst : bits(N)) => bits(5)
begin
  return inst[11:7];
end

/// extracts bits [24:20] from the provided 32-bit bitvector.
func GetShamt5(inst : bits(32)) => bits(5)
begin
  return inst[24:20];
end

/// extracts bits [31:20] from the provided 32-bit bitvector.
func GetIMM(inst : bits(32)) => bits(12)
begin
  return inst[31:20];
end

/// extracts bits [31:25] and [11:7] from the provided 32-bit bitvector
/// and concatenates them into a single 12-bit bitvector.
func GetSIMM(inst : bits(32)) => bits(12)
begin
  let hi : bits(7) = inst[31:25];
  let lo : bits(5) = inst[11:7];
  return [hi, lo];
end

/// extracts bits [31], [30:25], [11:8] and [7] from the provided 32-bit bitvector
/// and concatenates them into a single 12-bit bitvector with reordered position:
/// ```text
/// [[31], [7], [30:25], [11:8]]
/// ```
func GetBIMM(inst : bits(32)) => bits(12)
begin
  let imm12 : bit = inst[31];
  let imm10_5 : bits(6) = inst[30:25];
  let imm4_1 : bits(4) = inst[11:8];
  let imm11 : bit = inst[7];

  return [imm12, imm11, imm10_5, imm4_1];
end

/// extracts bits [31:12] from the provided 32-bit bitvector.
func GetUIMM(inst : bits(32)) => bits(20)
begin
  return inst[31:12];
end

/// extracts bits [31], [30:21], [20] and [19:12] from the provided 32-bit bitvector
/// and concatenates them into a single 20-bit bitvector with reordered position:
/// ```text
/// [[31], [19:12], [20], [30:21]]
/// ```
func GetJIMM(inst : bits(32)) => bits(20)
begin
  let imm20 : bit = inst[31];
  let imm10_1 : bits(10) = inst[30:21];
  let imm11 : bit = inst[20];
  let imm19_12 : bits(8) = inst[19:12];

  return [imm20, imm19_12, imm11, imm10_1];
end

/// extracts bits [31:20] from the provided 32-bit bitvector.
func GetCSR(inst : bits(32)) => bits(12)
begin
  return inst[31:20];
end

/// extracts bits [25] from the provided 32-bit bitvector.
func GetVM(inst : bits(32)) => bit
begin
  return inst[25];
end

/// extracts bits [31:29] from the provided 32-bit bitvector.
func GetNF(inst : bits(32)) => bits(3)
begin
  return inst[31:29];
end

/// extracts bits [12], [6:4] and [3:2] from the provided 16-bit bitvector
/// and concatenates them into a single 6-bit bitvector with reordered position:
/// ```text
/// [[3:2], [12], [6:4]]
/// ```
func GetCLWSP_IMM(inst : bits(16)) => bits(6)
begin
  return [inst[3:2], inst[12], inst[6:4]];
end

/// extracts bits [12] and [6:2] from the provided 16-bit bitvector
/// and concatenates them into a single 6-bit bitvector.
func GetNZIMM(inst : bits(16)) => bits(6)
begin
  return [inst[12], inst[6:2]];
end

/// extracts bits [12], [6], [5], [4:3] and [2] from the provided 16-bit bitvector
/// and concatenates them into a single 6-bit bitvector with reordered position:
/// ```text
/// [[12], [4:3], [5], [2], [6]]
/// ```
func GetCADDI16SP_IMM(inst : bits(16)) => bits(6)
begin
  return [inst[12], inst[4:3], inst[5], inst[2], inst[6]];
end

/// extracts bits [12:9] and [8:7] from the provided 16-bit bitvector
/// and concatenates them into a single 6-bit bitvector with reordered position:
/// ```text
/// [[8:7], [12:9]]
/// ```
func GetCSWSP_IMM(inst : bits(16)) => bits(6)
begin
  return [inst[8:7], inst[12:9]];
end

/// extracts bits [6:2] from the provided 16-bit bitvector.
func GetCSS_RS2(inst : bits(16)) => bits(5)
begin
  return inst[6:2];
end

/// extracts bits [12:10], [6] and [5] from the provided 16-bit bitvector
/// and concatenates them into a single 5-bit bitvector with reordered position:
/// ```text
/// [[5], [12:10], [6]]
/// ```
func GetCLW_IMM(inst : bits(16)) => bits(5)
begin
  return [inst[5], inst[12:10], inst[6]];
end

/// extracts bits [9:7] from the provided 16-bit bitvector.
func GetCL_RS1(inst : bits(16)) => bits(3)
begin
  return inst[9:7];
end

/// extracts bits [4:2] from the provided 16-bit bitvector.
func GetCL_RD(inst : bits(16)) => bits(3)
begin
  return inst[4:2];
end

/// extracts bits [12:10], [6] and [5] from the provided 16-bit bitvector
/// and concatenates them into a single 5-bit bitvector with reordered position:
/// ```text
/// [[5], [12:10], [6]]
/// ```
func GetCSW_IMM(inst : bits(16)) => bits(5)
begin
  return [inst[5], inst[12:10], inst[6]];
end

/// extracts bits [9:7] from the provided 16-bit bitvector.
func GetCS_RS1(inst : bits(16)) => bits(3)
begin
  return inst[9:7];
end

/// extracts bits [4:2] from the provided 16-bit bitvector.
func GetCS_RS2(inst : bits(16)) => bits(3)
begin
  return inst[4:2];
end

/// extracts bits [12:2] from the provided 16-bit bitvector
/// and concatenates them into a single 11-bit bitvector with reordered position:
/// ```text
/// [[12], [8], [10:9], [6], [7], [2], [11], [5:3]]
/// ```
func GetCJ_IMM(inst : bits(16)) => bits(11)
begin
  return [inst[12], inst[8], inst[10:9], inst[6], inst[7], inst[2], inst[11], inst[5:3]];
end

/// extracts bits [11:7] from the provided 16-bit bitvector.
func GetCR_RS1(inst : bits(16)) => bits(5)
begin
  return inst[11:7];
end

/// extracts bits [6:2] from the provided 16-bit bitvector.
func GetCR_RS2(inst : bits(16)) => bits(5)
begin
  return inst[6:2];
end

/// extracts bits [12], [11:10], [6:5], [4:3], [2] from the provided 16-bit bitvector
/// and concatenates them into a single 8-bit bitvector with reordered position:
/// ```text
/// [[12], [6:5], [2], [11:10], [4:3]]
/// ```
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

/// extracts bits [9:7] from the provided 16-bit bitvector.
func GetCB_RS1(inst : bits(16)) => bits(3)
begin
  return inst[9:7];
end

/// extracts bits [12], [6:2] from the provided 16-bit bitvector
/// and concatenates them into a single 6-bit bitvector.
func GetCI_IMM(inst : bits(16)) => bits(6)
begin
  return [inst[12], inst[6:2]];
end

/// extracts bits [12:11], [10:7], [6], [5] from the provided 16-bit bitvector
/// and concatenates them into a single 8-bit bitvector with reordered position:
/// ```text
/// [[10:7], [12:11], [6], [5]]
/// ```
func GetCIW_IMM(inst : bits(16)) => bits(8)
begin
  return [
    inst[10:7],
    inst[12:11],
    inst[6],
    inst[5]
  ];
end

/// extracts bits [4:2] from the provided 16-bit bitvector.
func GetCIW_RD(inst : bits(16)) => bits(3)
begin
  return inst[4:2];
end

/// extracts bits [12], [6:2] from the provided 16-bit bitvector
/// and concatenates them into a single 6-bit bitvector.
func GetC_SHAMT(inst : bits(16)) => bits(6)
begin
  return [inst[12], inst[6:2]];
end

/// extracts bits [12], [6:2] from the provided 16-bit bitvector
/// and concatenates them into a single 6-bit bitvector.
func GetC_ADDI_IMM(inst : bits(16)) => bits(6)
begin
  return [inst[12], inst[6:2]];
end

/// extracts bits [9:7] from the provided 16-bit bitvector.
func GetCA_RS1(inst : bits(16)) => bits(3)
begin
  return inst[9:7];
end

/// extracts bits [4:2] from the provided 16-bit bitvector.
func GetCA_RS2(inst : bits(16)) => bits(3)
begin
  return inst[4:2];
end

/// return true if the 25th bit of the provided 32-bit bitvector is 0b1.
func IsRelease(inst : bits(32)) => boolean
begin
  return inst[25] == '1';
end

/// return true if the 26th bit of the provided 32-bit bitvector is 0b1.
func IsAcquire(inst : bits(32)) => boolean
begin
  return inst[26] == '1';
end

/// extracts bits [14:12] from the provided 32-bit bitvector.
func GetRM(inst : bits(32)) => bits(3)
begin
  return inst[14:12];
end
