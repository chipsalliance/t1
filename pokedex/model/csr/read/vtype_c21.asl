if VTYPE.ill then
    return OK(['1', Zeros(31)]);
else
    return OK([
        Zeros(24),
        VTYPE.ma,   // [7]
        VTYPE.ta,   // [6]
        VTYPE.sew,  // [5:3]
        VTYPE.lmul  // [2:0]
    ]);
end
