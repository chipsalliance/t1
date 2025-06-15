#include <atomic>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <unistd.h>

#define GET_CALLER_PC()                                                        \
  ((uintptr_t)__builtin_extract_return_addr(                           \
      __builtin_return_address(0)))

static const int kMaxCallerPcs = 20;
static std::atomic_uintptr_t caller_pcs[kMaxCallerPcs];
// Number of elements in caller_pcs. A special value of kMaxCallerPcs + 1 means
// that "too many errors" has already been reported.
static std::atomic_uint32_t caller_pcs_sz;

static char *append_str(const char *s, char *buf, const char *end) {
  for (const char *p = s; (buf < end) && (*p != '\0'); ++p, ++buf)
    *buf = *p;
  return buf;
}

static char *append_hex(uintptr_t d, char *buf, const char *end) {
  // Print the address by nibbles.
  for (unsigned shift = sizeof(uintptr_t) * 8; shift && buf < end;) {
    shift -= 4;
    unsigned nibble = (d >> shift) & 0xf;
    *(buf++) = nibble < 10 ? nibble + '0' : nibble - 10 + 'a';
  }
  return buf;
}

static void format_msg(const char *kind, uintptr_t caller, char *buf,
                       const char *end) {
  buf = append_str("ubsan: ", buf, end);
  buf = append_str(kind, buf, end);
  buf = append_str(" by 0x", buf, end);
  buf = append_hex(caller, buf, end);
  buf = append_str("\n", buf, end);
  if (buf == end)
    --buf; // Make sure we don't cause a buffer overflow.
  *buf = '\0';
}

void __ubsan_report_error(const char *kind, uintptr_t caller) {
  if (caller == 0)
    return;
  while (true) {
    unsigned sz = std::atomic_load(&caller_pcs_sz);
    if (sz > kMaxCallerPcs)
      return; // early exit
    // when sz==kMaxCallerPcs print "too many errors", but only when cmpxchg
    // succeeds in order to not print it multiple times.
    if (sz > 0 && sz < kMaxCallerPcs) {
      uintptr_t p;
      for (unsigned i = 0; i < sz; ++i) {
        p = std::atomic_load(&caller_pcs[i]);
        if (p == 0)
          break; // Concurrent update.
        if (p == caller)
          return;
      }
      if (p == 0)
        continue; // FIXME: yield?
    }

    if (!std::atomic_compare_exchange_strong(&caller_pcs_sz, &sz, sz + 1))
      continue; // Concurrent update! Try again from the start.

    if (sz == kMaxCallerPcs) {
      puts("ubsan: too many errors\n");
      return;
    }
    std::atomic_store(&caller_pcs[sz], caller);

    char msg_buf[128];
    format_msg(kind, caller, msg_buf, msg_buf + sizeof(msg_buf));
    puts(msg_buf);
  }
}

static void abort_with_message(const char *kind, uintptr_t caller) { abort(); }

#define INTERFACE extern "C" __attribute__((visibility("default")))

#define HANDLER_RECOVER(name, kind)                                            \
  INTERFACE void __ubsan_handle_##name##_minimal() {                           \
    __ubsan_report_error(kind, GET_CALLER_PC());                               \
  }

#define HANDLER_NORECOVER(name, kind)                                          \
  INTERFACE void __ubsan_handle_##name##_minimal_abort() {                     \
    uintptr_t caller = GET_CALLER_PC();                                        \
    __ubsan_report_error(kind, caller);                                        \
    abort_with_message(kind, caller);                                          \
  }

#define HANDLER(name, kind)                                                    \
  HANDLER_RECOVER(name, kind)                                                  \
  HANDLER_NORECOVER(name, kind)

HANDLER(type_mismatch, "type-mismatch")
HANDLER(alignment_assumption, "alignment-assumption")
HANDLER(add_overflow, "add-overflow")
HANDLER(sub_overflow, "sub-overflow")
HANDLER(mul_overflow, "mul-overflow")
HANDLER(negate_overflow, "negate-overflow")
HANDLER(divrem_overflow, "divrem-overflow")
HANDLER(shift_out_of_bounds, "shift-out-of-bounds")
HANDLER(out_of_bounds, "out-of-bounds")
HANDLER(local_out_of_bounds, "local-out-of-bounds")
HANDLER_RECOVER(builtin_unreachable, "builtin-unreachable")
HANDLER_RECOVER(missing_return, "missing-return")
HANDLER(vla_bound_not_positive, "vla-bound-not-positive")
HANDLER(float_cast_overflow, "float-cast-overflow")
HANDLER(load_invalid_value, "load-invalid-value")
HANDLER(invalid_builtin, "invalid-builtin")
HANDLER(invalid_objc_cast, "invalid-objc-cast")
HANDLER(function_type_mismatch, "function-type-mismatch")
HANDLER(implicit_conversion, "implicit-conversion")
HANDLER(nonnull_arg, "nonnull-arg")
HANDLER(nonnull_return, "nonnull-return")
HANDLER(nullability_arg, "nullability-arg")
HANDLER(nullability_return, "nullability-return")
HANDLER(pointer_overflow, "pointer-overflow")
HANDLER(cfi_check_fail, "cfi-check-fail")
