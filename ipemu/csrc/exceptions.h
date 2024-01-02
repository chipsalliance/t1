#pragma once

#include <stdexcept>

class CosimException : public std::runtime_error {
public:
  explicit CosimException(const char *what) : runtime_error(what) {}
};

class TimeoutException : CosimException {
public:
  TimeoutException() : CosimException("timeout") {}
};

class ReturnException : CosimException {
public:
  ReturnException() : CosimException("returned") {}
};
