var valid : bits(32) = Zeros(32);
valid[MIE_MSIE] = value[MIE_MSIE];
valid[MIE_MTIE] = value[MIE_MTIE];
valid[MIE_MEIE] = value[MIE_MEIE];

MIE = valid;
