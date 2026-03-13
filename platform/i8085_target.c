/*
 * i8085_target.c — picoJVM interpreter for 8085.
 *
 * This file provides the platform shims around the shared split-half core.
 */

#include <stdint.h>

#define OUTPUT_PORT (*(volatile uint8_t *)0x0200)
#define PJVM_DATA    ((uint8_t *)0x1000)
#define HEAP_START  0x4000

#include "../pjvm.h"

uint16_t heap_alloc(PJVMCtx *j, uint16_t size) {
    uint16_t a = j->heap_ptr;
    uint8_t *p = (uint8_t *)a;

    j->heap_ptr = (uint16_t)(j->heap_ptr + size);

    for (uint16_t i = 0; i < size; i++) {
        p[i] = 0;
    }

    return a;
}

uint8_t r8(uint16_t a) {
    return *(uint8_t *)a;
}

void w8(uint16_t a, uint8_t v) {
    *(uint8_t *)a = v;
}

uint16_t r16(uint16_t a) {
    uint8_t *p = (uint8_t *)a;
    return (uint16_t)p[0] | ((uint16_t)p[1] << 8);
}

void w16(uint16_t a, uint16_t v) {
    uint8_t *p = (uint8_t *)a;
    p[0] = (uint8_t)v;
    p[1] = (uint8_t)(v >> 8);
}

void pjvm_platform_putchar(uint8_t ch) {
    OUTPUT_PORT = ch;
}

uint8_t pjvm_platform_peek8(uint32_t a) {
    return *(uint8_t *)(uintptr_t)(uint16_t)a;
}

void pjvm_platform_poke8(uint32_t a, uint8_t v) {
    *(uint8_t *)(uintptr_t)a = v;
}

void pjvm_platform_trap(uint8_t op, uint16_t pc) {
    (void)op;
    (void)pc;
    __asm__ volatile("hlt");
}

static void pjvm_load(void) {
    pjvm_prog = PJVM_DATA;
    pjvm_parse(PJVM_DATA);
}

void pjvm_main(void) {
    static PJVMCtx ctx;  /* BSS — auto-zeroed by CRT */

    pjvm_load();
    ctx.heap_ptr = HEAP_START;
    pjvm_run(&ctx);
    __asm__ volatile("hlt");
}
