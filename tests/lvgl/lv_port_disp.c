/**
 * @file lv_port_disp_templ.c
 *
 */

/*********************
 *      INCLUDES
 *********************/
#include "lv_port_disp.h"
#include <stdbool.h>

#include <stdio.h>
#include <stdlib.h>

/*********************
 *      DEFINES
 *********************/
#define MY_DISP_HOR_RES 64
#define MY_DISP_VER_RES 64

#define BYTE_PER_PIXEL (LV_COLOR_FORMAT_GET_SIZE(LV_COLOR_FORMAT_RGB888))

/**********************
 *  STATIC PROTOTYPES
 **********************/
static void disp_init(void);

static void disp_flush(lv_display_t *disp, const lv_area_t *area,
                       uint8_t *px_map);

/**********************
 *  STATIC VARIABLES
 **********************/

uint8_t *const framebuffer = (uint8_t *)0x04000000;
volatile int32_t *const counter = (int32_t *)((size_t)framebuffer + 0x1ff0000);

/**********************
 *   GLOBAL FUNCTIONS
 **********************/

#include <riscv_vector.h>
#include <string.h>

void *memcpy_vec(void *restrict destination, const void *restrict source,
                 size_t n) {
  unsigned char *dst = destination;
  const unsigned char *src = source;
  // copy data byte by byte
  for (size_t vl; n > 0; n -= vl, src += vl, dst += vl) {
    vl = __riscv_vsetvl_e8m8(n);
    vuint8m8_t vec_src = __riscv_vle8_v_u8m8(src, vl);
    __riscv_vse8_v_u8m8(dst, vec_src, vl);
  }
  return destination;
}

void lv_port_disp_init(void) {
  /*-------------------------
   * Initialize your display
   * -----------------------*/
  disp_init();

  /*------------------------------------
   * Create a display and set a flush_cb
   * -----------------------------------*/
  lv_display_t *disp = lv_display_create(MY_DISP_HOR_RES, MY_DISP_VER_RES);
  lv_display_set_flush_cb(disp, disp_flush);
  LV_ATTRIBUTE_MEM_ALIGN static lv_color_t
      buf_3_1[MY_DISP_HOR_RES * MY_DISP_VER_RES];
  LV_ATTRIBUTE_MEM_ALIGN static lv_color_t
      buf_3_2[MY_DISP_HOR_RES * MY_DISP_VER_RES];

  lv_display_set_buffers(disp, buf_3_1, buf_3_2, sizeof(buf_3_1),
                         LV_DISPLAY_RENDER_MODE_DIRECT);
}

/**********************
 *   STATIC FUNCTIONS
 **********************/

static void disp_init(void) {
  lv_memzero(framebuffer, MY_DISP_HOR_RES * MY_DISP_VER_RES * sizeof(uint16_t));
}

volatile bool disp_flush_enabled = true;

void disp_enable_update(void) { disp_flush_enabled = true; }

void disp_disable_update(void) { disp_flush_enabled = false; }

static void disp_flush(lv_display_t *disp_drv, const lv_area_t *area,
                       uint8_t *px_map) {
  if (disp_flush_enabled) {
    for (int32_t y = area->y1; y <= area->y2; y++) {
      memcpy_vec(
          &framebuffer[(y * MY_DISP_HOR_RES + area->x1) * BYTE_PER_PIXEL],
          px_map, BYTE_PER_PIXEL * (area->x2 - area->x1 + 1));
      px_map += BYTE_PER_PIXEL * (area->x2 - area->x1 + 1);
    }
  }

  *counter = 0;
  lv_display_flush_ready(disp_drv);
}
