/*
 * i8085_target.c — picoJVM interpreter for 8085.
 *
 * This file provides the platform shims around the shared split-half core.
 */

#include <stdint.h>

#define OUTPUT_PORT (*(volatile uint8_t *)0x0200)
#define PJVM_DATA    ((uint8_t *)0x1000)
#define HEAP_START  0x4000
#define HEAP_END    0x7E00

#include "../src/pjvm.h"

uint16_t heap_alloc(PJVMCtx *j, uint16_t size) {
    uint16_t a = pjvm_heap_alloc(j, size);
    if (a == 0) pjvm_platform_trap(0xFE, 0);
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

void pjvm_platform_out(uint16_t port, uint16_t val) {
    (void)port; (void)val;
}

void pjvm_platform_trap(uint8_t op, uint16_t pc) {
    (void)op;
    (void)pc;
    __asm__ volatile("hlt");
}

int32_t pjvm_platform_file_open(const uint8_t *name, uint8_t nameLen, uint8_t mode) {
    (void)name; (void)nameLen; (void)mode;
    return -1;
}
int32_t pjvm_platform_file_read_byte(void) { return -1; }
void pjvm_platform_file_write_byte(uint8_t b) { (void)b; }
void pjvm_platform_file_close(uint8_t mode) { (void)mode; }
int32_t pjvm_platform_file_delete(const uint8_t *name, uint8_t nameLen) { (void)name; (void)nameLen; return -1; }

static void pjvm_load(void) {
    pjvm_prog = PJVM_DATA;
    pjvm_parse(PJVM_DATA);
}

void pjvm_main(void) {
    static PJVMCtx ctx;  /* BSS — auto-zeroed by CRT */

    pjvm_load();
    pjvm_heap_init(&ctx, HEAP_START, HEAP_END);
    pjvm_run(&ctx);
    __asm__ volatile("hlt");
}
