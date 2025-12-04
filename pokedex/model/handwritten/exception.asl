/// Result denotes whether an operations trigger an exception.
/// When is_ok is TRUE, we do not use _cause_ and _payload_ fields.
/// When is_ok is FALSE, _cause_ contains the exception code, which will be written to XCAUSE later
///
/// Some exceptions define corresponding _payload_, which will be written to XTVAL later.
/// If the exception does not have payload, _payload_ generally should be set to zero.
record Result {
  is_ok : boolean;
  cause : integer{0..31};
  payload : bits(XLEN);
};

let OK: Result = Result {
  is_ok = TRUE,
  cause = 0,
  payload = Zeros(XLEN)
};

// Keep compatibility for old code.
func Retired() => Result
begin
  return OK;
end

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

func CsrReadIllegalInstruction() => CsrReadResult
begin
  return CsrReadResult {
    data = Zeros(XLEN), 
    result = IllegalInstruction()
  };
end

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
