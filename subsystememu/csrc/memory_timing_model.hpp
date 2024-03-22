#pragma once

#include <cstdint>
#include <queue>

class memory_timing_model {
public:
    virtual void add_req(uint64_t addr, uint64_t size, bool is_write, int64_t req_id) = 0;
    virtual bool has_finished_req() = 0;
    virtual bool has_pending_req() = 0;
    virtual int64_t get_finished_req_id() = 0;
    virtual void tick() = 0;
    virtual void reset() = 0;
};
