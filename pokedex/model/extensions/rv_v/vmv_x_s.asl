// vmv.x.s rd, vs2
// eew(vs1) = sew, emul(vs1) = 1
// compute rd = vs2[0], truncate/sext
func Execute_VMV_X_S(instruction: bits(32)) => Result
begin
  if !isEnabled_VS() then
    return IllegalInstruction();
  end
  if VTYPE.ill then
    return IllegalInstruction();
  end
  if !IsZero(VSTART) then
    return IllegalInstruction();
  end

  let rd: XRegIdx = UInt(GetRD(instruction));
  let vs2: VRegIdx = UInt(GetRS2(instruction));

  let sew: integer{8, 16, 32, 64} = VTYPE.sew;
  // This instruction explicitly ignores lmul

  case sew of
    when 8 => begin
      let src : bits(8) = VRF_8[vs2, 0];
      X[rd] = SInt(src)[XLEN-1:0];
    end

    when 16 => begin
      let src : bits(16) = VRF_16[vs2, 0];
      X[rd] = SInt(src)[XLEN-1:0];
    end

    when 32 => begin
      let src : bits(32) = VRF_32[vs2, 0];
      X[rd] = SInt(src)[XLEN-1:0];
    end
    
    when 64 => Todo("support sew=64");

    otherwise => Unreachable();
  end

  // no makeDirty_VS;
  clear_VSTART();
  PC = PC + 4;
  return Retired();
end
