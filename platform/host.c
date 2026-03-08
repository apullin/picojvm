/*
 * picojvm.c — picoJVM host interpreter.
 *
 * Uses the same split-half interpreter core as the 8085 target build so
 * representation and dispatch changes can be tested on the host.
 */

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>

#define PJVM_METHOD_CAP 256
#define PJVM_CLASS_CAP 64
#define PJVM_VTABLE_CAP 256
#define PJVM_STATIC_CAP 64
#define PJVM_MAX_STACK 256
#define PJVM_MAX_LOCALS 1024
#define PJVM_MAX_FRAMES 64
#define PJVM_TRACK_STATS

#define RAW_MEM_SIZE 65536u
#define HEAP_MEM_SIZE 65536u
#define HEAP_BASE 1u

static uint8_t raw_mem[RAW_MEM_SIZE];
static uint8_t heap_mem[HEAP_MEM_SIZE];
static uint8_t *prog_data;
static size_t prog_size;
static uint16_t heap_alloc_count;
static uint32_t heap_bytes_used;

#include "../core.h"

static uint16_t heap_alloc(PJVMCtx *j, uint16_t size) {
    uint16_t a = j->heap_ptr;
    uint32_t end = (uint32_t)a + size;

    if (end > HEAP_MEM_SIZE) {
        fprintf(stderr, "JVM heap overflow (%u + %u > %u)\n",
                (unsigned)a, (unsigned)size, (unsigned)HEAP_MEM_SIZE);
        exit(1);
    }

    j->heap_ptr = (uint16_t)end;

    for (uint16_t i = 0; i < size; i++) {
        heap_mem[a + i] = 0;
    }

    heap_alloc_count++;
    heap_bytes_used += size;
    return a;
}

static uint8_t r8(uint16_t a) {
    return heap_mem[a];
}

static void w8(uint16_t a, uint8_t v) {
    heap_mem[a] = v;
}

static uint16_t r16(uint16_t a) {
    return (uint16_t)heap_mem[a] | ((uint16_t)heap_mem[(uint16_t)(a + 1)] << 8);
}

static void w16(uint16_t a, uint16_t v) {
    heap_mem[a] = (uint8_t)v;
    heap_mem[(uint16_t)(a + 1)] = (uint8_t)(v >> 8);
}

static void pjvm_platform_putchar(uint8_t ch) {
    putchar((int)ch);
}

static uint8_t pjvm_platform_peek8(uint16_t a) {
    return raw_mem[a];
}

static void pjvm_platform_poke8(uint16_t a, uint8_t v) {
    raw_mem[a] = v;
}

static void pjvm_platform_trap(uint8_t op, uint16_t pc) {
    if (op == 0xFF) {
        fprintf(stderr, "Unknown native method at bytecode offset %u\n",
                (unsigned)pc);
    } else {
        fprintf(stderr, "Unimplemented opcode 0x%02X at bytecode offset %u\n",
                op, (unsigned)pc);
    }
    exit(1);
}

static void load_pjvm(const char *path) {
    FILE *f = fopen(path, "rb");

    if (!f) {
        perror(path);
        exit(1);
    }

    if (fseek(f, 0, SEEK_END) != 0) {
        perror("fseek");
        exit(1);
    }

    prog_size = (size_t)ftell(f);

    if (fseek(f, 0, SEEK_SET) != 0) {
        perror("fseek");
        exit(1);
    }

    prog_data = (uint8_t *)malloc(prog_size);
    if (!prog_data) {
        fprintf(stderr, "malloc failed (%zu bytes)\n", prog_size);
        exit(1);
    }

    if (fread(prog_data, 1, prog_size, f) != prog_size) {
        perror("fread");
        exit(1);
    }

    fclose(f);

    if (prog_data[0] != 0x85 || prog_data[1] != 0x4A) {
        fprintf(stderr, "Bad .pjvm magic\n");
        exit(1);
    }

    pjvm_parse(prog_data);
}

int main(int argc, char **argv) {
    PJVMCtx ctx = {0};

    if (argc < 2) {
        fprintf(stderr, "Usage: picojvm <program.pjvm>\n");
        return 1;
    }

    load_pjvm(argv[1]);

    fprintf(stderr, "picoJVM | %s | %u methods, %u classes, %u bytes bytecode\n",
            argv[1], (unsigned)n_methods, (unsigned)n_classes,
            (unsigned)bytecodes_size);

    ctx.heap_ptr = HEAP_BASE;
    pjvm_run(&ctx);
    fflush(stdout);

    fprintf(stderr,
            "HALT | heap: %u obj, %uB | stack: %u/%u slots | locals: %u/%u | frames: %u/%u\n",
            (unsigned)heap_alloc_count, (unsigned)heap_bytes_used,
            (unsigned)ctx.sp_max, (unsigned)PJVM_MAX_STACK,
            (unsigned)ctx.lt_max, (unsigned)PJVM_MAX_LOCALS,
            (unsigned)ctx.fdepth_max, (unsigned)PJVM_MAX_FRAMES);
    return 0;
}
