/*
 * tms9900_sim.c -- picoJVM platform for TMS9900 simulator.
 *
 * Builds against the shared core with platform shims for the tms9900-trace
 * simulator. The .pjvm program data is linked in as an extern const array.
 *
 * Output: putchar writes bytes sequentially to 0xEF00.
 * Heap: uses memory from _end (after BSS) up to 0xEEFF.
 */

#include <stdint.h>

#define OUTPUT_BASE 0xEF00
#define OUTPUT_DATA 0xEF02

static uint16_t output_ptr = OUTPUT_DATA;

extern const uint8_t pjvm_program[];
extern uint8_t _end[];

#include "../src/pjvm.h"

uint16_t heap_alloc(uint16_t size, uint8_t kind) {
    uint16_t a = pjvm_heap_alloc(size, kind);
    if (a == 0) {
        pjvm_platform_trap(0xFE, size);
    }
    return a;
}

uint8_t r8(uint16_t a) {
    return *(uint8_t *)(uintptr_t)a;
}

void w8(uint16_t a, uint8_t v) {
    *(uint8_t *)(uintptr_t)a = v;
}

uint16_t r16(uint16_t a) {
    uint8_t *p = (uint8_t *)(uintptr_t)a;
    return (uint16_t)p[0] | ((uint16_t)p[1] << 8);
}

void w16(uint16_t a, uint16_t v) {
    uint8_t *p = (uint8_t *)(uintptr_t)a;
    p[0] = (uint8_t)v;
    p[1] = (uint8_t)(v >> 8);
}

void pjvm_platform_putchar(uint8_t ch) {
    *(volatile uint8_t *)(uintptr_t)output_ptr = ch;
    output_ptr++;
}

uint8_t pjvm_platform_peek8(uint32_t a) {
    return *(uint8_t *)(uintptr_t)(uint16_t)a;
}

void pjvm_platform_poke8(uint32_t a, uint8_t v) {
    *(uint8_t *)(uintptr_t)(uint16_t)a = v;
}

void pjvm_platform_out(uint16_t port, uint16_t val) {
    (void)port;
    (void)val;
}

int32_t pjvm_platform_term_info(uint16_t code) {
    if (code == 0) return 64;
    if (code == 1) return 16;
    return 0;
}

int32_t pjvm_platform_key_read(void) {
    return -1;
}

int32_t pjvm_platform_ticks(void) {
    return 0;
}

void pjvm_platform_trap(uint8_t op, uint16_t pc) {
    *(volatile uint8_t *)(uintptr_t)OUTPUT_DATA = op;
    *(volatile uint8_t *)(uintptr_t)(OUTPUT_DATA + 1) = (uint8_t)pc;
    *(volatile uint8_t *)(uintptr_t)(OUTPUT_DATA + 2) = (uint8_t)(pc >> 8);
    *(volatile uint8_t *)(uintptr_t)OUTPUT_BASE = 0;
    *(volatile uint8_t *)(uintptr_t)(OUTPUT_BASE + 1) = 3;
    for (;;) {}
}

int32_t pjvm_platform_file_open(const uint8_t *name, uint8_t nameLen, uint8_t mode) {
    (void)name;
    (void)nameLen;
    (void)mode;
    return -1;
}

int32_t pjvm_platform_file_read_byte(void) {
    return -1;
}

void pjvm_platform_file_write_byte(uint8_t b) {
    (void)b;
}

void pjvm_platform_file_close(uint8_t mode) {
    (void)mode;
}

int32_t pjvm_platform_file_delete(const uint8_t *name, uint8_t nameLen) {
    (void)name;
    (void)nameLen;
    return -1;
}

int main(void) {
    static PJVMCtx ctx;

    output_ptr = OUTPUT_DATA;
    pjvm_prog = (uint8_t *)pjvm_program;
    pjvm_parse(pjvm_prog);
    if ((uint16_t)(uintptr_t)_end >= OUTPUT_BASE) {
        pjvm_platform_trap(PJVM_TRAP_CAPACITY, (uint16_t)(uintptr_t)_end);
    }
    pjvm_heap_init(&ctx, (uint16_t)(uintptr_t)_end, OUTPUT_BASE);
    pjvm_run(&ctx);

    {
        uint16_t count = output_ptr - OUTPUT_DATA;
        *(volatile uint8_t *)(uintptr_t)OUTPUT_BASE = (uint8_t)(count >> 8);
        *(volatile uint8_t *)(uintptr_t)(OUTPUT_BASE + 1) = (uint8_t)count;
    }
    return 0;
}
