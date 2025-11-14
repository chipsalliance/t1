func Execute_VSM_V(instruction: bits(32)) => Result
begin
  if !isEnabled_VS() then
    return IllegalInstruction();
  end
  if VTYPE.ill then
    return IllegalInstruction();
  end

  let evl: integer = DivCeil(VL, 8);

  // NOTE: this instruction supports non-zero vstart
  if UInt(VSTART) > evl then
    return IllegalInstruction();
  end

  let vs3: VRegIdx = UInt(GetRD(instruction));
  let rs1: XRegIdx = UInt(GetRS1(instruction));

  let base_addr: bits(XLEN) = X[rs1];
  let vstart: integer = UInt(VSTART);

  for idx = vstart to evl - 1 do
    let addr: bits(XLEN) = base_addr + idx;
    let data: bits(8) = VRF_8[vs3, idx];
    let result = WriteMemory(addr, data);

    if !result.is_ok then
      vectorInterrupt(idx);
      return result;
    end
  end

  // no makeDirty_VS;
  clear_VSTART();
  PC = PC + 4;
  return Retired();
end