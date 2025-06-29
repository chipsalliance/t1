let rd : integer{0..31} = UInt(GetRD(instruction));

let csr_addr : bits(12) = GetCSR(instruction);

if rd != 0 then
  let csr_read : Result = ReadCSR(csr_addr);
  if !csr_read.is_ok then
    return Exception(CAUSE_ILLEGAL_INSTRUCTION, instruction);
  end

  X[rd] = csr_read.value;

end

let uimm : bits(5) = GetRS1(instruction);
let uimm_ext : bits(32)  = ZeroExtend(uimm, 32);
let csr_write : Result = WriteCSR(csr_addr, uimm_ext);
if !csr_write.is_ok then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, instruction);
end

PC = PC + 4;
return Retired();
