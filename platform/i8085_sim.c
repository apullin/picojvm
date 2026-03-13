/*
 * i8085_sim.c — picoJVM interpreter for 8085 simulator.
 *
 * Builds against the shared core with platform shims for the i8085-trace
 * simulator.  The .pjvm program data is linked in as an extern const array
 * (generated from pjvmpack.py output).
 */

#include <stdint.h>

/* Output buffer: putchar writes sequentially into free RAM above BSS.
 * BSS ends at ~0x0AE8, stack is near 0x7FB0. 0x7000 is safely between. */
static uint16_t output_ptr = 0x7000;

/* Embedded .pjvm program data (provided by pjvm_data.c) */
extern const uint8_t pjvm_program[];

/* Heap starts after BSS (linker symbol) */
extern uint8_t _end[];

#include "../pjvm.h"

uint16_t heap_alloc(PJVMCtx *j, uint16_t size) {
    uint16_t a = j->heap_ptr;
    uint8_t *p = (uint8_t *)(uintptr_t)a;

    j->heap_ptr = (uint16_t)(j->heap_ptr + size);

    for (uint16_t i = 0; i < size; i++) {
        p[i] = 0;
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
    *(uint8_t *)(uintptr_t)a = v;
}

void pjvm_platform_out(uint16_t port, uint16_t val) {
    (void)port;
    (void)val;
}

void pjvm_platform_trap(uint8_t op, uint16_t pc) {
    (void)op;
    (void)pc;
    __asm__ volatile("hlt");
}

int main(void) {
    static PJVMCtx ctx;  /* BSS — auto-zeroed by CRT */

    pjvm_prog = (uint8_t *)pjvm_program;
    pjvm_parse(pjvm_prog);
    ctx.heap_ptr = (uint16_t)(uintptr_t)_end;
    pjvm_run(&ctx);
    return 0;
}
