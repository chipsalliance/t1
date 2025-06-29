func ReadMemory(addr : bits(32), width : integer) => (bits(width), Result)
begin
  case width of
    when 8 =>
      let ffi_read : FFI_ReadResult(8) = FFI_read_physical_memory_8bits(addr);
      if !ffi_read.success then
        return (Zeros(width), Exception(CAUSE_LOAD_ACCESS, addr));
      end

      // We know that data must be 8 in this branch, however the type system doesn't know.
      // After monomorphic transform, all the `width` parameter will be replaced with static instance and cause error.
      return (ZeroExtend(ffi_read.data, width), Retired());
    when 16 =>
      if addr[0] != '0' then
        return (Zeros(width), Exception(CAUSE_MISALIGNED_LOAD, addr));
      end

      let ffi_read : FFI_ReadResult(16) = FFI_read_physical_memory_16bits(addr);
      if !ffi_read.success then
        return (Zeros(width), Exception(CAUSE_LOAD_ACCESS, addr));
      end

      return (ZeroExtend(ffi_read.data, width), Retired());
    when 32 =>
      if addr[1:0] != '00' then
        return (Zeros(width), Exception(CAUSE_MISALIGNED_LOAD, addr));
      end

      let ffi_read : FFI_ReadResult(32) = FFI_read_physical_memory_32bits(addr);
      if !ffi_read.success then
        return (Zeros(width), Exception(CAUSE_LOAD_ACCESS, addr));
      end

      return (ZeroExtend(ffi_read.data, width), Retired());
    otherwise => assert FALSE;
  end
end

func WriteMemory{width : integer{8,16,32}}(addr : bits(32), data : bits(width)) => Result
begin
  case width of
    when 8 =>
      // We know that data must be 8 in this branch, however the type system doesn't know.
      // After monomorphic transform, all the `width` parameter will be replaced with static instance and cause error.
      let fixed_data : bits(8) = ZeroExtend(data, 8);
      let success = FFI_write_physical_memory_8bits(addr, fixed_data);
      if !success then
        return Exception(CAUSE_STORE_ACCESS, addr);
      end

      return Retired();
    when 16 =>
      if addr[0] != '0' then
        return Exception(CAUSE_MISALIGNED_STORE, addr);
      end

      let fixed_data : bits(16) = ZeroExtend(data, 16);
      let success = FFI_write_physical_memory_16bits(addr, fixed_data);
      if !success then
        return Exception(CAUSE_STORE_ACCESS, addr);
      end

      return Retired();
    when 32 =>
      if addr[1:0] != '00' then
        return Exception(CAUSE_MISALIGNED_STORE, addr);
      end

      let fixed_data : bits(32) = ZeroExtend(data, 32);
      let success = FFI_write_physical_memory_32bits(addr, fixed_data);
      if !success then
        return Exception(CAUSE_STORE_ACCESS, addr);
      end

      return Retired();
    otherwise => assert FALSE;
  end
end
