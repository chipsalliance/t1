// vcpop_m rd, vs2, vm
// eew(vs2) = 1, vs2 is mask
// count ones in vs2, write to X[rd], optionally masked by vm
func Execute_VCPOP_M(instruction: bits(32)) => Result
begin
  if !isEnabled_VS() then
    return IllegalInstruction();
  end
  if VTYPE.ill then
    return IllegalInstruction();
  end
  if !IsZero(VSTART) then
    // explicitly required by the instruction
    return IllegalInstruction();
  end

  let rd: XRegIdx = UInt(GetRD(instruction));
  let vs2: VRegIdx = UInt(GetRS2(instruction));
  let vm: bit = GetVM(instruction);

  let vl: integer = VL;

  var count : integer = 0;

  for idx = 0 to vl - 1 do
    if vm != '0' || V0_MASK[idx] then
      if (VRF_MASK[vs2, idx]) as boolean then
        count = count + 1;
      end
    end
  end

  X[rd] = count[XLEN-1:0];

  // no makeDirty_VS
  clear_VSTART();
  PC = PC + 4;
  return Retired();
end
