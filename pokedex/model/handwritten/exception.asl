// The `Result` structure indicates whether an operation triggered an exception.
//
// - When `is_ok` is `TRUE`, the operation was successful.
// - When `is_ok` is `FALSE`, the operation failed, and the structure contains
//   necessary exception details (such as the `cause`).
//
// If an exception does not require a `payload`, the field is typically set to
// zero. However, developers should treat the `payload` as undefined in these
// cases and ignore it.
record Result {
  is_ok : boolean;
  cause : integer{0..31};
  payload : bits(XLEN);
};

// OK is a constant instance value always indicate successful operation.
let OK: Result = Result {
  is_ok = TRUE,
  cause = 0,
  payload = Zeros(XLEN)
};


/// Return a `Result` indicating success(`is_ok` is `TRUE`).
/// Other fields are undefined.
func Retired() => Result
begin
  return OK;
end

/// Return an error `Result` with `cause` set to illegal
/// instruction exception code. `payload` is undefined.
func IllegalInstruction() => Result
begin
  return Result {
    is_ok = FALSE,
    cause = XCPT_CODE_ILLEGAL_INSTRUCTION,
    payload = Zeros(XLEN)
  };
end

func ExceptionMemory(cause : integer{0..31}, addr : bits(XLEN)) => Result
begin
  return Result {
    is_ok = FALSE,
    cause = cause,
    payload = addr
  };
end

/// Return an error `Result` with `cause` set
/// to environment call (ECALL) exception code corresponding to the
/// provided privilege `mode`.`payload` is undefined.
func ExceptionEcall(mode: PrivMode) => Result
begin
  var cause: integer{0..31};
  case mode of
    when PRIV_MODE_M => cause = XCPT_CODE_MACHINE_ECALL;
  end
  return Result {
    is_ok = FALSE,
    cause = cause,
    payload = Zeros(XLEN)
  };
end

/// Return an error `Result` with `cause` set
/// to the breakpoint exception code. `payload` is undefined.
func ExceptionEbreak() => Result
begin
  return Result {
    is_ok = FALSE,
    cause = XCPT_CODE_BREAKPOINT,
    payload = Zeros(XLEN)
  };
end

// FIXME: asl2c does not support tuple very well
//
type CsrReadResult of record {
  data: bits(XLEN),
  result: Result
};

/// Return a `CsrReadResult` containing an
/// `IllegalInstruction()` error. `data` is undefined.
func CsrReadIllegalInstruction() => CsrReadResult
begin
  return CsrReadResult {
    data = Zeros(XLEN), 
    result = IllegalInstruction()
  };
end

/// Return a `CsrReadResult` containing the
/// provided `data`, with an internal success result.
func CsrReadOk(data: bits(XLEN)) => CsrReadResult
begin
  return CsrReadResult {
    data = data,
    result = OK
  };
end

func asTupleCsrRead(result: CsrReadResult) => (bits(XLEN), Result)
begin
  return (result.data, result.result);
end
