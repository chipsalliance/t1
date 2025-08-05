// vmv.x.s rd, vs2
// eew(vs1) = sew, emul(vs1) = 1
// compute rd = vs2[0], truncate/sext

let rd : XREG_TYPE = UInt(GetRD(instruction));
let vs2 : VREG_TYPE = UInt(GetRS2(instruction));

if VTYPE.ill then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

// uarch
if UInt(VSTART) != 0 then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

let sew = VTYPE.sew;

// This instruction explicitly ignores lmul

// always undisturbed
// TOOD: support agnostic to reduce VRF read
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
  
  otherwise => assert FALSE; // TODO
end

ClearVSTART();

PC = PC + 4;

return Retired();
