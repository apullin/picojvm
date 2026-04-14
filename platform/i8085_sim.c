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
#define HEAP_END 0x7000

/* Embedded .pjvm program data (provided by pjvm_data.c) */
extern const uint8_t pjvm_program[];

/* Heap starts after BSS (linker symbol) */
extern uint8_t _end[];

#include "../src/pjvm.h"

uint16_t heap_alloc(PJVMCtx *j, uint16_t size, uint8_t kind) {
    uint16_t a = pjvm_heap_alloc(j, size, kind);
    if (a == 0) pjvm_platform_trap(0xFE, 0);
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

/* --- Port I/O macros ------------------------------------------------- */
/* 8085 IN/OUT require immediate port — use "r" constraint + MOV A,reg.
 * Port arg must be a compile-time constant (stringified into the asm). */
#define _IO_OUT(port, val) do { \
    uint8_t _v = (val); \
    __asm__ volatile("mov a,%0\n\tout " #port : : "r"(_v) : "a"); \
} while(0)
#define IO_OUT(port, val) _IO_OUT(port, val)

#define _IO_IN(port) ({ \
    uint8_t _v; \
    __asm__ volatile("in " #port "\n\tmov %0,a" : "=r"(_v) : : "a"); \
    _v; \
})
#define IO_IN(port) _IO_IN(port)

/* --- Disk emulator port definitions ---------------------------------- */
#define DISK_CMD   0xF0    /* command / status */
#define DISK_DATA  0xF1    /* data / param byte */
#define DISK_ADRL  0xF2    /* buffer address low */
#define DISK_ADRH  0xF3    /* buffer address high */
#define DISK_OPEN_READ  1
#define DISK_OPEN_WRITE 2
#define DISK_READ_BYTE  3
#define DISK_WRITE_BYTE 4
#define DISK_CLOSE      5
#define DISK_DELETE      6
#define DISK_FNAME_BUF  0x6F00

int32_t pjvm_platform_file_open(const uint8_t *name, uint8_t nameLen, uint8_t mode) {
    uint8_t *fname_buf = (uint8_t *)(uintptr_t)DISK_FNAME_BUF;
    for (uint8_t i = 0; i < nameLen; i++)
        fname_buf[i] = name[i];
    IO_OUT(DISK_ADRL, 0x00);           /* addr lo = 0x00 */
    IO_OUT(DISK_ADRH, 0x6F);           /* addr hi = 0x6F → 0x6F00 */
    IO_OUT(DISK_DATA, nameLen);
    IO_OUT(DISK_CMD, mode);            /* 1=OPEN_READ, 2=OPEN_WRITE */
    uint8_t status = IO_IN(DISK_CMD);
    return status == 0 ? 0 : -1;
}

int32_t pjvm_platform_file_read_byte(void) {
    IO_OUT(DISK_CMD, DISK_READ_BYTE);
    uint8_t status = IO_IN(DISK_CMD);
    if (status == 0x01) return -1;     /* EOF */
    if (status != 0x00) return -1;     /* error */
    return (int32_t)(uint8_t)IO_IN(DISK_DATA);
}

void pjvm_platform_file_write_byte(uint8_t b) {
    IO_OUT(DISK_DATA, b);
    IO_OUT(DISK_CMD, DISK_WRITE_BYTE);
}

void pjvm_platform_file_close(uint8_t mode) {
    IO_OUT(DISK_DATA, mode);           /* 0=both, 1=read, 2=write */
    IO_OUT(DISK_CMD, DISK_CLOSE);
}

int32_t pjvm_platform_file_delete(const uint8_t *name, uint8_t nameLen) {
    uint8_t *fname_buf = (uint8_t *)(uintptr_t)DISK_FNAME_BUF;
    for (uint8_t i = 0; i < nameLen; i++)
        fname_buf[i] = name[i];
    IO_OUT(DISK_ADRL, 0x00);
    IO_OUT(DISK_ADRH, 0x6F);           /* addr hi → 0x6F00 */
    IO_OUT(DISK_DATA, nameLen);
    IO_OUT(DISK_CMD, DISK_DELETE);
    uint8_t status = IO_IN(DISK_CMD);
    return status == 0 ? 0 : -1;
}

int main(void) {
    static PJVMCtx ctx;  /* BSS — auto-zeroed by CRT */

    pjvm_prog = (uint8_t *)pjvm_program;
    pjvm_parse(pjvm_prog);
    pjvm_heap_init(&ctx, (uint16_t)(uintptr_t)_end, HEAP_END);
    pjvm_run(&ctx);
    return 0;
}
