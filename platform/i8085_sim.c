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

/* --- File I/O via disk emulator ports (0xF0-0xF3) -------------------- */
static inline void io_out(uint8_t port, uint8_t val) {
    __asm__ volatile("out %0" : : "iN"(port), "a"(val));
}

static inline uint8_t io_in(uint8_t port) {
    uint8_t val;
    __asm__ volatile("in %1" : "=a"(val) : "iN"(port));
    return val;
}

int32_t pjvm_platform_file_open(const uint8_t *name, uint8_t nameLen, uint8_t mode) {
    /* Copy filename into a known memory location for disk emu */
    uint8_t *fname_buf = (uint8_t *)(uintptr_t)0x6F00;
    for (uint8_t i = 0; i < nameLen; i++)
        fname_buf[i] = name[i];
    /* Set address to filename buffer */
    io_out(0xF2, 0x00);  /* addr lo = 0x00 */
    io_out(0xF3, 0x6F);  /* addr hi = 0x6F → 0x6F00 */
    /* Set filename length */
    io_out(0xF1, nameLen);
    /* Issue OPEN command */
    io_out(0xF0, mode);   /* 1=OPEN_READ, 2=OPEN_WRITE */
    /* Check status */
    uint8_t status = io_in(0xF0);
    return status == 0 ? 0 : -1;
}

int32_t pjvm_platform_file_read_byte(void) {
    /* Issue READ_BYTE command */
    io_out(0xF0, 0x03);
    uint8_t status = io_in(0xF0);
    if (status == 0x01) return -1;  /* EOF */
    if (status != 0x00) return -1;  /* error */
    return (int32_t)(uint8_t)io_in(0xF1);
}

void pjvm_platform_file_write_byte(uint8_t b) {
    io_out(0xF1, b);       /* set data byte */
    io_out(0xF0, 0x04);    /* WRITE_BYTE command */
}

void pjvm_platform_file_close(uint8_t mode) {
    io_out(0xF1, mode);    /* 0=both, 1=read, 2=write */
    io_out(0xF0, 0x05);    /* CLOSE command */
}

int32_t pjvm_platform_file_delete(const uint8_t *name, uint8_t nameLen) {
    uint8_t *fname_buf = (uint8_t *)(uintptr_t)0x6F00;
    for (uint8_t i = 0; i < nameLen; i++)
        fname_buf[i] = name[i];
    io_out(0xF2, 0x00);  /* addr lo */
    io_out(0xF3, 0x6F);  /* addr hi → 0x6F00 */
    io_out(0xF1, nameLen);
    io_out(0xF0, 0x06);  /* DELETE command */
    uint8_t status = io_in(0xF0);
    return status == 0 ? 0 : -1;
}

int main(void) {
    static PJVMCtx ctx;  /* BSS — auto-zeroed by CRT */

    pjvm_prog = (uint8_t *)pjvm_program;
    pjvm_parse(pjvm_prog);
    ctx.heap_ptr = (uint16_t)(uintptr_t)_end;
    pjvm_run(&ctx);
    return 0;
}
