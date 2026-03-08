/*
 * picojvm.c — picoJVM host interpreter.
 *
 * Uses the same split-half interpreter core as the 8085 target build so
 * representation and dispatch changes can be tested on the host.
 */

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

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

#ifdef PJVM_PAGED
static FILE *pjvm_file_handle;

static void pjvm_host_read(uint32_t file_offset, uint8_t *buf, uint16_t len, void *ctx) {
    FILE *f = (FILE *)ctx;
    fseek(f, (long)file_offset, SEEK_SET);
    fread(buf, 1, len, f);
}
#endif

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

#ifdef PJVM_PAGED
    pjvm_file_handle = f;  /* keep open for paged reads */
#else
    fclose(f);
#endif

    if (prog_data[0] != 0x85 || (prog_data[1] != 0x4A && prog_data[1] != 0x4B)) {
        fprintf(stderr, "Bad .pjvm magic\n");
        exit(1);
    }

    pjvm_prog = prog_data;
    pjvm_prog_size = (uint32_t)prog_size;
    pjvm_parse(prog_data);
}

int main(int argc, char **argv) {
    PJVMCtx ctx = {0};

    if (argc < 2) {
        fprintf(stderr, "Usage: picojvm <program.pjvm> [--cache=N] [--pin=0,3,7]\n");
        return 1;
    }

    load_pjvm(argv[1]);

    fprintf(stderr, "picoJVM | %s | %u methods, %u classes, %u bytes bytecode\n",
            argv[1], (unsigned)n_methods, (unsigned)n_classes,
            (unsigned)bytecodes_size);

#ifdef PJVM_PAGED
    /* Set up fixed-page pager */
    static PJVMPager pager;

    uint16_t page_size = 1024;
    uint8_t  n_pages = 4;

    for (int i = 2; i < argc; i++) {
        if (strncmp(argv[i], "--page-size=", 12) == 0)
            page_size = (uint16_t)atoi(argv[i] + 12);
        else if (strncmp(argv[i], "--pages=", 8) == 0)
            n_pages = (uint8_t)atoi(argv[i] + 8);
        else if (strncmp(argv[i], "--cache=", 8) == 0) {
            /* Shorthand: --cache=N sets total cache bytes */
            uint32_t cache = (uint32_t)atoi(argv[i] + 8);
            n_pages = (uint8_t)(cache / page_size);
            if (n_pages < 2) n_pages = 2;
        }
    }

    if (n_pages > PJVM_MAX_PAGES) n_pages = PJVM_MAX_PAGES;

    /* Compute page_shift from page_size (must be power of 2) */
    uint8_t page_shift = 0;
    { uint16_t ps = page_size; while (ps > 1) { ps >>= 1; page_shift++; } }

    pager.page_size = page_size;
    pager.page_shift = page_shift;
    pager.n_pages = n_pages;
    pager.file_size = pjvm_prog_size;
    pager.read_fn = pjvm_host_read;
    pager.read_ctx = pjvm_file_handle;
    pjvm_pager_init(&pager);

    /* Allocate pool */
    pager.pool = (uint8_t *)malloc((uint32_t)n_pages * page_size);
    if (!pager.pool) {
        fprintf(stderr, "Failed to allocate %u byte pager pool\n",
                (unsigned)((uint32_t)n_pages * page_size));
        return 1;
    }

    /* Apply explicit CLI pin overrides (pin chunks) */
    for (int i = 2; i < argc; i++) {
        if (strncmp(argv[i], "--pin=", 6) == 0) {
            const char *s = argv[i] + 6;
            while (*s) {
                int chunk = atoi(s);
                pjvm_pin_chunk(&pager, (uint16_t)chunk);
                while (*s && *s != ',') s++;
                if (*s == ',') s++;
            }
        }
    }

    ctx.pager = &pager;
    fprintf(stderr, "PAGE | %u pages × %uB = %uB pool\n",
            (unsigned)n_pages, (unsigned)page_size,
            (unsigned)((uint32_t)n_pages * page_size));
#endif

    ctx.heap_ptr = HEAP_BASE;
    pjvm_run(&ctx);
    fflush(stdout);

    fprintf(stderr,
            "HALT | heap: %u obj, %uB | stack: %u/%u slots | locals: %u/%u | frames: %u/%u\n",
            (unsigned)heap_alloc_count, (unsigned)heap_bytes_used,
            (unsigned)ctx.sp_max, (unsigned)PJVM_MAX_STACK,
            (unsigned)ctx.lt_max, (unsigned)PJVM_MAX_LOCALS,
            (unsigned)ctx.fdepth_max, (unsigned)PJVM_MAX_FRAMES);

#ifdef PJVM_PAGED
    fprintf(stderr,
            "PAGE | hits: %u, misses: %u, rate: %.1f%%\n",
            (unsigned)pager.hits, (unsigned)pager.misses,
            pager.hits + pager.misses > 0
                ? 100.0 * pager.hits / (pager.hits + pager.misses)
                : 0.0);
    fprintf(stderr, "PAGE | per-slot misses:");
    for (uint8_t i = 0; i < pager.n_pages; i++)
        fprintf(stderr, " [%u]=%u", (unsigned)i, (unsigned)pager.slot_misses[i]);
    fprintf(stderr, "\n");
    fclose(pjvm_file_handle);
    free(pager.pool);
#endif

    return 0;
}
