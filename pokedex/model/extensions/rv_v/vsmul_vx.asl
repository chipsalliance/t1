
// vsmul.vx vd, vs2, rs1, vm; vm mask is optional
// eew(vd, vs2) = sew, w(src1) = sew, src1 is truncate from X[rs1]
//
// NOTE: this instruction uses VXRM.
// NOTE: this instruction may accure to VXSAT.
//
// NOTE: vsmul does not need to support SEW=64

func Execute_VSMUL_VX(instruction: bits(32)) => Result
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
  let vs2: VRegIdx = UInt(GetRS2(instruction));
  let rs1: XRegIdx = UInt(GetRS1(instruction));
  let vm: bit = GetVM(instruction);

  let vl: integer = VL;
  let sew: integer{8, 16, 32, 64} = VTYPE.sew;
  let vreg_align: integer{1, 2, 4, 8} = getAlign(VTYPE);
  let vxrm: bits(2) = VXRM;

  if vm == '0' && vd == 0 then
    // overlap with mask
    return IllegalInstruction();
  end
  if vd MOD vreg_align != 0 then
    // vd is not aligned with lmul group
    return IllegalInstruction();
  end
  if vs2 MOD vreg_align != 0 then
    // vs2 is not aligned with elmul group
    return IllegalInstruction();
  end

  case sew of
    when 8 => begin
      let src1 : bits(8) = (X[rs1])[7:0];

      for idx = 0 to vl - 1 do
        if vm != '0' || V0_MASK[idx] then
          let src2 : bits(8) = VRF_8[vs2, idx];
          let (res: bits(8), sat: boolean) = riscv_saturateMul_ss(src2, src1, vxrm);
          VRF_8[vd, idx] = res;
          if sat then
            VXSAT = '1';
          end
        end
      end
    end

    when 16 => begin
      let src1 : bits(16) = (X[rs1])[15:0];

      for idx = 0 to vl - 1 do
        if vm != '0' || V0_MASK[idx] then
          let src2 : bits(16) = VRF_16[vs2, idx];
          let (res: bits(16), sat: boolean) = riscv_saturateMul_ss(src2, src1, vxrm);
          VRF_16[vd, idx] = res;
          if sat then
            VXSAT = '1';
          end
        end
      end
    end

    when 32 => begin
      let src1 : bits(32) = (X[rs1])[31:0];

      for idx = 0 to vl - 1 do
        if vm != '0' || V0_MASK[idx] then
          let src2 : bits(32) = VRF_32[vs2, idx];
          let (res: bits(32), sat: boolean) = riscv_saturateMul_ss(src2, src1, vxrm);
          VRF_32[vd, idx] = res;
          if sat then
            VXSAT = '1';
          end
        end
      end
    end

    // NOTE: vsmul does not need to support SEW=64
    when 64 => Todo("support sew=64");

    otherwise => Unreachable();
  end

  logWrite_VCSR();
  logWrite_VREG_elmul(vd, vreg_align);

  makeDirty_VS();
  clear_VSTART();
  PC = PC + 4;
  return Retired();
end
