#ifndef UARTLITE_H
#define UARTLITE_H

#include "axi4.hpp"
#include "mmio_dev.hpp"
#include <algorithm>
#include <queue>
#include <mutex>

#define SR_TX_FIFO_FULL         (1<<3) /* transmit FIFO full */
#define SR_TX_FIFO_EMPTY        (1<<2) /* transmit FIFO empty */
#define SR_RX_FIFO_VALID_DATA   (1<<0) /* data in receive FIFO */
#define SR_RX_FIFO_FULL         (1<<1) /* receive FIFO full */

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
        memset(&regs,0,sizeof(regs));
        regs.status = SR_TX_FIFO_EMPTY;
        wait_ack = false;
    }
    bool do_read(uint64_t start_addr, uint64_t size, unsigned char* buffer) {
        std::unique_lock<std::mutex> lock(rx_lock);
        if (start_addr + size > sizeof(regs)) return false;
        if (!rx.empty()) {
            regs.status |= SR_RX_FIFO_VALID_DATA;
            regs.rx_fifo = rx.front();
        }
        else regs.status &= ~SR_RX_FIFO_VALID_DATA;
        memcpy(buffer,((char*)(&regs))+start_addr,std::min(size,sizeof(regs)-start_addr));
        wait_ack = false;
        if (start_addr <= offsetof(uartlite_regs,rx_fifo) && offsetof(uartlite_regs,rx_fifo) <= start_addr + size) {
            if (!rx.empty()) rx.pop();
        }
        return true;
    }
    bool do_write(uint64_t start_addr, uint64_t size, const unsigned char* buffer) {
        std::unique_lock<std::mutex> lock_tx(tx_lock);
        std::unique_lock<std::mutex> lock_rx(rx_lock);
        if (start_addr + size > sizeof(regs)) return false;
        memcpy(((char*)(&regs))+start_addr,buffer,std::min(size,sizeof(regs)-start_addr));
        if (start_addr <= offsetof(uartlite_regs,tx_fifo) && offsetof(uartlite_regs,tx_fifo) <= start_addr + size) {
            tx.push(static_cast<char>(regs.tx_fifo));
        }
        if (start_addr <= offsetof(uartlite_regs,control) && offsetof(uartlite_regs,control) <= start_addr + size) {
            if (regs.control & ULITE_CONTROL_RST_TX) {
                while (!tx.empty()) tx.pop();
            }
            if (regs.control & ULITE_CONTROL_RST_RX) {
                while (!rx.empty()) rx.pop();
            }
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
        else return EOF;
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

#endif