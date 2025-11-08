let rs1 : integer{0..31} = UInt(GetCR_RS1(instruction));

// FIXME : it should be unreachable after the decoder supports decoding priority
if rs1 == 0 then
  return Execute_C_EBREAK(instruction);
end

X[1] = PC + 2;
PC = X[rs1];

return Retired();
