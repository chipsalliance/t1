func Execute_CSRRW(instruction: bits(32)) => Result
begin
  let csr : bits(12) = GetCSR(instruction);
  let rd : XRegIdx = UInt(GetRD(instruction));
  let rs1 : XRegIdx  = UInt(GetRS1(instruction));

  let src1: bits(XLEN) = X[rs1];

  // only perform read when rd != 0
  var read_data : bits(XLEN) = Zeros(XLEN);
  if rd != 0 then
    let (rdata, csr_read) = asTupleCsrRead(ReadCSR(csr));
    if !csr_read.is_ok then
      return csr_read;
    end
    read_data = rdata;
  end

  let csr_write : Result = WriteCSR(csr, src1);
  if !csr_write.is_ok then
    return csr_write;
  end

  X[rd] = read_data;

  PC = PC + 4;
  return Retired();
end
