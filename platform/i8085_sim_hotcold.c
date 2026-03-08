/*
 * i8085_sim_hotcold.c — picoJVM interpreter for 8085.
 *
 * This file provides the platform shims around the shared split-half core.
 */

#include <stdint.h>

#define OUTPUT_PORT (*(volatile uint8_t *)0x0200)
#define PJVM_DATA    ((uint8_t *)0x1000)
#define HEAP_START  0x4000

#define PJVM_METHOD_CAP 64
#define PJVM_CLASS_CAP 16
#define PJVM_VTABLE_CAP 128
#define PJVM_STATIC_CAP 32
#define PJVM_MAX_STACK 64
#define PJVM_MAX_LOCALS 128
#define PJVM_MAX_FRAMES 16

#include "i8085_core_hotcold.h"

static uint16_t heap_alloc(PJVMCtx *j, uint16_t size) {
    uint16_t a = j->cold.heap_ptr;
    uint8_t *p = (uint8_t *)a;

    j->cold.heap_ptr = (uint16_t)(j->cold.heap_ptr + size);

    for (uint16_t i = 0; i < size; i++) {
        p[i] = 0;
    }

    return a;
}

static uint16_t r16(uint16_t a) {
    uint8_t *p = (uint8_t *)a;
    return (uint16_t)p[0] | ((uint16_t)p[1] << 8);
}

static void w16(uint16_t a, uint16_t v) {
    uint8_t *p = (uint8_t *)a;
    p[0] = (uint8_t)v;
    p[1] = (uint8_t)(v >> 8);
}

static void pjvm_platform_putchar(uint8_t ch) {
    OUTPUT_PORT = ch;
}

static uint8_t pjvm_platform_peek8(uint16_t a) {
    return *(uint8_t *)(uintptr_t)a;
}

static void pjvm_platform_poke8(uint16_t a, uint8_t v) {
    *(uint8_t *)(uintptr_t)a = v;
}

static void pjvm_platform_trap(uint8_t op, uint16_t pc) {
    (void)op;
    (void)pc;
    __asm__ volatile("hlt");
}

static void pjvm_load(void) {
    pjvm_parse(PJVM_DATA);
}

void pjvm_main(void) {
    PJVMCtx ctx = {0};

    pjvm_load();
    ctx.cold.heap_ptr = HEAP_START;
    pjvm_run(&ctx);
    __asm__ volatile("hlt");
}
