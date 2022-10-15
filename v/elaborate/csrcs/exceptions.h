#include <stdexcept>

class CosimException : public std::runtime_error {
public:
    CosimException(const char* what) : runtime_error(what) {}
};

class TimeoutException : CosimException {
public:
    TimeoutException() : CosimException("timeout") {}
};