let csr : bits(12) = GetArg_CSR(instruction);
let csr_read : Result = ReadCSR(csr);
if !csr_read.is_ok then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, instruction);
end

let rd : integer{0..31} = UInt(GetArg_RD(instruction));
X[rd] = csr_read.value;

let rs1 : integer{0..31} = UInt(GetArg_RS1(instruction));
if rs1 != 0 then
  return WriteCSR(csr, csr_read.value AND (NOT X[rs1]));
end

PC = PC + 4;
return Retired();
