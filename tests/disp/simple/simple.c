#include <stdint.h>

typedef struct __attribute__((packed)) pixel {
    uint8_t r;
    uint8_t g;
    uint8_t b;
} pixel_t;

volatile int32_t *counter = (int32_t*) 0x05ff0000;
pixel_t *buffer = (pixel_t *) 0x04000000;

void test() {
    for (int i = 0; i < 720; i++) {
        for (int j = 0; j < 320; j++) {
            buffer[i * 960 + j].r = 0xf0;
            buffer[i * 960 + j].g = 0x00;
            buffer[i * 960 + j].b = 0x00;
        }
        for (int j = 320; j < 640; j++) {
            buffer[i * 960 + j].r = 0x00;
            buffer[i * 960 + j].g = 0xf0;
            buffer[i * 960 + j].b = 0x00;
        }
        for (int j = 640; j < 960; j++) {
            buffer[i * 960 + j].r = 0x00;
            buffer[i * 960 + j].g = 0x00;
            buffer[i * 960 + j].b = 0xf0;
        }
    }
    *counter = 0;
    for (int i = 0; i < 720; i++) {
        for (int j = 0; j < 320; j ++) {
            buffer[i * 960 + j].b = 0xf0;
        }
    }
    *counter = 0;
}
