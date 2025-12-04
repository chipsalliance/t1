func Execute_SC_W(instruction: bits(32)) => Result
begin
  let rd  : XRegIdx = UInt(GetRD(instruction));
  let rs1 : XRegIdx = UInt(GetRS1(instruction));
  let rs2 : XRegIdx = UInt(GetRS2(instruction));

  let addr : bits(XLEN) = X[rs1];

  // NOTE: impl defined behavior
  // we do not emulate misaligned LR/SC, thus we raise access fault exception
  if addr[1:0] != '00' then
    return ExceptionMemory(CAUSE_STORE_ACCESS, addr);
  end

  var value : bits(XLEN);
  if FFI_store_conditional(addr, X[rs2]) then
    // success
    value = ZeroExtend('0', XLEN);
  else
    // failure
    value = ZeroExtend('1', XLEN);
  end

  X[rd] = value;

  PC = PC + 4;
  return Retired();
end
