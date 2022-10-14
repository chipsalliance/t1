#ifndef V_VBRIDGEIMPL_H
#define V_VBRIDGEIMPL_H

#include "VV.h"
#include "verilated_fst_c.h"
#include "mmu.h"
#include "simple_sim.h"

constexpr int numTL = 2;

struct TLBank {
    uint32_t data;
    enum class opType {
        Nil, Get, PutFullData
    } op;
    int remainingCycles;

    TLBank() {
      op = opType::Nil;
    }

    void step() {
      if (remainingCycles > 0) remainingCycles--;
    }

    [[nodiscard]] bool done() const {
      return op != opType::Nil && remainingCycles == 0;
    }

    [[nodiscard]] bool ready() const {
      return op == opType::Nil;
    }

    void clear() {
      op = opType::Nil;
    }
};

class VBridgeImpl {
public:
    explicit VBridgeImpl();
    ~VBridgeImpl();

    void setup(const std::string &bin, const std::string &wave, uint64_t reset_vector, uint64_t cycles);
    void configure_simulator(int argc, char** argv);

    [[noreturn]] void loop();

private:
    VerilatedContext ctx;
    VV top;
    VerilatedFstC tfp;
    simple_sim sim;
    isa_parser_t isa;
    processor_t proc;
    uint64_t _cycles;
    TLBank banks[numTL];

    inline void reset();
    inline void rtl_tick();
    inline uint64_t rtl_cycle();
    insn_fetch_t fetch_proc_insn();

// the following macro helps us to access tl interface by dynamic index
#define TL_INTERFACE(type, name) \
type &get_tl_##name(int i) {      \
  switch (i) {                   \
  case 0: return top.tlPort_0_##name;                     \
  case 1: return top.tlPort_1_##name;                     \
  default: assert(false && "unknown tl port index");                                 \
  }\
}

#include "tl_interface.inc.h"
};

#endif //V_VBRIDGEIMPL_H
