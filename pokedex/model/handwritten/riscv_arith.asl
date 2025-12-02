// wrapping addition, e.g. add, addi
func riscv_add{N}(x: bits(N), y: bits(N)) => bits(N)
begin
  return x + y;
end

// wrapping substraction, e.g. sub
func riscv_sub{N}(x: bits(N), y: bits(N)) => bits(N)
begin
  return x - y;
end

// logical and, e.g. and, andi
func riscv_and{N}(x: bits(N), y: bits(N)) => bits(N)
begin
  return x AND y;
end

// logical or, e.g. or, ori
func riscv_or{N}(x: bits(N), y: bits(N)) => bits(N)
begin
  return x OR y;
end

// logical xor, e.g. xor, xori
func riscv_xor{N}(x: bits(N), y: bits(N)) => bits(N)
begin
  return x XOR y;
end

// set if less than as unsigned, e.g. sltu, sltiu
func riscv_slt_u{N}(x: bits(N), y: bits(N)) => bits(N)
begin
  if UInt(x) < UInt(y) then
    return ZeroExtend('1', N);
  else
    return ZeroExtend('0', N);
  end
end

// set if less than as signed, e.g. sltu, sltiu
func riscv_slt_s{N}(x: bits(N), y: bits(N)) => bits(N)
begin
  if SInt(x) < SInt(y) then
    return ZeroExtend('1', N);
  else
    return ZeroExtend('0', N);
  end
end

// min value as signed, e.g. min, vmin.vv
func riscv_min_s{N}(x: bits(N), y: bits(N)) => bits(N)
begin
  if SInt(x) < SInt(y) then
    return x;
  else
    return y;
  end
end

// max value as signed, e.g. max, vmax.vv
func riscv_max_s{N}(x: bits(N), y: bits(N)) => bits(N)
begin
  if SInt(x) > SInt(y) then
    return x;
  else
    return y;
  end
end

// min value as unsigned, e.g. minu, vminu.vv
func riscv_min_u{N}(x: bits(N), y: bits(N)) => bits(N)
begin
  if UInt(x) < UInt(y) then
    return x;
  else
    return y;
  end
end

// max value as unsigned, e.g. maxu, vmaxu.vv
func riscv_max_u{N}(x: bits(N), y: bits(N)) => bits(N)
begin
  if UInt(x) > UInt(y) then
    return x;
  else
    return y;
  end
end


//////////////////////
// shift operations //
//////////////////////


// logical shift left
func riscv_sll{N}(x: bits(N), shamt: integer{0..N-1}) => bits(N)
begin
  return [x[N-1-shamt:0], Zeros(shamt)];
end

// logical shift left, only lower bits of y is used
func riscv_sll_var{N}(x: bits(N), y: bits(N)) => bits(N)
begin
  case N of
    when 8 => return riscv_sll(x, UInt(y[2:0]) as integer{0..N-1});
    when 16 => return riscv_sll(x, UInt(y[3:0]) as integer{0..N-1});
    when 32 => return riscv_sll(x, UInt(y[4:0]) as integer{0..N-1});
    when 64 => return riscv_sll(x, UInt(y[5:0]) as integer{0..N-1});
    otherwise => Unreachable();
  end
end

// logical shift right
func riscv_srl{N}(x: bits(N), shamt: integer{0..N-1}) => bits(N)
begin
  return [Zeros(shamt), x[N-1:shamt]];
end

// logical shift right, only lower bits of y is used
func riscv_srl_var{N}(x: bits(N), y: bits(N)) => bits(N)
begin
  case N of
    when 8 => return riscv_srl(x, UInt(y[2:0]) as integer{0..N-1});
    when 16 => return riscv_srl(x, UInt(y[3:0]) as integer{0..N-1});
    when 32 => return riscv_srl(x, UInt(y[4:0]) as integer{0..N-1});
    when 64 => return riscv_srl(x, UInt(y[5:0]) as integer{0..N-1});
    otherwise => Unreachable();
  end
end

// arithmetic shift right
func riscv_sra{N}(x: bits(N), shamt: integer{0..N-1}) => bits(N)
begin
  return ShiftRightArithmetic(x, shamt);

  // return SignExtend(x[N-1:shamt], N);
end

// arithmetic shift right, only lower bits of y is used
func riscv_sra_var{N}(x: bits(N), y: bits(N)) => bits(N)
begin
  case N of
    when 8 => return riscv_sra(x, UInt(y[2:0]) as integer{0..N-1});
    when 16 => return riscv_sra(x, UInt(y[3:0]) as integer{0..N-1});
    when 32 => return riscv_sra(x, UInt(y[4:0]) as integer{0..N-1});
    when 64 => return riscv_sra(x, UInt(y[5:0]) as integer{0..N-1});
    otherwise => Unreachable();
  end
