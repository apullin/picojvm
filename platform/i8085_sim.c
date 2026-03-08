/*
 * i8085_sim.c — picoJVM interpreter for 8085 simulator.
 *
 * Builds against the shared core with platform shims for the i8085-trace
 * simulator.  The .pjvm program data is linked in as an extern const array
 * (generated from pjvmpack.py output).
 */

#include <stdint.h>

#define PJVM_ASM_HELPERS

/* Output buffer: putchar writes sequentially starting at 0x0200 */
static uint16_t output_ptr = 0x0200;

/* Embedded .pjvm program data (provided by pjvm_data.c) */
extern const uint8_t pjvm_program[];

/* Heap starts after BSS (linker symbol) */
extern uint8_t _end[];

#define PJVM_METHOD_CAP 64
#define PJVM_CLASS_CAP 16
#define PJVM_VTABLE_CAP 128
#define PJVM_STATIC_CAP 32
#define PJVM_MAX_STACK 64
#define PJVM_MAX_LOCALS 128
#define PJVM_MAX_FRAMES 16

#include "../core.h"

static uint16_t heap_alloc(PJVMCtx *j, uint16_t size) {
    uint16_t a = j->heap_ptr;
    uint8_t *p = (uint8_t *)(uintptr_t)a;

    j->heap_ptr = (uint16_t)(j->heap_ptr + size);

    for (uint16_t i = 0; i < size; i++) {
        p[i] = 0;
    }

    return a;
}

static uint8_t r8(uint16_t a) {
    return *(uint8_t *)(uintptr_t)a;
}

static void w8(uint16_t a, uint8_t v) {
    *(uint8_t *)(uintptr_t)a = v;
}

static uint16_t r16(uint16_t a) {
    uint8_t *p = (uint8_t *)(uintptr_t)a;
    return (uint16_t)p[0] | ((uint16_t)p[1] << 8);
}

static void w16(uint16_t a, uint16_t v) {
    uint8_t *p = (uint8_t *)(uintptr_t)a;
    p[0] = (uint8_t)v;
    p[1] = (uint8_t)(v >> 8);
}

static void pjvm_platform_putchar(uint8_t ch) {
    *(volatile uint8_t *)(uintptr_t)output_ptr = ch;
    output_ptr++;
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

int main(void) {
    PJVMCtx ctx = {0};

    pjvm_parse((uint8_t *)pjvm_program);
    ctx.heap_ptr = (uint16_t)(uintptr_t)_end;
    pjvm_run(&ctx);
    return 0;
}
