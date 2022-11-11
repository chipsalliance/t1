#pragma once

#include <verilated_vpi.h>

inline auto vpi_get_integer(const char *name) {
  vpiHandle handle = vpi_handle_by_name((PLI_BYTE8 *) name, nullptr);
  s_vpi_value val;
  val.format = vpiIntVal;
  vpi_get_value(handle, &val);
  vpi_free_object(handle);
  return val.value.integer;
}
