#define XDMA_CHARDEV_TX "/dev/xdma0_h2c_0"
#define XDMA_CHARDEV_RX "/dev/xdma0_c2h_0"
#define XDMA_USER "/dev/xdma0_user"

#include <cstdio>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <unistd.h>
#include <cstring>
#include <thread>
#include <sys/mman.h>
#include <fstream>
#include <cassert>

enum uartlite_status {
    SR_RX_FIFO_VALID_DATA   = (1<<0),
    SR_RX_FIFO_FULL         = (1<<1),
    SR_TX_FIFO_EMPTY        = (1<<2),
    SR_TX_FIFO_FULL         = (1<<3)
};

enum uartlite_ctrl {
    ULITE_CONTROL_RST_TX    = 0x01,
    ULITE_CONTROL_RST_RX    = 0x02,
    ULITE_INT_ON            = 0x10
};

struct uartlite_regs {
    volatile uint32_t uart_rx;
    volatile uint32_t uart_tx;
    volatile uint32_t uart_status;
    volatile uint32_t uart_control;
};

class uartlite {
public:
    uartlite(uartlite_regs *uart_ptr):uart(uart_ptr) {}
    void uart_put_c(char c) {
        while (uart->uart_status & SR_TX_FIFO_FULL);
        uart->uart_tx = c;
    }

    void uart_send(const char *s) {
        while (*s) {
            uart_put_c(*s);
            s ++;
        }
    }

    void output_rx_buffer() {
        while (uart->uart_status & 1) {
            printf("%c", uart->uart_rx);
            fflush(stdout);
        }
    }

    void reset_uart() {
        uart->uart_control = ULITE_CONTROL_RST_TX | ULITE_CONTROL_RST_RX | ULITE_INT_ON;
    }
private:
    uartlite_regs *uart;
};

struct simif_regs {
    uartlite_regs uart;
    volatile uint32_t soc_reset;
    volatile uint32_t cpu_reset;
    volatile uint32_t lastMemRead;
    volatile uint32_t lastMemWrite;
    volatile uint32_t lastMMIORead;
    volatile uint32_t lastMMIOWrite;
    volatile uint32_t hasMMIOError;
    volatile uint32_t cpucycle_lo;
    volatile uint32_t cpucycle_hi;
    volatile uint32_t resetVector;
};

class sim_if {
public:
    sim_if() {
        fd_read  = open(XDMA_CHARDEV_RX, O_RDONLY | O_SYNC);
        fd_write = open(XDMA_CHARDEV_TX, O_WRONLY | O_SYNC);
        fd_user  = open(XDMA_USER, O_RDWR | O_SYNC);
        user_bar = (simif_regs*)mmap(0, 16384, PROT_READ | PROT_WRITE, MAP_SHARED, fd_user, 0);
    }

    ~sim_if() {
        munmap(user_bar, 16384);
        close(fd_read);
        close(fd_write);
        close(fd_user);
    }

    void reset_soc() {
        user_bar->cpu_reset = 1;
        user_bar->soc_reset = 1;
        uint32_t res;
        while ((res = user_bar->soc_reset) != 2) {
            printf("waiting soc reset status=%x\n", res);
        }
        printf("soc and cpu reseted!\n");
    }

    void dump_debug() {
        printf("cpu reset=%x\n", user_bar->cpu_reset);
        printf("last mem read=%x\n", user_bar->lastMemRead);
        printf("last mem write=%x\n", user_bar->lastMemWrite);
        printf("last mmio read=%x\n", user_bar->lastMMIORead);
        printf("last mmio write=%x\n", user_bar->lastMMIOWrite);
	    printf("has mmio error=%x\n", user_bar->hasMMIOError);
        printf("cpu cycle=%ld\n", (user_bar->cpucycle_hi*1llu << 32) | user_bar->cpucycle_lo);
        printf("resetVector=%x\n", user_bar->resetVector);
    }

    void dump_buffer(char *buffer, size_t size) {
        for (int i=0;i<size;i++) {
            printf("%04x ", buffer[i]);
        }
    }

    void load_file_to_memory(const char *path, off_t memory_offset = 0x1000) {
        std::ifstream file(path, std::ios::in | std::ios::binary | std::ios::ate);
        size_t file_size = file.tellg();
        file.seekg(std::ios_base::beg);
        size_t buffer_size = file_size;
        if (buffer_size % 16384 != 0) buffer_size += 16384 - (buffer_size % 16384);
        char *buffer = new char[buffer_size];
        char *buffer2 = new char[buffer_size];
        file.read(buffer, file_size);
        pwrite(fd_write, buffer, buffer_size, memory_offset);
        pread(fd_read, buffer2, buffer_size, memory_offset);
        assert(memcmp(buffer, buffer2, buffer_size) == 0);
        delete [] buffer;
        delete [] buffer2;
    }

    simif_regs* get_userbar() {
        return user_bar;
    }
    
    void cpu_start() {
        user_bar->cpu_reset = 0;
    }

    void set_resetVector(uint32_t resetVector) {
        user_bar->resetVector = resetVector;
    }
private:
    int fd_read, fd_write, fd_user;
    simif_regs *user_bar;
};

int main(int argc, char *argv[]) {
    sim_if sim;
    uartlite uart( (uartlite_regs*)sim.get_userbar() );
    

    sim.reset_soc();
    uart.reset_uart();
    sim.set_resetVector(0x1000);

    if (argc == 2) {
        printf("Loading %s to memory.\n", argv[1]);
        sim.load_file_to_memory(argv[1]);
    }

    sim.dump_debug();
    sim.cpu_start();

    while (true) uart.output_rx_buffer();
    return 0;
}
