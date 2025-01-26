/**
 * @file lv_port_disp_templ.c
 *
 */

/*Copy this file as "lv_port_disp.c" and set this value to "1" to enable content*/
#if 1

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
#define MY_DISP_HOR_RES 960
#define MY_DISP_VER_RES 720

#ifndef MY_DISP_HOR_RES
    #warning Please define or replace the macro MY_DISP_HOR_RES with the actual screen width, default value 320 is used for now.
    #define MY_DISP_HOR_RES    320
#endif

#ifndef MY_DISP_VER_RES
    #warning Please define or replace the macro MY_DISP_VER_RES with the actual screen height, default value 240 is used for now.
    #define MY_DISP_VER_RES    240
#endif

#define BYTE_PER_PIXEL (LV_COLOR_FORMAT_GET_SIZE(LV_COLOR_FORMAT_RGB888)) /*will be 2 for RGB565 */

/**********************
 *      TYPEDEFS
 **********************/

/**********************
 *  STATIC PROTOTYPES
 **********************/
static void disp_init(void);

static void disp_flush(lv_display_t * disp, const lv_area_t * area, uint8_t * px_map);

/**********************
 *  STATIC VARIABLES
 **********************/

uint8_t *framebuffer = (uint8_t *) 0x04000000;
volatile int32_t *counter = (int32_t*) 0x05ff0000;
// uint8_t framebuffer[MY_DISP_HOR_RES * MY_DISP_VER_RES * BYTE_PER_PIXEL];
#ifdef OPENGL
GLFWwindow* window;
#endif
/**********************
 *      MACROS
 **********************/

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

void lv_port_disp_init(void)
{
    /*-------------------------
     * Initialize your display
     * -----------------------*/
    disp_init();

    /*------------------------------------
     * Create a display and set a flush_cb
     * -----------------------------------*/
    lv_display_t * disp = lv_display_create(MY_DISP_HOR_RES, MY_DISP_VER_RES);
    lv_display_set_flush_cb(disp, disp_flush);

    /* Example 1
     * One buffer for partial rendering*/
    // LV_ATTRIBUTE_MEM_ALIGN
    // static uint8_t buf_1_1[MY_DISP_HOR_RES * 120 * BYTE_PER_PIXEL];            /*A buffer for 10 rows*/
    // lv_display_set_buffers(disp, buf_1_1, NULL, sizeof(buf_1_1), LV_DISPLAY_RENDER_MODE_PARTIAL);

    /* Example 2
     * Two buffers for partial rendering
     * In flush_cb DMA or similar hardware should be used to update the display in the background.*/
//    LV_ATTRIBUTE_MEM_ALIGN
//    static uint8_t buf_2_1[MY_DISP_HOR_RES * 10 * BYTE_PER_PIXEL];
//
//    LV_ATTRIBUTE_MEM_ALIGN
//    static uint8_t buf_2_2[MY_DISP_HOR_RES * 10 * BYTE_PER_PIXEL];
//    lv_display_set_buffers(disp, buf_2_1, buf_2_2, sizeof(buf_2_1), LV_DISPLAY_RENDER_MODE_PARTIAL);

    /* Example 3
     * Two buffers screen sized buffer for double buffering.
     * Both LV_DISPLAY_RENDER_MODE_DIRECT and LV_DISPLAY_RENDER_MODE_FULL works, see their comments*/
   LV_ATTRIBUTE_MEM_ALIGN
   static uint8_t buf_3_1[MY_DISP_HOR_RES * MY_DISP_VER_RES * BYTE_PER_PIXEL];

   LV_ATTRIBUTE_MEM_ALIGN
   static uint8_t buf_3_2[MY_DISP_HOR_RES * MY_DISP_VER_RES * BYTE_PER_PIXEL];
   lv_display_set_buffers(disp, buf_3_1, buf_3_2, sizeof(buf_3_1), LV_DISPLAY_RENDER_MODE_DIRECT);

}

/**********************
 *   STATIC FUNCTIONS
 **********************/

#ifdef OPENGL
static const char* vtxShader = "#version 330\n"
                               "\n"
                               "layout (location = 0) in vec3 aPos;\n"
                               "layout (location = 1) in vec2 aUV;\n"
                               "\n"
                               "out vec2 uv;\n"
                               "\n"
                               "void main() {\n"
                               "    gl_Position = vec4(aPos, 1.0);\n"
                               "    uv = aUV;\n"
                               "}";

