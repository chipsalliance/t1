let rd : integer{0..31} = UInt(GetRD(instruction));

let csr_addr : bits(12) = GetCSR(instruction);

if rd != 0 then
  let csr_read : Result = ReadCSR(csr_addr);
  if !csr_read.is_ok then
    return Exception(CAUSE_ILLEGAL_INSTRUCTION, instruction);
  end

  X[rd] = csr_read.value;
end

let rs1 : integer{0..31}  = UInt(GetRS1(instruction));
let csr_write : Result = WriteCSR(csr_addr, X[rs1]);
if !csr_write.is_ok then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, instruction);
end

PC = PC + 4;
return Retired();
