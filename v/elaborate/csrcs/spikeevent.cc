#include <fmt/core.h>
#include <glog/logging.h>

#include "spikeevent.h"
#include "disasm.h"

//------------------------------- Get methods---------------------------------------

// return false: not issued
//        true: has been issued
bool SpikeEvent::get_issued() {
  return _issue;
}
void SpikeEvent::issue() {
  assert(_issue == false);
  _issue = true;
}
void SpikeEvent::reset_issue() {
  _issue =  false;
}

void SpikeEvent::commit() {
  // TODO: check this event is finished.
}
void SpikeEvent::reset_commit(){
  _commit = false;
}


uint32_t SpikeEvent::instruction() {
  return _inst;
}

uint64_t SpikeEvent::pc() {
  return _pc;
}

uint8_t SpikeEvent::vsew() {
  return _vsew;
}
uint8_t SpikeEvent::vlmul() {
  return _vlmul;
}
bool SpikeEvent::vma() {
  return _vma;
}
bool SpikeEvent::vta() {
  return _vta;
}
bool SpikeEvent::vill() {
  return _vill;
}
uint32_t SpikeEvent::vl() {
  return _vl;
}
uint16_t SpikeEvent::vstart() {
  return _vstart;
}
uint8_t SpikeEvent::vxrm() {
  return _vxrm;
}
bool SpikeEvent::vxsat() {
  return _vxsat;
}

bool SpikeEvent::need_lsu_index() {
  return _need_lsu_index;
}

uint8_t  SpikeEvent::lsu_index() {
  return _index;
}
//------------------------------- Set methods---------------------------------------
void SpikeEvent::set_inst(uint32_t instruction){
  _inst = instruction;
}
void SpikeEvent::set_src1(uint32_t src1){
  _src1 = src1;
}
void SpikeEvent::set_src2(uint32_t src2){
  _src2 = src2;
}

void SpikeEvent::set_vsew(uint8_t vsew){
  _vsew = vsew;
}
void SpikeEvent::set_vlmul(uint8_t vlmul){
  _vlmul = vlmul;
}
void SpikeEvent::set_vma(bool vma){
  _vma = vma;
}
void SpikeEvent::set_vta(bool vta){
  _vta = vta;
}
void SpikeEvent::set_vl(uint32_t vl){
  _vl = vl;
}
void SpikeEvent::set_vxrm(uint8_t vxrm){
  _vxrm = vxrm;
}
void SpikeEvent::set_vstart(uint16_t vstart) {
  _vstart = vstart;
}

void SpikeEvent::assign_instruction(uint32_t instruction) {
  _pc = _proc.get_state()->pc;
  _inst = instruction;
}

void SpikeEvent::set_lsu_index(uint8_t value) {
  _index = value;
}

void SpikeEvent::set_need_lsu_index() {
  _need_lsu_index = true;
}

void SpikeEvent::clr_need_lsu_index() {
  _need_lsu_index = false;
}

//------------------------------Other methods----------------------------
std::string SpikeEvent::disam() {
  return fmt::format("PC: {:X}, ASM: {:08X}, DISASM: {}", _pc, _inst, _proc.get_disassembler()->disassemble(_inst));
}

void SpikeEvent::log_reset() {
  // clear state for difftest.
  _proc.get_state()->log_reg_write.clear();
  _proc.get_state()->log_mem_read.clear();
  _proc.get_state()->log_mem_write.clear();
}


// TODO
void SpikeEvent::get_mask() {
  // get mask from v0
  _mask = _proc.VU.elt<uint8_t>(0, 0);
}

uint64_t SpikeEvent::mem_load(uint64_t addr, uint32_t size) {
  switch (size) {
    case 0:
      return _proc.get_mmu()->load_uint8(addr);
    case 1:
      return _proc.get_mmu()->load_uint16(addr);
    case 2:
      return _proc.get_mmu()->load_uint32(addr);
    default:
      LOG(FATAL) << fmt::format("unknown load size {}", size);
  }
}

// 记录的数据会不会是之前多条指令的结果
void SpikeEvent::log() {
  auto state = _proc.get_state();
  auto& regs = state->log_reg_write;
  auto& loads = state->log_mem_read;
  auto& stores = state->log_mem_write;
  mem_read_info = loads;
  int load_size = loads.size();
  int store_size = stores.size();
  if(!state->log_mem_read.empty()){
    //std::vector <std::tuple<int,int>> ve = {std::make_tuple(1,1),std::make_tuple(2,2)};
    //LOG(INFO) << fmt::format(" test = {}", ve);
    LOG(INFO) << fmt::format(" load times = {}", load_size);
    //LOG(INFO) << fmt::format(" front reg = {}", std::get<0>(loads.front()));
    //LOG(INFO) << fmt::format(" front address = {}", std::get<1>(loads.front()));
    //LOG(INFO) << fmt::format(" front size = {}", std::get<2>(loads.front()));
    for (auto item : loads) {
      //std::get<1> (item) = 1;
      //LOG(INFO) << fmt::format(" load addr, value, size = {}, {}, {}", std::get<0>(item),std::get<1>(item),std::get<2>(item));
      uint64_t addr = std::get<0>(item);
      uint64_t value = mem_load(std::get<0>(item),std::get<2>(item)-1);
      uint8_t size =  std::get<2>(item);
      LOG(INFO) << fmt::format(" load addr, load back value, size = {:X}, {}, {}", addr,value,size);
      auto tu = std::make_tuple(addr,value,size);
      log_mem_queue.push_back(tu);

    }
  }
  if(!state->log_mem_write.empty()){
    LOG(INFO) << fmt::format(" store size = {}" , store_size);
  }

  for (auto reg : regs) {
    // in spike, log_reg_write is arrange:
    // xx0000 <- x
    // xx0001 <- f
    // xx0010 <- vreg
    // xx0011 <- vec
    // xx0100 <- csr
    if ((reg.first & 0xf) == 2) {
      // TODO: based on VLMUL, SEW, set _vrf
      continue;
    }
  }
  for (auto mem_write : state->log_mem_write) {

  }
  for (auto mem_read : state->log_mem_write) {

  }
}
