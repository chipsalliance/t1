var tmp : bits(32) = Zeros(32);
tmp[7] = MTIE;
tmp[11] = MEIE;
return tmp;
