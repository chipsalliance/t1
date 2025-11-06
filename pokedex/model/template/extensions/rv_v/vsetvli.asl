// rd=new_vl, rs1=AVL, imm=new_vtype

if !isEnabled_VS() then
  return IllegalInstruction();
end

let rs1 : integer{0..31} = UInt(GetRS1(instruction));
let rd  : integer{0..31} = UInt(GetRD(instruction));

let new_vtype_bits = [Zeros(21), instruction[30:20]];
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
    let src1 : integer = UInt(X[rs1]);
    if src1 < VL then
      VL = src1;
    end
  end
end

X[rd] = VL[31:0];

logWrite_VTYPE_VL();

ClearVSTART();

PC = PC + 4;

return Retired();
