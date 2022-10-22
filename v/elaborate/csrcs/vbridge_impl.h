#pragma once

#include <queue>
#include <optional>

#include "VV.h"
#include "verilated_fst_c.h"
#include "spike_event.h"
#include "mmu.h"
#include "simple_sim.h"
#include "rtl_event.h"

constexpr int numTL = 2;

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

  // todo remove this.
  void configure_simulator(int argc, char **argv);

  void loop();

  void run();

  void poke_instruction(SpikeEvent se);

  void poke_csr_control(SpikeEvent se);

private:
  VerilatedContext ctx;
  VV top;
  VerilatedFstC tfp;
  simple_sim sim;
  isa_parser_t isa;
  processor_t proc;
  uint64_t _cycles{};
  TLBank banks[numTL];

  /// to rtl stack
  /// in the spike thread, spike should detech if this queue is full, if not full, execute until a vector instruction,
  /// record the behavior of this instruction, and send to str_stack.
  /// in the RTL thread, the RTL driver will consume from this queue, drive signal based on the queue.
  /// size of this queue should be as big as enough to make rtl free to run, reducing the context switch overhead.
  std::list<SpikeEvent> to_rtl_queue;

  const size_t to_rtl_queue_size = 10;

  /// file path of executeable binary file, which will be executed.
  std::string bin;
  /// generated waveform path.
  std::string wave;
  /// reset vector of
  uint64_t reset_vector{};
  /// RTL timeout cycles
  /// note: this is not the real system cycles, scalar instructions is evaulated via spike, which is not recorded.
  uint64_t timeout{};

  inline void reset();

  inline void rtl_tick();

  inline uint64_t rtl_cycle();

  insn_fetch_t fetch_proc_insn();

  uint64_t mem_load(uint64_t addr, uint32_t size);

  void mem_store(uint64_t addr, uint32_t size, uint64_t data);

  std::optional<SpikeEvent> create_spike_event(insn_fetch_t fetch);

  void init_spike();

  void init_simulator();

  void terminate_simulator();

  uint64_t get_simulator_cycle();

  std::optional<SpikeEvent> spike_step();

  std::optional<RTLEvent> rtl_step();

  std::optional<RTLEvent> create_rtl_event();


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
