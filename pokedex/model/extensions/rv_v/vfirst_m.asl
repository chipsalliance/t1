// vfirst_m rd, vs2, vm
// eew(vs2) = 1, vs2 is mask
// write the index of the first one in vs2 (-1 for unfound), write to X[rd], optionally masked by vm
func Execute_VFIRST_M(instruction: bits(32)) => Result
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

  var first: integer = -1;

  // TODO: workaround for ASL do not have break
  var idx = 0;
  while first == -1 && idx < vl do
    if vm != '0' || V0_MASK[idx] then
      if (VRF_MASK[vs2, idx]) as boolean then
        first = idx;
      end
    end
    
    idx = idx + 1;
  end

  X[rd] = first[XLEN-1:0];

  // no makeDirty_VS
  clear_VSTART();
  PC = PC + 4;
  return Retired();
end
