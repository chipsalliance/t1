#ifndef V_VBRIDGEIMPL_H
#define V_VBRIDGEIMPL_H

#include <queue>

#include "VV.h"
#include "verilated_fst_c.h"
#include "mmu.h"
#include "simple_sim.h"

constexpr int numTL = 2;

class SpikeToRTL {

} ;
class RTLEvent {

} ;

struct TLBank {
    uint32_t data;
    uint16_t source;
    enum class opType {
        Nil, Get, PutFullData
    } op;
    int remainingCycles;

    TLBank();

    void step();

    [[nodiscard]] bool done() const;

    [[nodiscard]] bool ready() const;

    void clear();
};

class VBridgeImpl {
public:
    explicit VBridgeImpl();
    ~VBridgeImpl();

    void setup(const std::string &bin, const std::string &wave, uint64_t reset_vector, uint64_t cycles);
    void configure_simulator(int argc, char** argv);

    void loop();

private:
    VerilatedContext ctx;
    VV top;
    VerilatedFstC tfp;
    simple_sim sim;
    isa_parser_t isa;
    processor_t proc;
    uint64_t _cycles;
    TLBank banks[numTL];
    /// to rtl stack
    /// in the spike thread, spike should detech if this queue is full, if not full, execute until a vector instruction,
    /// record the behavior of this instruction, and send to str_stack.
    /// in the RTL thread, the RTL driver will consume from this queue, drive signal based on the queue.
    std::queue<SpikeToRTL> to_rtl_queue;

    /// from rtl queue
    /// in the RTL thread, for each RTL cycle, valid signals should be checked, generate events, let testbench be able
    /// to check the correctness of RTL behavior, benchmark performance signals.
    std::queue<RTLEvent> from_rtl_queue;

    inline void reset();
    inline void rtl_tick();
    inline uint64_t rtl_cycle();
    insn_fetch_t fetch_proc_insn();
    uint64_t mem_load(uint64_t addr, uint32_t size);
    void mem_store(uint64_t addr, uint32_t size, uint64_t data);

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
