#pragma once

#include "mmio_dev.hpp"
#include <cstring>
#include <algorithm>
#include <queue>
#include <mutex>

#define SR_TX_FIFO_EMPTY        (1<<2) /* transmit FIFO empty */
#define SR_RX_FIFO_VALID_DATA   (1<<0) /* data in receive FIFO */

#define ULITE_CONTROL_RST_TX	0x01
#define ULITE_CONTROL_RST_RX	0x02

struct uartlite_regs {
    unsigned int rx_fifo;
    unsigned int tx_fifo;
    unsigned int status;
    unsigned int control;
};

class uartlite : public mmio_dev  {
public:
    uartlite() {
        wait_ack = false;
    }
    bool do_read(uint64_t start_addr, uint64_t size, unsigned char* buffer) {
        if (size != 4) return false;
        uint32_t &res_buffer = *reinterpret_cast<uint32_t*>(buffer);
        switch (start_addr) {
            case offsetof(uartlite_regs, rx_fifo): {
                std::unique_lock<std::mutex> lock(rx_lock);
                if (!rx.empty()) {
                    res_buffer = rx.front();
                    rx.pop();
                }
                else res_buffer = 0;
                break;
            }
            case offsetof(uartlite_regs, status): {
                std::unique_lock<std::mutex> rxlock(rx_lock);
                std::unique_lock<std::mutex> txlock(tx_lock);
                res_buffer = (tx.empty() ? SR_TX_FIFO_EMPTY : 0) 
                           | (rx.empty() ? 0 : SR_RX_FIFO_VALID_DATA);
                break;
            }
            case offsetof(uartlite_regs, control): case offsetof(uartlite_regs, tx_fifo): {
                res_buffer = 0;
                break;
            }
            default:
                return false;
        }
        return true;
    }
    bool do_write(uint64_t start_addr, uint64_t size, const unsigned char* buffer) {
        if (size != 4) return false;
        switch (start_addr) {
            case offsetof(uartlite_regs, tx_fifo): {
                std::unique_lock<std::mutex> lock_tx(tx_lock);
                tx.push(static_cast<char>(*buffer));
                break;
            }
            case offsetof(uartlite_regs, control): {
                if (*buffer & ULITE_CONTROL_RST_TX) {
                    std::unique_lock<std::mutex> lock_tx(tx_lock);
                    while (!tx.empty()) tx.pop();
                }
                if (*buffer & ULITE_CONTROL_RST_RX) {
                    std::unique_lock<std::mutex> lock_rx(rx_lock);
                    while (!rx.empty()) rx.pop();
                }
                break;
            }
            case offsetof(uartlite_regs, rx_fifo): case offsetof(uartlite_regs, status): {
                break;
            }
            default:
                return false;
        }
        return true;
    }
    void putc(char c) {
        std::unique_lock<std::mutex> lock(rx_lock);
        rx.push(c);
    }
    char getc() {
        std::unique_lock<std::mutex> lock(tx_lock);
        if (!tx.empty()) {
            char res = tx.front();
            tx.pop();
            if (tx.empty()) wait_ack = true;
            return res;
        }
        else return -1;
    }
    bool exist_tx() {
        std::unique_lock<std::mutex> lock(tx_lock);
        return !tx.empty();
    }
    bool irq() {
        std::unique_lock<std::mutex> lock(rx_lock);
        return !rx.empty() || wait_ack;
    }
private:
    uartlite_regs regs;
    std::queue <char> rx;
    std::queue <char> tx;
    std::mutex rx_lock;
    std::mutex tx_lock;
    bool wait_ack;
};
