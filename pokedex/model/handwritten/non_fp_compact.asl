//! This file act as a drop-in replacement for non-FP model.
//! Because ASL is a static analyze language, we need to
//! defines some variable even it is untouch.

func __resetFPStates()
begin
  // Do nothing
end

/// A read to MSTATUS_FS is always zero indicate it is unsupported
getter MSTATUS_FS => bits(2)
begin
  return '00';
end

/// A write to MSTATUS_FS is always ignored
setter MSTATUS_FS = value : bits(2)
begin
  // Do nothing
end
