#include "RTLEvent.h"
void RTLEvent::request_ready(bool signal) {
  _req_ready = signal;
}
void RTLEvent::commit_ready(bool signal) {
  _resp_valid = signal;
}
bool RTLEvent::request() {
  return _req_ready;
}
bool RTLEvent::commit() {
  return _resp_valid;
}


// mem load methods
bool RTLEvent::load_valid() {
  return _load_valid;
}
void RTLEvent::set_load_valid(bool valid){
  _load_valid = valid;
}
void RTLEvent::set_load_base_address(uint64_t address){
  _load_base_address = address;
}
// mem store methods
void RTLEvent::set_store_valid(bool valid){
  _store_valid = valid;
}
void RTLEvent::set_store_base_address(uint64_t address){
  _store_base_address = address;
}
void RTLEvent::set_store_data(uint32_t data){
  _store_data = data;
}