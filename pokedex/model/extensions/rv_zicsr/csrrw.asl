func Execute_CSRRW(instruction: bits(32)) => Result
begin
  let csr : bits(12) = GetCSR(instruction);
  let rd : XRegIdx = UInt(GetRD(instruction));
  let rs1 : XRegIdx  = UInt(GetRS1(instruction));

  let src1: bits(XLEN) = X[rs1];

  // only perform read when rd != 0
  var csr_read : Result = Ok(Zeros(XLEN));
  if rd != 0 then
    csr_read = ReadCSR(csr);
    if !csr_read.is_ok then
      return csr_read;
    end
  end

  let csr_write : Result = WriteCSR(csr, src1);
  if !csr_write.is_ok then
    return csr_write;
  end

  X[rd] = csr_read.value;

  PC = PC + 4;
  return Retired();
end
