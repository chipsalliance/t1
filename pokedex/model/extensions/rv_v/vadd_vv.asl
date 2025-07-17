let vd : VREG_TYPE = UInt(GetRD(instruction));
let vs2 : VREG_TYPE = UInt(GetRS2(instruction));
let vs1 : VREG_TYPE = UInt(GetRS1(instruction));

if VTYPE.ill then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

let sew = VTYPE.sew;
let lmul = VTYPE.lmul;
let vlmax = VLMAX;
let vl = VL;

if !valid_with_lmul3(VTYPE.lmul, vd, vs2, vs1) then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

if UInt(VSTART) != 0 then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

case sew of
  when 8 => begin
    for idx = 0 to vl - 1 do
        let src2 = VRF_8[vs2, idx];
        let src1 = VRF_8[vs1, idx];

        let res = src2 + src1;

        VRF_8[vd, idx] = res;
    end
  end

  when 16 => begin
    for idx = 0 to vl - 1 do
        let src2 = VRF_16[vs2, idx];
        let src1 = VRF_16[vs1, idx];

        let res = src2 + src1;

        VRF_16[vd, idx] = res;
    end
  end

  when 32 => begin
    for idx = 0 to vl - 1 do
        let src2 = VRF_32[vs2, idx];
        let src1 = VRF_32[vs1, idx];

        let res = src2 + src1;

        VRF_32[vd, idx] = res;
    end
  end
  
  otherwise => assert FALSE; // TODO
end

ClearVSTART();

PC = PC + 4;

return Retired();
