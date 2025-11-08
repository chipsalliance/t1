let rd : integer{0..31} = UInt(GetCR_RS1(instruction));
let rs2 : integer{0..31} = UInt(GetCR_RS2(instruction));

// FIXME : it should be unreachable after the decoder supports decoding priority
if rs2 == 0 then
  return Execute_C_JR(instruction);
end

if rd != 0 then
  X[rd] = X[rs2];
end

PC = PC + 2;
return Retired();
