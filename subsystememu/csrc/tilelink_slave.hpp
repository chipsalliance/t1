#pragma once

#include "tilelink.hpp"
#include "memory_timing_model.hpp"

#include <queue>
#include <algorithm>
#include <utility>
#include <vector>
#include <queue>
#include <cassert>
#include <climits>

template <unsigned int A_WIDTH, unsigned int W_WIDTH = 64,
          unsigned int O_WIDTH = 4, unsigned int Z_WIDTH = 3>
class tilelink_slave {
public:
    tilelink_slave() {

    }

    void reset() {
        while (!a_queue.empty()) a_queue.pop();
        while (!d_queue.empty()) d_queue.pop();
        cur_a.data.clear();
        cur_a.mask.clear();
        cur_d.data.clear();
        d_index = -1;
        for (auto &each_model : timing_constrain) {
            each_model.second->reset();
        }
    }

    void tick(tilelink_ref<A_WIDTH, W_WIDTH, O_WIDTH, Z_WIDTH> &tl) {
        input_a(tl);
        transaction_process();
        do_timing_constrain();
        output_d(tl);
    }

    bool insert_memory_timing_model(uint64_t start_addr, uint64_t length, memory_timing_model* model) {
        std::pair<uint64_t, uint64_t> addr_range = std::make_pair(start_addr,start_addr+length);
        if (start_addr % length) return false;
        // check range
        auto it = timing_constrain.upper_bound(addr_range);
        if (it != timing_constrain.end()) {
            uint64_t l_max = std::max(it->first.first,addr_range.first);
            uint64_t r_min = std::min(it->first.second,addr_range.second);
            if (l_max < r_min) return false; // overleap
        }
        if (it != timing_constrain.begin()) {
            it = std::prev(it);
            uint64_t l_max = std::max(it->first.first,addr_range.first);
            uint64_t r_min = std::min(it->first.second,addr_range.second);
            if (l_max < r_min) return false; // overleap
        }
        // overleap check pass
        timing_constrain[addr_range] = model;
        return true;
    }
protected:
    virtual bool do_read (uint64_t start_addr, uint64_t size, uint8_t* buffer) = 0;
    virtual bool do_write(uint64_t start_addr, uint64_t size, const uint8_t* buffer) = 0;

private:
    // for memory timing constrain {
    //                 addr_start,addr_end
    std::map < std::pair<uint64_t,uint64_t>, memory_timing_model* > timing_constrain;
    std::map < int64_t, a_packet > pending_a;
    int64_t req_id_gen = 0;

    memory_timing_model* find_timing_model(uint64_t start_addr, uint64_t size) {
        auto it = timing_constrain.upper_bound(std::make_pair(start_addr, ULONG_MAX));
        if (it == timing_constrain.begin()) return nullptr;
        it = std::prev(it);
        if (it->first.first <= start_addr && start_addr + size <= it->first.second) {
            return it->second;
        }
        else return nullptr;
    }

    void do_timing_constrain() {
        if (pending_a.empty()) return;
        for (auto &each_model : timing_constrain) {
            while (each_model.second->has_finished_req()) {
                int64_t req_id = each_model.second->get_finished_req_id();
                if (pending_a.count(req_id)) {
                    a_packet cur_a = pending_a[req_id];
                    pending_a.erase(req_id);
                    a_packet_process(cur_a);
                }
            }
        }
    }
    // for memory timing constrain }

    std::queue <a_packet> a_queue;
    std::queue <d_packet> d_queue;

    a_packet cur_a;
    d_packet cur_d;
    int d_index = -1;

