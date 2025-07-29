// vcpop_m rd, vs2, vm
// eew(vs2) = 1, vs2 is mask
// count ones in vs2, write to X[rd], optionally masked by vm

let rd : XREG_TYPE = UInt(GetRD(instruction));
let vs2 : VREG_TYPE = UInt(GetRS2(instruction));
let vm : bit = GetVM(instruction);

if VTYPE.ill then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

if UInt(VSTART) != 0 then
  // explicitly required by the instruction
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

let vl = VL;

var count : integer = 0;

for idx = 0 to vl - 1 do
  if vm != '0' || V0_MASK[idx] then
    if (VRF_MASK[vs2, idx]) as boolean then
      count = count + 1;
    end
  end
end

X[rd] = count[31:0];

ClearVSTART();

PC = PC + 4;

return Retired();
