// vfirst_m rd, vs2, vm
// eew(vs2) = 1, vs2 is mask
// write the index of the first one in vs2 (-1 for unfound), write to X[rd], optionally masked by vm

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

var first : integer = -1;

// TODO: workaround for ASL do not have break
var idx = 0;
while first == -1 && idx < vl do
  if vm != '0' || V0_MASK[idx] then
    if (VRF_MASK[vs2, idx]) as boolean then
      first = idx;
    end
  end
  
  idx = idx + 1;
end

X[rd] = first[31:0];

ClearVSTART();

PC = PC + 4;

return Retired();