static const char* fragShader = "#version 330\n"
                                "\n"
                                "uniform sampler2D tex;\n"
                                "\n"
                                "in vec2 uv;\n"
                                "out vec4 FragColor;\n"
                                "\n"
                                "void main() {\n"
                                //                                "    FragColor = vec4(uv, 0.7f, 1.0f);\n"
                                "    FragColor = texture(tex, uv);\n"
                                "}";

static GLint compileShader(GLenum shaderType, const char* shaderSrc)
{
    GLuint shader = glCreateShader(shaderType);
    glShaderSource(shader, 1, &shaderSrc, NULL);
    glCompileShader(shader);
    int status;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &status);
    if (status != GL_TRUE)
    {
        // Assume we only care about vertex and fragment shaders
        fprintf(stderr, "Could not compile shader! Shader type: %s\n", ((shaderType == GL_VERTEX_SHADER) ? "vertex" : "fragment"));
        GLint logLength;
        glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &logLength);
        char* pInfoLog = malloc(logLength + 1);
        glGetShaderInfoLog(shader, logLength + 1, &logLength, pInfoLog);
        fprintf(stderr, "Error log: %s\n", pInfoLog);
        fprintf(stderr, "Shader source: %s\n", shaderSrc);
        glDeleteShader(shader);
        free(pInfoLog);
        return -1;
    }
    return shader;
}

bool compileShaders(GLuint* pprogram) {
    GLint vertexShader = compileShader(GL_VERTEX_SHADER, vtxShader);
    GLint fragmentShader = compileShader(GL_FRAGMENT_SHADER, fragShader);
    if (vertexShader < 0 || fragmentShader < 0)
    {
        // Delete any shaders that were actually compiled
        if (vertexShader >= 0) {glDeleteShader(vertexShader);}
        if (fragmentShader >= 0) {glDeleteShader(fragmentShader);}
        return false;
    }

    *pprogram = glCreateProgram();
    glAttachShader(*pprogram, vertexShader);
    glAttachShader(*pprogram, fragmentShader);
    glLinkProgram(*pprogram);
    GLint status;
    glGetProgramiv(*pprogram, GL_LINK_STATUS, &status);
    if (status != GL_TRUE)
    {
        fprintf(stderr, "Could not link shaders; interface mismatch?\n");
        GLint logLength;
        glGetProgramiv(*pprogram, GL_INFO_LOG_LENGTH, &logLength);
        char* pInfoLog = malloc(logLength + 1);
        glGetProgramInfoLog(*pprogram, logLength + 1, &logLength, pInfoLog);
        fprintf(stderr, "Error log: %s\n", pInfoLog);
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
        glDeleteProgram(*pprogram);
        free(pInfoLog);
        return false;
    }

    return true;
}

const char* errorToStr(GLenum error)
{
    switch(error)
    {
        case GL_INVALID_ENUM:
            return "GL_INVALID_ENUM";
        case GL_INVALID_OPERATION:
            return "GL_INVALID_OPERATION";
        case GL_INVALID_VALUE:
            return "GL_INVALID_VALUE";
#ifdef GL_INVALID_INDEX
        case GL_INVALID_INDEX:
            return "GL_INVALID_INDEX";
#endif // GL_INVALID_INDEX
        default:
            return "Unknown error";
    }
}

GLuint textureID = 0;

float verts[] = {
        // pos                           // uv
        1.0f,  -1.0f, 0.0f, 1.0f, 1.0f,
        1.0f, 1.0f, 0.0f, 1.0f, 0.0f,
        -1.0f,  1.0f, 0.0f, 0.0f, 0.0f,
        -1.0f,  -1.0f, 0.0f, 0.0f, 1.0f,
};

GLuint idx[] = {0, 1, 2, 0, 2, 3};

GLuint vbo = 0, ibo = 0, vao = 0;
#endif

/*Initialize your display and the required peripherals.*/
static void disp_init(void)
{
    /*You code here*/
    lv_memzero(framebuffer, MY_DISP_HOR_RES * MY_DISP_VER_RES * sizeof(uint16_t));
#ifdef OPENGL
    if (!glfwInit()) {
        fprintf(stderr, "glfwInit failed\n");
        return;
    }

    glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_API);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
