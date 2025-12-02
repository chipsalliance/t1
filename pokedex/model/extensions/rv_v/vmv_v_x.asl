// vmv.v.x vd, rs1
// eew(vd, vs1) = sew, w(rs1) = sew, rs1 is sext/truncated from X[rs1]
// compute vd[i] = X[rs1]
func Execute_VMV_V_X(instruction: bits(32)) => Result
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

  let vl: integer = VL;
  let sew: integer{8, 16, 32, 64} = VTYPE.sew;
  let vreg_align: integer{1, 2, 4, 8} = getAlign(VTYPE);

  if vd MOD vreg_align != 0 then
    // vd is not aligned with lmul group
    return IllegalInstruction();
  end

  case sew of
    when 8 => begin
      let src: bits(8) = SInt(X[rs1])[7:0];
      for idx = 0 to vl - 1 do
        VRF_8[vd, idx] = src;
      end
    end

    when 16 => begin
      let src: bits(16) = SInt(X[rs1])[15:0];
      for idx = 0 to vl - 1 do
        VRF_16[vd, idx] = src;
      end
    end

    when 32 => begin
      let src: bits(32) = SInt(X[rs1])[31:0];
      for idx = 0 to vl - 1 do
        VRF_32[vd, idx] = src;
      end
    end
    
    when 64 => Todo("support sew=64");

    otherwise => Unreachable();
  end

  logWrite_VREG_elmul(vd, vreg_align);

  makeDirty_VS();
  clear_VSTART();
  PC = PC + 4;
  return Retired();
end
