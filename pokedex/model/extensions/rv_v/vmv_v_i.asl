// vmv.v.i vd, imm
// eew(vd) = sew, w(imm) = sew, imm is sext from imm5
// compute vd[i] = imm

let vd : VREG_TYPE = UInt(GetRD(instruction));
let imm5 : bits(5) = GetRS1(instruction);

if VTYPE.ill then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

let sew = VTYPE.sew;
let lmul = VTYPE.lmul;
let vlmax = VLMAX;
let vl = VL;

if invalid_vreg(lmul, vd) then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

// uarch
if UInt(VSTART) != 0 then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

// always undisturbed
// TOOD: support agnostic to reduce VRF read
case sew of
  when 8 => begin
    let src = SInt(imm5)[7:0];

    for idx = 0 to vl - 1 do
      VRF_8[vd, idx] = src;
    end
  end

  when 16 => begin
    let src = SInt(imm5)[15:0];

    for idx = 0 to vl - 1 do
      VRF_16[vd, idx] = src;
    end
  end

  when 32 => begin
    let src = SInt(imm5)[31:0];

    for idx = 0 to vl - 1 do
      VRF_32[vd, idx] = src;
    end
  end
  
  otherwise => assert FALSE; // TODO
end

ClearVSTART();

PC = PC + 4;

return Retired();
