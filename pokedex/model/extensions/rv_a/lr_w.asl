func Execute_LR_W(instruction: bits(32)) => Result
begin
  let rd  : XRegIdx = UInt(GetRD(instruction));
  let rs1 : XRegIdx = UInt(GetRS1(instruction));

  let addr : bits(XLEN) = X[rs1];

  // NOTE: impl defined behavior
  // we do not emulate misaligned LR/SC, thus we raise access fault exception
  if addr[1:0] != '00' then
    return ExceptionMemory(CAUSE_LOAD_ACCESS, addr);
  end

  let result = FFI_load_reserved(addr);
  if !result.success then
    return ExceptionMemory(CAUSE_LOAD_ACCESS, addr);
  end

  X[rd] = SignExtend(result.data, XLEN);

  PC = PC + 4;
  return Retired();
end
