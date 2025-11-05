/// Result stores common metadata to record status of an execution. A successful
/// execution will return a `Result` with `is_ok` set to `TRUE`, `value` set with
/// value for next operation or zeros, and `cause` field set with value -1.
///
/// A fail execution will return a `Result` with `is_ok` set to `FALSE`, `value`
/// set to trap value, and `cause` field set to the exception cause.
record Result {
  cause : integer;
  value : bits(32);
  is_ok : boolean;
};

/// Ok return a Result with provided `value`. The returned result
/// has `is_ok` set to true and `cause` set to -1.
func Ok(value : bits(32)) => Result
begin
  return Result {
    cause = -1,
    value = value,
    is_ok = TRUE
  };
end

// TODO : remove OK, use Ok instead
func OK(value : bits(32)) => Result
begin
  return Ok(value);
end

/// Exception return a Result with provided `cause` and `trap_value`.
/// The returned result has `is_ok` set to false.
/// The returned Result is always of 32 bits length. A trap_value longer
/// then 32 bits will only be kept with least significant 32 bits. A
/// trap_value smaller than 32-bits will be zero extended to 32 bits.
func Exception(cause : integer, trap_value : bits(N)) => Result
begin
  if N >= 32 then
    return Result {
      cause = cause,
      value = trap_value[31:0],
      is_ok = FALSE
    };
  else
    return Result {
      cause = cause,
      value = ZeroExtend(trap_value, 32),
      is_ok = FALSE
    };
  end
end

/// Retired a Result with `cause` set to -1, `value` set to zeros, and
/// `is_ok` set to true.
func Retired() => Result
begin
  return Result {
    cause = -1,
    value = Zeros(32),
    is_ok = TRUE
  };
end

func IllegalInstruction() => Result
begin
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end