end


/////////////////////////////////
// multiplication and division //
/////////////////////////////////


// wrapping multiplication, e.g. mul
func riscv_mul{N}(x: bits(N), y: bits(N)) => bits(N)
begin
  return x * y;
end

// upper bits of multiplication as signed, e.g. mulh
func riscv_mulh_ss{N}(x: bits(N), y: bits(N)) => bits(N)
begin
  return (SInt(x) * SInt(y))[2*N-1:N];
end

// upper bits of multiplication as unsigned, e.g. mulhu
func riscv_mulh_uu{N}(x: bits(N), y: bits(N)) => bits(N)
begin
  return (UInt(x) * UInt(y))[2*N-1:N];
end

// upper bits of multiplication with different sign, e.g. mulhsu
// x is signed, y is unsigned
func riscv_mulh_su{N}(x: bits(N), y: bits(N)) => bits(N)
begin
  return (SInt(x) * UInt(y))[2*N-1:N];
end

// NOTE : this is unused
// upper bits of multiplication with different sign, e.g. mulhsu
// x is signed, y is unsigned
// 
// func riscv_mulh_us{N}(x: bits(N), y: bits(N)) => bits(N)
// begin
//   return (UInt(x) * SInt(y))[2*N-1:N];
// end


// compute signed division in RISC_V rule, e.g. div
//
// divrem_s(x, 0) = (-1, x)
// divrem_s(SINT_MIN, -1) = (SINT_MIN, 0)
func riscv_div_s{N}(rs1: bits(N), rs2: bits(N)) => bits(N)
begin
  if IsZero(rs2) then
    // division by zero
    return Ones(N);
  elsif rs1 == ['1', Zeros(N-1)] && IsOnes(rs2) then
    return ['1', Zeros(N-1)];
  else
    // division: round to zero
    return (SInt(rs1) QUOT SInt(rs2))[N-1:0];
  end
end

// compute signed remainder in RISC_V rule, e.g. rem
func riscv_rem_s(rs1: bits(N), rs2: bits(N)) => bits(N)
begin
  if IsZero(rs2) then
    // division by zero
    return rs1;
  elsif rs1 == ['1', Zeros(N-1)] && IsOnes(rs2) then
    // overflow
    return Zeros(N);
  else
    // division: round to zero
    return (SInt(rs1) REM SInt(rs2))[N-1:0];
  end
end

// compute unsigned division in RISC_V rule, e.g. divu
//
// divrem_u(x, 0) = (UINT_MAX , x)
func riscv_div_u(rs1: bits(N), rs2: bits(N)) => bits(N)
begin
  if IsZero(rs2) then
    // division by zero
    return Ones(N);
  else
    // division: round to zero
    return (UInt(rs1) QUOT UInt(rs2))[N-1:0];
  end
end

// compute unsigned remainder in RISC_V rule, e.g. remu
func riscv_rem_u(rs1: bits(N), rs2: bits(N)) => bits(N)
begin
  if IsZero(rs2) then
    // division by zero
    return rs1;
  else
    // division: round to zero
    return (UInt(rs1) REM UInt(rs2))[N-1:0];
  end
end


////////////////////
// RVV operations //
////////////////////

// add with carry, e.g. vadc.vvm
func riscv_carryAdd{N}(x: bits(N), y: bits(N), carry: bit) => (bits(N), bit)
begin
  let res: bits(N+1) = ['0', x] + ['0', y] + ZeroExtend(carry, N+1);
  return (res[N-1:0], res[N]);
end

// sub with borrow, e.g. vsbc.vvm
func riscv_borrowSub{N}(x: bits(N), y: bits(N), borrow: bit) => (bits(N), bit)
begin
  let res: bits(N+1) = ['0', x] - ['0', y] - ZeroExtend(borrow, N+1);
  return (res[N-1:0], res[N]);
end


// wrapping substraction with swapped operand, e.g. vrsub_vi
func riscv_reverseSub{N}(x: bits(N), y: bits(N)) => bits(N)
begin
  return y - x;
end

// widening addition, treat both operands as signed, e.g. vwadd.vv
func riscv_widenAdd_s{N}(x: bits(N), y: bits(N)) => bits(2*N)
begin
  return (SInt(x) + SInt(y))[2*N-1:0];
end

// widening addition, treat both operands as unsigned, e.g. vwaddu.vv
func riscv_widenAdd_u{N}(x: bits(N), y: bits(N)) => bits(2*N)
begin
  return (UInt(x) + UInt(y))[2*N-1:0];
end