//    glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE);
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

    window = glfwCreateWindow(MY_DISP_HOR_RES, MY_DISP_VER_RES, "LVGL", NULL, NULL);
    if (!window)
    {
        glfwTerminate();
        fprintf(stderr, "glfwCreateWindow failed\n");
        return;
    }
    glfwMakeContextCurrent(window);

    glGenVertexArrays(1, &vao);
    glBindVertexArray(vao);

    glGenTextures(1, &textureID);
    glBindTexture(GL_TEXTURE_2D, textureID);
//    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, 960, 960, 0, GL_RGB, GL_UNSIGNED_BYTE, framebuffer);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

    glCheckError();

    glGenBuffers(1, &vbo);
    glGenBuffers(1, &ibo);
    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo);

    glCheckError();

    glBufferData(GL_ARRAY_BUFFER, sizeof(verts), verts,
                 GL_STATIC_DRAW);
    glBufferData(GL_ELEMENT_ARRAY_BUFFER, sizeof(idx), idx,
                 GL_STATIC_DRAW);
    glCheckError();

    glEnableVertexAttribArray(0);
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 5 * sizeof(float), (void*)0);
    glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 5 * sizeof(float), (void*)(3 * sizeof(float)));
    glCheckError();

    GLuint program;
    if (!compileShaders(&program))
        return;
    glUseProgram(program);
    glCheckError();
#endif
}

volatile bool disp_flush_enabled = true;

/* Enable updating the screen (the flushing process) when disp_flush() is called by LVGL
 */
void disp_enable_update(void)
{
    disp_flush_enabled = true;
}

/* Disable updating the screen (the flushing process) when disp_flush() is called by LVGL
 */
void disp_disable_update(void)
{
    disp_flush_enabled = false;
}

/*Flush the content of the internal buffer the specific area on the display.
 *`px_map` contains the rendered image as raw pixel map and it should be copied to `area` on the display.
 *You can use DMA or any hardware acceleration to do this operation in the background but
 *'lv_display_flush_ready()' has to be called when it's finished.*/
static void disp_flush(lv_display_t * disp_drv, const lv_area_t * area, uint8_t * px_map)
{
    if(disp_flush_enabled) {
        /*The most simple case (but also the slowest) to put all pixels to the screen one-by-one*/
        int32_t x;
        int32_t y;
        int32_t i;
        for(y = area->y1; y <= area->y2; y++) {
            memcpy_vec(&framebuffer[(area->x1 + y * MY_DISP_HOR_RES) * BYTE_PER_PIXEL],
                      px_map, BYTE_PER_PIXEL * (area->x2 - area->x1 + 1));
            px_map += (BYTE_PER_PIXEL * (area->x2 - area->x1 + 1));
//            for(x = area->x1; x <= area->x2; x++) {
////                for (i = 0; i < BYTE_PER_PIXEL; i++) {
//                    /*Put a pixel to the display. For example:*/
//                    /*put_px(x, y, *px_map)*/
//                    framebuffer[(x + y * MY_DISP_HOR_RES) * BYTE_PER_PIXEL + 2] = *px_map;
//                    px_map++;
//                    framebuffer[(x + y * MY_DISP_HOR_RES) * BYTE_PER_PIXEL + 1] = *px_map;
//                    px_map++;
//                    framebuffer[(x + y * MY_DISP_HOR_RES) * BYTE_PER_PIXEL + 0] = *px_map;
//                    px_map++;
////                }
//            }
        }
    }

#ifdef OPENGL
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, MY_DISP_HOR_RES, MY_DISP_VER_RES, 0, GL_RGB, GL_UNSIGNED_BYTE, framebuffer);

    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
    glCheckError();
    glfwSwapBuffers(window);
    glfwPollEvents();
#endif
    *counter = 0;
    /*IMPORTANT!!!
     *Inform the graphics library that you are ready with the flushing*/
    lv_display_flush_ready(disp_drv);
}

#else /*Enable this file at the top*/

/*This dummy typedef exists purely to silence -Wpedantic.*/
typedef int keep_pedantic_happy;
#endif
