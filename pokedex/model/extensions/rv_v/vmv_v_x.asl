// vmv.v.x vd, rs1
// eew(vd, vs1) = sew, w(rs1) = sew, rs1 is sext/truncated from X[rs1]
// compute vd[i] = rs1

let vd : VREG_TYPE = UInt(GetRD(instruction));
let rs1 : XREG_TYPE = UInt(GetRS1(instruction));

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
    let src = SInt(X[rs1])[7:0];

    for idx = 0 to vl - 1 do
      VRF_8[vd, idx] = src;
    end
  end

  when 16 => begin
    let src = SInt(X[rs1])[15:0];

    for idx = 0 to vl - 1 do
      VRF_16[vd, idx] = src;
    end
  end

  when 32 => begin
    let src = SInt(X[rs1])[31:0];

    for idx = 0 to vl - 1 do
      VRF_32[vd, idx] = src;
    end
  end
  
  otherwise => assert FALSE; // TODO
end

ClearVSTART();

PC = PC + 4;

return Retired();
