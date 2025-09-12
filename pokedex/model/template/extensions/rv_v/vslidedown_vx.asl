// vslidedown.vx vd, vs2, rs1, vm
// eew(vd, vs2) = sew, rs1 is X[rs1] treat as unsigned
// vd[i] = vs2[i+rs1], optionally masked by vm, vm is mask for vd
// NOTE: read from vs2 could be beyond vl

let vd : VREG_TYPE = UInt(GetRD(instruction));
let vs2 : VREG_TYPE = UInt(GetRS2(instruction));
let rs1 : XREG_TYPE = UInt(GetRS1(instruction));
let vm : bit = GetVM(instruction);

if VTYPE.ill then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

// uarch
if UInt(VSTART) != 0 then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

let sew = VTYPE.sew;
let lmul = VTYPE.lmul;
let vlmax = VLMAX;
let vl = VL;

if invalid_vreg(lmul, vd) then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

if invalid_vreg(lmul, vs2) then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

if vm == '0' && vd == 0 then
  // overlap with mask
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

// vslidedown allow overlap vd with vs2

let offset = UInt(X[rs1]);

// always undisturbed
// TOOD: support agnostic to reduce VRF read
case sew of
  when 8 => begin
    for idx = 0 to vl - 1 do
      if vm != '0' || V0_MASK[idx] then
        var src : bits(8) = Zeros(8);
        if idx + offset < vlmax then
          src = VRF_8[vs2, idx + offset];
        end
        
        VRF_8[vd, idx] = src;
      end
    end
  end

  when 16 => begin
    for idx = 0 to vl - 1 do
      if vm != '0' || V0_MASK[idx] then
        var src : bits(16) = Zeros(16);
        if idx + offset < vlmax then
          src = VRF_16[vs2, idx + offset];
        end
        
        VRF_16[vd, idx] = src;
      end
    end
  end

  when 32 => begin
    for idx = 0 to vl - 1 do
      if vm != '0' || V0_MASK[idx] then
        var src : bits(32) = Zeros(32);
        if idx + offset < vlmax then
          src = VRF_32[vs2, idx + offset];
        end
        
        VRF_32[vd, idx] = src;
      end
    end
  end
  
  otherwise => assert FALSE; // TODO
end

ClearVSTART();

PC = PC + 4;

return Retired();
