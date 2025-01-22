#include <fmt/core.h>
#include <glog/logging.h>

#include "verilated.h"

#include "testharness.h"

TestHarness::TestHarness() : terminate(false) {}

int TestHarness::report() {
  if (terminate == true) {
    LOG(INFO) << fmt::format("ran {} cases, cycles = {}", cnt - 1, getCycle());
    dpiFinish();
  }
  return 0;
}

void TestHarness::setAvailable() { available = true; }

void TestHarness::clrAvailable() { available = false; }

void TestHarness::dpiInitCosim() {
  google::InitGoogleLogging("emulator");
  FLAGS_logtostderr = true;
  FLAGS_minloglevel = 0;

  ctx = Verilated::threadContextp();
  cnt = 0;

  switch (rm) {
  case 0:
    roundingMode = ROUND_NEAR_EVEN;
    rmstring = "RNE";
    break;
  case 1:
    roundingMode = ROUND_MINMAG;
    rmstring = "RTZ";
    break;
  case 2:
    roundingMode = ROUND_MIN;
    rmstring = "RDN";
    break;
  case 3:
    roundingMode = ROUND_MAX;
    rmstring = "RUP";
    break;
  case 4:
    roundingMode = ROUND_NEAR_MAXMAG;
    rmstring = "RMM";
    break;
  default:
    LOG(FATAL) << fmt::format("ilegal rm value = {}", rm);
  }

  if (op == "div") {
    opSignal = false;
  } else if (op == "sqrt") {
    opSignal = true;
  } else
    LOG(FATAL) << fmt::format("illegal operation");

  LOG(INFO) << fmt::format("test f32_{} in {}", op, rmstring);

  initTestCases();

  reloadCase();

  dpiDumpWave();
}

void TestHarness::dpiPeek(svBit ready) {
  if (ready == 1) {
    setAvailable();
  }
}

void TestHarness::dpiPoke(const DutInterface &toDut) {
  if (available == false)
    return;

  *toDut.a = testcase.a;
  *toDut.b = testcase.b;
  *toDut.op = opSignal;
  *toDut.rm = rm;
  *toDut.valid = true;
}

void TestHarness::dpiCheck(svBit valid, svBitVecVal result,
                           svBitVecVal fflags) {
  if (valid == 0)
    return;
  if ((result == testcase.expected_out) &&
      (fflags == testcase.expectedException))
    reloadCase();
  else {
    LOG(ERROR) << fmt::format("error at {} cases", cnt);
    LOG(ERROR) << fmt::format("a = {:08X},b = {:08X} \n", testcase.a,
                              testcase.b);
    LOG(ERROR) << fmt::format("Result  dut vs ref  = {:08X} vs {:08X} \n",
                              result, testcase.expected_out);
    LOG(ERROR) << fmt::format("Flag    dut vs ref  = {:08X} vs {:08X} \n",
                              fflags, (int)testcase.expectedException);
    dpiFinish();
  }
}

std::vector<testdata>
mygen_abz_f32(float32_t trueFunction(float32_t, float32_t), testfloat_function_t function,
              roundingMode_t roundingMode) {
  // modified from berkeley-testfloat-3/source/genLoops.c
  union ui32_f32 {
    uint32_t ui;
    float32_t f;
  } u;

  std::vector<testdata> res;

  softfloat_roundingMode = roundingMode - 1;

  genCases_f32_ab_init();
  while (!genCases_done) {
    genCases_f32_ab_next();

    testdata curData;
    curData.function = function;
    u.f = genCases_f32_a;
    curData.a = u.ui;
    u.f = genCases_f32_b;
    curData.b = u.ui;
    softfloat_exceptionFlags = 0;
    u.f = trueFunction(genCases_f32_a, genCases_f32_b);
    curData.expectedException =
        static_cast<exceptionFlag_t>(softfloat_exceptionFlags);
    curData.expected_out = u.ui;

    res.push_back(curData);
  }

  return res;
}

std::vector<testdata> gencase_az_f32(float32_t trueFunction(float32_t),
                                   testfloat_function_t function,
                                   roundingMode_t roundingMode) {
  union ui32_f32 {
    uint32_t ui;
    float32_t f;
  } u;
  std::vector<testdata> res;
  softfloat_roundingMode = roundingMode - 1;

  genCases_f32_ab_init();
  while (!genCases_done) {
    genCases_f32_ab_next();

    testdata curData;
    curData.function = function;

    u.f = genCases_f32_a;
    curData.a = u.ui;
    curData.b = u.ui;
    softfloat_exceptionFlags = 0;
    u.f = trueFunction(genCases_f32_a);
    curData.expectedException =
        static_cast<exceptionFlag_t>(softfloat_exceptionFlags);
    curData.expected_out = u.ui;
    res.push_back(curData);
  }
  return res;
}

std::vector<testdata>
genTestCase(testfloat_function_t function,
            roundingMode_t roundingMode) { // call it in dpiInit
  // see berkeley-testfloat-3/source/testfloat_gen.c
  std::vector<testdata> res;

  genCases_setLevel(1);

  switch (function) {
  case F32_DIV:
    res = mygen_abz_f32(f32_div, function, roundingMode);
    break;
  case F32_SQRT:
    res = gencase_az_f32(f32_sqrt, function, roundingMode);
    break;
  default:
    assert(false);
  }

  return res;
}

void fillTestQueue(std::vector<testdata> cases) {
  for (auto x : cases) {
    testharness_instance.test_queue.push(x);
  }
}

void TestHarness::initTestCases() {

  std::vector<testdata> res;

  if (op == "div") {
    res = genTestCase(F32_DIV, roundingMode);
  } else if (op == "sqrt") {
    res = genTestCase(F32_SQRT, roundingMode);
  } else
    LOG(FATAL) << fmt::format("illegal operation");

  fillTestQueue(res);
}

void TestHarness::reloadCase() {

  cnt++;

  testcase.a = test_queue.front().a;
  testcase.b = test_queue.front().b;
  testcase.expected_out = test_queue.front().expected_out;
  testcase.expectedException = test_queue.front().expectedException;

  test_queue.pop();
  if (test_queue.size() == 0)
    terminate = true;
}

TestHarness testharness_instance;
