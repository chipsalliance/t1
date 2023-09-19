#include <spdlog/spdlog.h>

// Exported symbols
void setup_logger();

#define CHECK(cond, context)                                                   \
  if (!(cond)) {                                                               \
    auto _f_msg =                                                              \
        fmt::format("check failed: {} : Assertion ({}) failed at {}:{}",       \
                    context, #cond, __FILE__, __LINE__);                       \
    json _j;                                                                   \
    _j["message"] = _f_msg;                                                    \
    spdlog::error(_j.dump());                                                  \
    spdlog::shutdown();                                                        \
    throw std::runtime_error(_f_msg);                                          \
  }
#define CHECK_EQ(val1, val2, context) CHECK(val1 == val2, context)
#define CHECK_NE(val1, val2, context) CHECK(val1 != val2, context)
#define CHECK_LE(val1, val2, context) CHECK(val1 <= val2, context)
#define CHECK_LT(val1, val2, context) CHECK(val1 < val2, context)
#define CHECK_GE(val1, val2, context) CHECK(val1 >= val2, context)
#define CHECK_GT(val1, val2, context) CHECK(val1 > val2, context)