    void input_a(tilelink_ref<A_WIDTH, W_WIDTH, O_WIDTH, Z_WIDTH> &pin) {
        if (pin.a_valid && pin.a_ready) { // a fire
            switch (pin.a_bits_opcode) {
                case TL_A_PutFullData: case TL_A_PutPartialData: {
                    cur_a.opcode = static_cast<opcode_a>(pin.a_bits_opcode);
                    cur_a.param = pin.a_bits_param;
                    cur_a.size = pin.a_bits_size;
                    cur_a.source = pin.a_bits_source;
                    cur_a.address = pin.a_bits_address;
                    cur_a.corrupt = pin.a_bits_corrupt;

                    int end = std::min(static_cast<uint64_t>(W_WIDTH), cur_a.address % W_WIDTH + (1<<pin.a_bits_size) );
                    for (int i = cur_a.address % W_WIDTH; i < end; i++) {
                        cur_a.data.push_back((reinterpret_cast<char*>(&pin.a_bits_data))[i]);
                        cur_a.mask.push_back( ( ((reinterpret_cast<char*>(&pin.a_bits_mask))[i/8]) & (1 << (i % 8)) ) ? true : false);
                    }

                    if (cur_a.data.size() == 1 << pin.a_bits_size) {
                        a_queue.push(cur_a);
                        cur_a.data.clear();
                        cur_a.mask.clear();
                    }
                    break;
                }
                case TL_A_ArithmeticData: case TL_A_LogicalData: {
                    cur_a.opcode    = static_cast<opcode_a>(pin.a_bits_opcode);
                    cur_a.param     = pin.a_bits_param;
                    cur_a.size      = pin.a_bits_size;
                    cur_a.source    = pin.a_bits_source;
                    cur_a.address   = pin.a_bits_address;
                    cur_a.corrupt   = pin.a_bits_corrupt;

                    int end = std::min(static_cast<uint64_t>(W_WIDTH), cur_a.address % W_WIDTH + (1<<pin.d_bits_size));
                    for (int i = cur_a.address % W_WIDTH; i < end; i++) {
                        cur_a.data.push_back(( reinterpret_cast<char*>(&pin.a_bits_data))[i]);
                        cur_a.mask.push_back( ( ((reinterpret_cast<char*>(&pin.a_bits_mask))[i/8]) & (1 << (i % 8)) ) ? true : false);
                    }

                    if (cur_a.data.size() == 1 << cur_a.size) {
                        a_queue.push(cur_a);
                        cur_a.data.clear();
                        cur_a.mask.clear();
                    }
                    break;
                }
                case TL_A_Get: {
                    cur_a.opcode = TL_A_Get;
                    cur_a.param = pin.a_bits_param;
                    cur_a.size = pin.a_bits_size;
                    cur_a.source = pin.a_bits_source;
                    cur_a.address = pin.a_bits_address;
                    cur_a.corrupt = pin.a_bits_corrupt;
                    a_queue.push(cur_a);
                    break;
                }
                case TL_A_Intent: {
                    cur_a.opcode = TL_A_Intent;
                    cur_a.param = pin.a_bits_param;
                    cur_a.size = pin.a_bits_size;
                    cur_a.source = pin.a_bits_source;
                    cur_a.address = pin.a_bits_address;
                    cur_a.corrupt = pin.a_bits_corrupt;
                    a_queue.push(cur_a);
                    break;
                }
                default: assert(false);
            }
        }
        pin.a_ready = 1;
    }

    void output_d(tilelink_ref<A_WIDTH, W_WIDTH, O_WIDTH, Z_WIDTH> &pin) {
        if (d_index != -1) {
            if (pin.d_ready) {
                // next beat in a burst
                if (d_index == (1<<cur_d.size) || cur_d.opcode != TL_D_AccessAckData) {
                    // here is the last transation, stop
                    // if there is a new transaction, it will be solved in the next if
                    d_index = -1;
                }
                else {
                    for (int i = 0; i < W_WIDTH; i++) { // burst transaction always aligned to W_WIDTH
                        (reinterpret_cast<char*>(&pin.d_bits_data))[i] = cur_d.data[d_index++];
                    }
                }
            }
        }
        if (d_index == -1 && !d_queue.empty()) {
            cur_d = d_queue.front();
            d_queue.pop();
            d_index = 0;

            pin.d_bits_opcode = cur_d.opcode;
            pin.d_bits_param = cur_d.param;
            pin.d_bits_size = cur_d.size;
            pin.d_bits_source = cur_d.source;
            pin.d_bits_corrupt = cur_d.corrupt;
            pin.d_bits_denied = cur_d.denied;

            if (cur_d.opcode == TL_D_AccessAckData) {
                int end = std::min(static_cast<uint64_t>(W_WIDTH), cur_d.address % W_WIDTH + (1<<pin.d_bits_size));
                for (int i = cur_a.address % W_WIDTH; i < end; i++) {
                    (reinterpret_cast<char*>(&pin.d_bits_data))[i] = cur_d.data[d_index++];
                }
            }
        }
        pin.d_valid = d_index != -1;
    }

