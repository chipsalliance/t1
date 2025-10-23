// todo: when implementing supervisor mode, csrrs need to carefully handle special
// csr like mip.seip

let csr : bits(12) = GetCSR(instruction);
let csr_read : Result = ReadCSR(csr);
if !csr_read.is_ok then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, instruction);
end

let rd : integer{0..31} = UInt(GetRD(instruction));
X[rd] = csr_read.value;

let rs1 : integer{0..31} = UInt(GetRS1(instruction));
if rs1 != 0 then
  PC = PC + 4;
  return WriteCSR(csr, csr_read.value OR X[rs1]);
end

PC = PC + 4;
return Retired();