// widening substraction, treat both operands as signed, e.g. vwsub.vv
func riscv_widenSub_s{N}(x: bits(N), y: bits(N)) => bits(2*N)
begin
  return (SInt(x) - SInt(y))[2*N-1:0];
end

// widening substraction, treat both operands as unsigned, e.g. vwsubu.vv
func riscv_widenSub_u{N}(x: bits(N), y: bits(N)) => bits(2*N)
begin
  return (UInt(x) - UInt(y))[2*N-1:0];
end

// widening multiplication, treat both operands as signed, e.g. vwmul.vv
func riscv_widenMul_ss{N}(x: bits(N), y: bits(N)) => bits(2*N)
begin
  return (SInt(x) * SInt(y))[2*N-1:0];
end

// widening multiplication, treat both operands as unsigned, e.g. vwmulu.vv
func riscv_widenMul_uu{N}(x: bits(N), y: bits(N)) => bits(2*N)
begin
  return (UInt(x) * UInt(y))[2*N-1:0];
end

// widening multiplication, treat x as signed and y as unsigned, e.g. vwmulsu.vv
func riscv_widenMul_su{N}(x: bits(N), y: bits(N)) => bits(2*N)
begin
  return (SInt(x) * UInt(y))[2*N-1:0];
end

// widening multiplication, treat x as unsigned and y as signed, e.g. vwmaccus.vv
func riscv_widenMul_us{N}(x: bits(N), y: bits(N)) => bits(2*N)
begin
  return (UInt(x) * SInt(y))[2*N-1:0];
end

// wrapping signed average, rounding according to vxrm, ignore overflow in rounding.
// e.g. vaadd.vv
func riscv_averageAdd_s{N}(x: bits(N), y: bits(N), vxrm: bits(2)) => bits(N)
begin
  let res: bits(N) = ((SInt(x) + SInt(y)) DIVRM 2)[N-1:0];
  if x[0] != y[0] then
    return __vxrm_average_round(res, vxrm);
  else
    return res;
  end
end

// wrapping unsigned average, rounding according to vxrm, ignore overflow in rounding.
// e.g. vaaddu.vv
func riscv_averageAdd_u{N}(x: bits(N), y: bits(N), vxrm: bits(2)) => bits(N)
begin
  let res: bits(N) = ((UInt(x) + UInt(y)) DIVRM 2)[N-1:0];
  if x[0] != y[0] then
    return __vxrm_average_round(res, vxrm);
  else
    return res;
  end
end

// wrapping signed average substraction, rounding according to vxrm, ignore overflow in rounding.
// e.g. vasub.vv
func riscv_averageSub_s{N}(x: bits(N), y: bits(N), vxrm: bits(2)) => bits(N)
begin
  let res: bits(N) = ((SInt(x) - SInt(y)) DIVRM 2)[N-1:0];
  if x[0] != y[0] then
    return __vxrm_average_round(res, vxrm);
  else
    return res;
  end
end

// wrapping unsigned average substraction, rounding according to vxrm, ignore overflow in rounding.
// e.g. vasubu.vv
func riscv_averageSub_u{N}(x: bits(N), y: bits(N), vxrm: bits(2)) => bits(N)
begin
  let res: bits(N) = ((UInt(x) - UInt(y)) DIVRM 2)[N-1:0];
  if x[0] != y[0] then
    return __vxrm_average_round(res, vxrm);
  else
    return res;
  end
end

func __vxrm_average_round{N}(x: bits(N), vxrm: bits(2)) => bits(N)
begin
  case vxrm of
    when VXRM_RNU => return x + 1;
    when VXRM_RNE => return x + ZeroExtend(x[0], N);
    when VXRM_RDN => return x;
    when VXRM_ROD => return [x[N-1:1], '1'];
  end
end

func __vxrm_round(N: integer, x: bits(N+2), vxrm: bits(2)) => bits(N)
begin
  case vxrm of
    when VXRM_RNU => return x[N+1:2] + ZeroExtend(x[1], N);
    when VXRM_RNE => return x[N+1:2] + ZeroExtend(__round_to_even_addent(x[2:0]), N);
    when VXRM_RDN => return x[N+1:2];
    when VXRM_ROD => return [x[N+1:3], x[2] OR x[1] OR x[0]];
  end
end

func __round_to_even_addent(x: bits(3)) => bit
begin
  case x of
    when '000' => return '0';
    when '001' => return '0';
    when '010' => return '0';
    when '011' => return '1';
    when '100' => return '0';
    when '101' => return '0';
    when '110' => return '1';
    when '111' => return '1';
  end
end

