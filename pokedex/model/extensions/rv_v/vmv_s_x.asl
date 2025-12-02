// vmv.s.x vd, rs1
// eew(vd) = sew, emul(vd) = 1
// compute vd[0] = X[rs1], truncate/sext
func Execute_VMV_S_X(instruction: bits(32)) => Result
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

  let vd: VRegIdx = UInt(GetRD(instruction));
  let rs1: XRegIdx = UInt(GetRS1(instruction));

  let sew: integer{8, 16, 32, 64} = VTYPE.sew;
  // This instruction explicitly ignores lmul

  // do nothing if vl == 0
  if VL != 0 then
    case sew of
      when 8 => begin
        let src : bits(8) = SInt(X[rs1])[7:0];
        VRF_8[vd, 0] = src;
      end

      when 16 => begin
        let src : bits(16) = SInt(X[rs1])[15:0];
        VRF_16[vd, 0] = src;
      end

      when 32 => begin
        let src : bits(32) = SInt(X[rs1])[31:0];
        VRF_32[vd, 0] = src;
      end
      
      when 64 => Todo("support sew=64");

      otherwise => Unreachable();
    end
  end

  logWrite_VREG_1(vd);

  makeDirty_VS();
  clear_VSTART();
  PC = PC + 4;
  return Retired();
end