    void transaction_process() {
        while (!a_queue.empty()) {
            a_packet a = a_queue.front();
            a_queue.pop();
            memory_timing_model* model = find_timing_model(a.address, (1<<a.size));
            if (model) {
                // has timing constrain, add to pending list
                int64_t req_id = req_id_gen++;
                pending_a[req_id] = a;
                model->add_req(a.address, (1<<a.size),
                               a.opcode == TL_A_PutFullData ||
                               a.opcode == TL_A_PutPartialData, req_id);
            }
            else {
                // no timing constrain, process directly
                a_packet_process(a);
            }
        }
    }

    void a_packet_process(a_packet a) {
        switch (a.opcode) {
            case TL_A_PutFullData: {
                bool ok = do_write(a.address, 1 << a.size, reinterpret_cast<const unsigned char*>(a.data.data()));
                d_queue.push(make_AccessAck(a.size, a.source, !ok));
                break;
            }
            case TL_A_PutPartialData: {
                // TODO: merge
                bool ok = do_write_with_mask(a.address, 1 << a.size, a.data, a.mask);
                d_queue.push(make_AccessAck(a.size, a.source, !ok));
                break;
            }
            case TL_A_ArithmeticData: TL_A_LogicalData: {
                // TODO: ArithmeticData, LogicalData
                assert(false);
                break;
            }
            case TL_A_Get: {
                std::vector<char> buffer = std::vector<char>(1 << a.size);
                bool ok = do_read(a.address, 1 << a.size, reinterpret_cast<unsigned char*>(buffer.data()));
                d_queue.push(make_AccessAckData(a.size, a.source, !ok, a.address, buffer));
                break;
            }
            case TL_A_Intent: {
                d_queue.push(make_HintAck(a.size, a.source, a.corrupt));
                break;
            }
            default: assert(false);
        }
    }

    bool do_write_with_mask(uint64_t start_addr, int64_t data_len, std::vector<char> &data, std::vector<bool> &mask) {
        // As the type of std::vector<bool> is very special, we don't use it .data() to pass the bool* pointer
        int64_t l = 0;
        int64_t r = 0;
        bool res = true;

        for (int64_t i = 0; i < data_len; i++) {
            if (mask[i]) {
                r = i + 1;
            }
            else {
                if (l < r) {
                    res &= do_write(start_addr + l, r - l, &reinterpret_cast<unsigned char*>(data.data())[l]);
                }
                l = i + 1;
            }
        }

        if (l < r) {
            res &= do_write(start_addr + l, r - l, &reinterpret_cast<unsigned char*>(data.data())[l]);
        }

        return res;
    }

    d_packet make_AccessAck(uint64_t size, uint64_t source, bool denied) {
        d_packet d;
        d.opcode = TL_D_AccessAck;
        d.param = 0;
        d.size = size;
        d.source = source;
        d.address = 0;
        d.corrupt = false;
        d.denied = denied;
        return d;
    }

    d_packet make_AccessAckData(uint64_t size, uint64_t source, bool denied,
        uint64_t address, std::vector <char> &data) {
        d_packet d;
        d.opcode = TL_D_AccessAckData;
        d.param = 0;
        d.size = size;
        d.source = source;
        d.address = address;
        d.data = data;
        d.corrupt = denied;
        d.denied = denied;
        return d;
    }

    d_packet make_HintAck(uint64_t size, uint64_t source, bool denied) {
        d_packet d;
        d.opcode = TL_D_HintAck;
        d.param = 0;
        d.size = size;
        d.source = source;
        d.address = 0;
        d.corrupt = false;
        d.denied = denied;
        return d;
    }
};
