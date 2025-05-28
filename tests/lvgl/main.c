#include "lv_port_disp.h"
#include <lvgl/lvgl.h>

void test() {
  lv_init();
  lv_port_disp_init();

  lv_obj_set_style_bg_color(lv_screen_active(), lv_color_hex(0x020406), LV_PART_MAIN);

  lv_tick_inc(1);
  lv_timer_handler();
}
