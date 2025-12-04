
func Execute_VSETIVLI(instruction: bits(32)) => Result
begin 
  if !isEnabled_VS() then
    return IllegalInstruction();
  end

  // rd=new_vl, rs1(imm)=AVL, imm=new_vtype
  let rd  : XRegIdx = UInt(GetRD(instruction));
  let uimm_avl : integer{0..31} = UInt(GetRS1(instruction));
  let uimm : bits(10) = instruction[29:20];

  let new_vtype_bits = ZeroExtend(uimm, XLEN);
  let new_vtype : VType = VTYPE_from_bits(new_vtype_bits);

  if new_vtype.ill then
    VTYPE = VTYPE_ILL;
    VL = 0;
  else
    VTYPE = new_vtype;

    VL = VLMAX;
    if uimm_avl < VL then
      VL = uimm_avl;
    end
  end

  X[rd] = VL[31:0];

  makeDirty_VS();

  logWrite_VTYPE_VL();

  clear_VSTART();
  PC = PC + 4;
  return Retired();
end