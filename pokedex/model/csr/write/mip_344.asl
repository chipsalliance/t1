var valid : bits(32) = Zeros(32);
valid[MIP_MSIP] = value[MIP_MSIP];
valid[MIP_MTIP] = value[MIP_MTIP];
valid[MIP_MEIP] = value[MIP_MEIP];

MIP = valid;