// saturated signed addition, e.g. vsadd.vv
func riscv_saturateAdd_s{N}(x: bits(N), y: bits(N)) => (bits(N), boolean)
begin
  let res = SInt(x) + SInt(y);
    
  if res > UInt(Ones(N-1)) then
    return (['0', Ones(N-1)], TRUE);
  end
  if res < SInt(['1', Zeros(N-1)]) then
    return (['1', Zeros(N-1)], TRUE);
  end
  return (res[N-1:0], FALSE);
end

// saturated unsigned addition, e.g. vsadd.vv
func riscv_saturateAdd_u{N}(x: bits(N), y: bits(N)) => (bits(N), boolean)
begin
  let res = x + y;

  if UInt(res) < UInt(x) then
    return (Ones(N), TRUE);
  end

  return (res, FALSE);
end

// saturated signed substraction, e.g. vsadd.vv
func riscv_saturateSub_s{N}(x: bits(N), y: bits(N)) => (bits(N), boolean)
begin
  let res = SInt(x) - SInt(y);
    
  if res > UInt(Ones(N-1)) then
    return (['0', Ones(N-1)], TRUE);
  end
  if res < SInt(['1', Zeros(N-1)]) then
    return (['1', Zeros(N-1)], TRUE);
  end
  return (res[N-1:0], FALSE);
end

// saturated unsigned substraction, e.g. vsadd.vv
func riscv_saturateSub_u{N}(x: bits(N), y: bits(N)) => (bits(N), boolean)
begin
  if UInt(x) < UInt(y) then
    return (Zeros(N), TRUE);
  end

  return (x - y, FALSE);
end

func riscv_saturateSrl{N}(x: bits(N), y: integer{0..N-1}, vxrm: bits(2)) => bits(N)
begin
  var shifted_odd: bits(N+2) = ShiftRightLogical([x, '00'], y);
  if ShiftLeft(shifted_odd, y) != [x, '00'] then
    shifted_odd[0] = '1';
  end
  return __vxrm_round(N, shifted_odd, vxrm);
end

func riscv_saturateSra{N}(x: bits(N), y: integer{0..N-1}, vxrm: bits(2)) => bits(N)
begin
  var shifted_odd: bits(N+2) = ShiftRightArithmetic([x, '00'], y);
  if ShiftLeft(shifted_odd, y) != [x, '00'] then
    shifted_odd[0] = '1';
  end
  return __vxrm_round(N, shifted_odd, vxrm);
end

// rounding logical shift right, only lower bits of y is used, e.g. vssrl.vv
func riscv_saturateSrl_var{N}(x: bits(N), y: bits(N), vxrm: bits(2)) => bits(N)
begin
  case N of
    when 8 => return riscv_saturateSrl(x, UInt(y[2:0]) as integer{0..N-1}, vxrm);
    when 16 => return riscv_saturateSrl(x, UInt(y[3:0]) as integer{0..N-1}, vxrm);
    when 32 => return riscv_saturateSrl(x, UInt(y[4:0]) as integer{0..N-1}, vxrm);
    when 64 => return riscv_saturateSrl(x, UInt(y[5:0]) as integer{0..N-1}, vxrm);
    otherwise => Unreachable();
  end
end

// rounding arithmetic shift right, only lower bits of y is used, e.g. vssra.vv
func riscv_saturateSra_var{N}(x: bits(N), y: bits(N), vxrm: bits(2)) => bits(N)
begin
  case N of
    when 8 => return riscv_saturateSra(x, UInt(y[2:0]) as integer{0..N-1}, vxrm);
    when 16 => return riscv_saturateSra(x, UInt(y[3:0]) as integer{0..N-1}, vxrm);
    when 32 => return riscv_saturateSra(x, UInt(y[4:0]) as integer{0..N-1}, vxrm);
    when 64 => return riscv_saturateSra(x, UInt(y[5:0]) as integer{0..N-1}, vxrm);
    otherwise => Unreachable();
  end
end

// rounding shifted signed multiplication, the shift width is (N-1), e.g. vsmul.vv
func riscv_saturateMul_ss{N}(x: bits(N), y: bits(N), vxrm: bits(2)) => (bits(N), boolean)
begin
  if x == ['1', Zeros(N-1)] && y == ['1', Zeros(N-1)] then
    // x == y == sN::MIN will overflow.
    // This is the only case overflow will happen
    return (['0', Ones(N-1)], TRUE);
  end

  let prod: bits(2*N) = SignExtend(x, 2*N) * SignExtend(y, 2*N);

  var prod_shifted_odd: bits(N+2) = prod[2*N-2:N-3];
  if !IsZero(prod[0 +: N-3]) then
    prod_shifted_odd[0] = '1';
  end

  return (__vxrm_round(N, prod_shifted_odd, vxrm), FALSE);
end
