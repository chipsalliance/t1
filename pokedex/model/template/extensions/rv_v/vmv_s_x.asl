// vmv.s.x vd, rs1
// eew(vd) = sew, emul(vs1) = 1
// compute vd[0] = rs1, truncate/sext

let vd : VREG_TYPE = UInt(GetRD(instruction));
let rs1 : XREG_TYPE = UInt(GetRS1(instruction));

if VTYPE.ill then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

// uarch
if UInt(VSTART) != 0 then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

let sew = VTYPE.sew;

// This instruction explicitly ignores lmul

// do nothing if vl == 0
if VL == 0 then
  // always undisturbed
  // TOOD: support agnostic to reduce VRF read
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
    
    otherwise => assert FALSE; // TODO
  end
end

ClearVSTART();

PC = PC + 4;

return Retired();
