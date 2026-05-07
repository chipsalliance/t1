#include <stdio.h>
#include <stdlib.h>

#include "lvgl.h"
#include "porting/lv_port_disp.h"

#ifdef OPENGL
#include <GLFW/glfw3.h>
#include <OpenGL/gl3.h>

#include <pthread.h>
#include <unistd.h>
#endif

extern uint16_t framebuffer[];

#ifdef OPENGL
extern GLFWwindow* window;

void* tick_function(void* arg) {
    while (1) {
        lv_tick_inc(1);
        usleep(1000);
    }
    return NULL;
}
#endif

static void anim_x_cb(void * var, int32_t v)
{
    lv_obj_set_x(var, v);
}

static void anim_size_cb(void * var, int32_t v)
{
    lv_obj_set_size(var, v, v);
}
// volatile int32_t *counter = (int32_t*) 0x05ff0000;
// int main(void) {
extern volatile int32_t *counter;
void test() {
    *counter = 0;
    
    lv_init();
    lv_port_disp_init();

#ifdef OPENGL
    pthread_t tick_thread, timer_thread;

    if (pthread_create(&tick_thread, NULL, tick_function, NULL) != 0) {
        fprintf(stderr, "Error creating tick thread\n");
        return 1;
    }
#endif

    lv_obj_t * label = lv_label_create(lv_screen_active());
    // lv_label_set_text(label, "Hello world");
    // lv_obj_set_style_text_color(lv_screen_active(), lv_color_hex(0x0665557), LV_PART_MAIN);
    // lv_obj_align(label, LV_ALIGN_CENTER, 0, 0);

    // lv_obj_t *myBtn = lv_btn_create(lv_scr_act());
    // lv_obj_set_pos(myBtn, 10, 10);
    // lv_obj_set_size(myBtn, 300, 100);

    // lv_obj_t *label_btn = lv_label_create(myBtn);
    // lv_obj_align(label_btn, LV_ALIGN_CENTER, 0, 0);
    // lv_label_set_text(label_btn, "Test");

    // lv_obj_t *myLabel = lv_label_create(lv_scr_act());
    // lv_label_set_text(myLabel, "Hello world!");
    // lv_obj_align(myLabel, LV_ALIGN_CENTER, 0, 0);
    // lv_obj_align_to(myBtn, myLabel, LV_ALIGN_OUT_TOP_MID, 0, -20);

    lv_obj_set_style_bg_color(lv_screen_active(), lv_color_hex(0x0665557), LV_PART_MAIN);

    lv_obj_t * obj = lv_obj_create(lv_scr_act());
    lv_obj_set_style_bg_color(obj, lv_palette_main(LV_PALETTE_RED), 0);
    lv_obj_set_style_radius(obj, LV_RADIUS_CIRCLE, 0);

    lv_obj_align(obj, LV_ALIGN_LEFT_MID, 10, 0);

    lv_anim_t a;
    lv_anim_init(&a);
    lv_anim_set_var(&a, obj);
    lv_anim_set_values(&a, 10, 100);
    lv_anim_set_time(&a, 1000);
    lv_anim_set_playback_delay(&a, 100);
    lv_anim_set_playback_time(&a, 300);
    lv_anim_set_repeat_delay(&a, 500);
    lv_anim_set_repeat_count(&a, LV_ANIM_REPEAT_INFINITE);
    lv_anim_set_path_cb(&a, lv_anim_path_ease_in_out);

    lv_anim_set_exec_cb(&a, anim_size_cb);
    lv_anim_start(&a);
    lv_anim_set_exec_cb(&a, anim_x_cb);
    lv_anim_set_values(&a, 10, 700);
    lv_anim_start(&a);

#ifdef OPENGL
    while (!glfwWindowShouldClose(window))
#else
// render 5 frames
    for (int i = 0; i < 5; ++i)
#endif
    {
        lv_tick_inc(1);
        lv_timer_handler();
        *counter = 0;
    }
#ifdef OPENGL
    glfwDestroyWindow(window);
    glfwTerminate();
    exit(EXIT_SUCCESS);
#endif
}
