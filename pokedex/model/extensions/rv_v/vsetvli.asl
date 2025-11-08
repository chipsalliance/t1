func Execute_VSETVLI(instruction: bits(32)) => Result
begin
  if !isEnabled_VS() then
    return IllegalInstruction();
  end

  // rd=new_vl, rs1=AVL, imm=new_vtype
  let rd  : XRegIdx = UInt(GetRD(instruction));
  let rs1 : XRegIdx = UInt(GetRS1(instruction));
  let uimm : bits(11) = instruction[30:20];

  let src1: bits(XLEN) = X[rs1];

  let new_vtype_bits = ZeroExtend(uimm, XLEN);
  let new_vtype : VTYPE_TYPE = VTYPE_from_bits(new_vtype_bits);

  if new_vtype.ill then
    VTYPE = VTYPE_ILL;
    VL = 0;
  elsif rd == 0 && rs1 == 0 then
    // sew/lmul ratio is unchanged iff vlmax is unchanged
    if !VTYPE.ill && VLMAX == __compute_vlmax(new_vtype) then
      VTYPE = new_vtype;
      // VL is unchanged
    else
      VTYPE = VTYPE_ILL;
      VL = 0;
    end
  else
    VTYPE = new_vtype;

    VL = VLMAX;
    if rs1 != 0 then
      if UInt(src1) < VL then
        VL = UInt(src1);
      end
    end
  end

  X[rd] = VL[31:0];

  logWrite_VTYPE_VL();

  ClearVSTART();
  PC = PC + 4;
  return Retired();
end
