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
