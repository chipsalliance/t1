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


// wrapping substraction with swapped operand, e.g. vrsub_vi
func riscv_reverseSub{N}(x: bits(N), y: bits(N)) => bits(N)
begin
  return y - x;
end
