let csr : bits(12) = GetCSR(instruction);
let csr_read : Result = ReadCSR(csr);
if !csr_read.is_ok then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, instruction);
end

let rd : integer{0..31} = UInt(GetRD(instruction));
X[rd] = csr_read.value;

let uimm : bits(5) = GetRS1(instruction);
let uimm_ext : bits(32) = ZeroExtend(uimm, 32);
if !(IsZero(uimm_ext)) then
  return WriteCSR(csr, csr_read.value AND (NOT uimm_ext));
end

PC = PC + 4;
return Retired();
