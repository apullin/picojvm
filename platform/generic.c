/*
 * generic.c — picoJVM pure-C platform.
 *
 * Portable reference implementation with no platform-specific code.
 * Uses a static heap array and a function pointer for character output.
 * To port picoJVM to a new target, either:
 *   (a) Copy this file and customize the platform callbacks, or
 *   (b) Implement the callbacks declared in pjvm.h, link with src/pjvm.c
 */

#include <stdint.h>
#include "../src/pjvm.h"

#ifndef PJVM_HEAP_SIZE
#define PJVM_HEAP_SIZE 32768u
#endif

#define PJVM_HEAP_BASE 1u

static uint8_t heap_mem[PJVM_HEAP_SIZE];

/* --- Platform callbacks ----------------------------------------------- */

uint16_t heap_alloc(PJVMCtx *j, uint16_t size) {
    uint16_t a = pjvm_heap_alloc(j, size);
    if (a == 0) {
        pjvm_platform_trap(0xFE, 0);  /* heap overflow */
        return 0;
    }
    return a;
}

uint8_t r8(uint16_t a) {
    return heap_mem[a];
}

void w8(uint16_t a, uint8_t v) {
    heap_mem[a] = v;
}

uint16_t r16(uint16_t a) {
    return (uint16_t)heap_mem[a] | ((uint16_t)heap_mem[(uint16_t)(a + 1)] << 8);
}

void w16(uint16_t a, uint16_t v) {
    heap_mem[a] = (uint8_t)v;
    heap_mem[(uint16_t)(a + 1)] = (uint8_t)(v >> 8);
}

/*
 * Output hook — set this to your platform's character output function
 * before calling pjvm_run().  For example:
 *
 *   pjvm_putchar_fn = my_uart_putchar;
 */
void (*pjvm_putchar_fn)(uint8_t ch);

void pjvm_platform_putchar(uint8_t ch) {
    if (pjvm_putchar_fn) pjvm_putchar_fn(ch);
}

uint8_t pjvm_platform_peek8(uint32_t a) {
    return heap_mem[(uint16_t)a];
}

void pjvm_platform_poke8(uint32_t a, uint8_t v) {
    heap_mem[a] = v;
}

void pjvm_platform_trap(uint8_t op, uint16_t pc) {
    (void)op;
    (void)pc;
    /* Halt — override if your platform has a better mechanism */
    for (;;) {}
}

int32_t pjvm_platform_file_open(const uint8_t *name, uint8_t nameLen, uint8_t mode) {
    (void)name; (void)nameLen; (void)mode;
    return -1;  /* not supported on generic platform */
}

int32_t pjvm_platform_file_read_byte(void) { return -1; }
void pjvm_platform_file_write_byte(uint8_t b) { (void)b; }
void pjvm_platform_file_close(uint8_t mode) { (void)mode; }
int32_t pjvm_platform_file_delete(const uint8_t *name, uint8_t nameLen) { (void)name; (void)nameLen; return -1; }

/*
 * Entry point for generic platform.
 * Call with a pointer to the .pjvm binary data (already in memory).
 */
void pjvm_generic_run(uint8_t *program_data) {
    PJVMCtx ctx = {0};

    pjvm_prog = program_data;
    pjvm_parse(program_data);
    pjvm_heap_init(&ctx, PJVM_HEAP_BASE, (uint16_t)(PJVM_HEAP_BASE + PJVM_HEAP_SIZE));
    pjvm_run(&ctx);
}
