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

/// OK return a Result with provided `value`. The returned result
/// has `is_ok` set to true and `cause` set to -1.
func OK(value : bits(32)) => Result
begin
  return Result {
    cause = -1,
    value = value,
    is_ok = TRUE
  };
end

/// Exception return a Result with provided `cause` and `trap_value`.
/// The returned result has `is_ok` set to false.
func Exception(cause : integer, trap_value : bits(32)) => Result
begin
  return Result {
    cause = cause,
    value = trap_value,
    is_ok = FALSE
  };
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
