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

#define RAW_MEM_SIZE 262144u  /* 256KB — supports source files > 64K for self-hosting */
#define HEAP_MEM_SIZE 524288u
#define HEAP_BASE 1u

static uint8_t raw_mem[RAW_MEM_SIZE];
static uint8_t heap_mem[HEAP_MEM_SIZE];
static uint8_t *prog_data;
static size_t prog_size;
static uint16_t heap_alloc_count;
static uint32_t heap_bytes_used;

#include "../src/pjvm.h"

uint16_t heap_alloc(PJVMCtx *j, uint16_t size) {
    uint16_t a = j->heap_ptr;
    uint32_t end = (uint32_t)a + size;

    if (end > HEAP_MEM_SIZE) {
        fprintf(stderr, "JVM heap overflow (%u + %u > %u)\n",
                (unsigned)a, (unsigned)size, (unsigned)HEAP_MEM_SIZE);
        exit(1);
    }
    if (end > 65535u) {
        fprintf(stderr, "JVM heap_ptr overflow! (%u + %u = %u > 65535)\n",
                (unsigned)a, (unsigned)size, (unsigned)end);
        exit(1);
    }

    j->heap_ptr = (uint16_t)end;

    for (uint16_t i = 0; i < size; i++) {
        heap_mem[a + i] = 0;
    }

    heap_alloc_count++;
    heap_bytes_used += size;
    if (getenv("PJVM_HEAP_TRACE"))
        fprintf(stderr, "HEAP | alloc #%u: %u bytes at %u (heap_ptr=%u, mi=%u)\n",
                (unsigned)heap_alloc_count, (unsigned)size, (unsigned)a,
                (unsigned)end, (unsigned)g_pjvm->cur_mi);
    return a;
}

uint8_t r8(uint16_t a) {
    return heap_mem[a];
}

static uint16_t watch_addr;
void w8(uint16_t a, uint8_t v) {
    if (watch_addr && (a == watch_addr || a == (uint16_t)(watch_addr + 1))) {
        fprintf(stderr, "WATCH | w8(%u, 0x%02X) mi=%u pc=%u sp=%u\n",
                (unsigned)a, (unsigned)v, (unsigned)g_pjvm->cur_mi,
                (unsigned)g_pjvm->pc, (unsigned)g_pjvm->sp);
    }
    heap_mem[a] = v;
}

uint16_t r16(uint16_t a) {
    return (uint16_t)heap_mem[a] | ((uint16_t)heap_mem[(uint16_t)(a + 1)] << 8);
}

void w16(uint16_t a, uint16_t v) {
    if (watch_addr && (a == watch_addr || a == (uint16_t)(watch_addr + 1) ||
        (uint16_t)(a + 1) == watch_addr || (uint16_t)(a + 1) == (uint16_t)(watch_addr + 1))) {
        fprintf(stderr, "WATCH | w16(%u, 0x%04X) mi=%u pc=%u sp=%u\n",
                (unsigned)a, (unsigned)v, (unsigned)g_pjvm->cur_mi,
                (unsigned)g_pjvm->pc, (unsigned)g_pjvm->sp);
    }
    heap_mem[a] = (uint8_t)v;
    heap_mem[(uint16_t)(a + 1)] = (uint8_t)(v >> 8);
}

void pjvm_platform_putchar(uint8_t ch) {
    putchar((int)ch);
}

uint8_t pjvm_platform_peek8(uint32_t a) {
    if (a >= RAW_MEM_SIZE) return 0;
    return raw_mem[a];
}

void pjvm_platform_poke8(uint32_t a, uint8_t v) {
    if (a < RAW_MEM_SIZE) raw_mem[a] = v;
}

void pjvm_platform_out(uint16_t port, uint16_t val) {
    if (port == 0xFE) {
        fprintf(stderr, "[DBG] %c\n", (char)val);
    }
}

/* --- File I/O -------------------------------------------------------- */
static FILE *file_read_fp;
static FILE *file_write_fp;

int32_t pjvm_platform_file_open(const uint8_t *name, uint8_t nameLen, uint8_t mode) {
    char path[256];
    if (nameLen > 254) nameLen = 254;
    memcpy(path, name, nameLen);
    path[nameLen] = '\0';
    if (mode == 1) {
        if (file_read_fp) fclose(file_read_fp);
        file_read_fp = fopen(path, "rb");
        if (!file_read_fp) return -1;
        fprintf(stderr, "FILE | open read '%s'\n", path);
    } else if (mode == 2) {
        if (file_write_fp) fclose(file_write_fp);
        file_write_fp = fopen(path, "wb");
        if (!file_write_fp) return -1;
        fprintf(stderr, "FILE | open write '%s'\n", path);
    } else {
        return -1;
    }
    return 0;
}

int32_t pjvm_platform_file_read_byte(void) {
    if (!file_read_fp) return -1;
    int ch = fgetc(file_read_fp);
    if (ch == EOF) return -1;
    return (int32_t)(uint8_t)ch;
}

void pjvm_platform_file_write_byte(uint8_t b) {
    if (file_write_fp) fputc(b, file_write_fp);
    else putchar(b);  /* fallback to stdout */
}

void pjvm_platform_file_close(uint8_t mode) {
    if (mode != 2 && file_read_fp) {
        fclose(file_read_fp);
        fprintf(stderr, "FILE | closed read\n");
        file_read_fp = NULL;
    }
    if (mode != 1 && file_write_fp) {
        fclose(file_write_fp);
        fprintf(stderr, "FILE | closed write\n");
        file_write_fp = NULL;
    }
}

int32_t pjvm_platform_file_delete(const uint8_t *name, uint8_t nameLen) {
    char path[256];
    if (nameLen > 254) nameLen = 254;
    memcpy(path, name, nameLen);
    path[nameLen] = '\0';
    fprintf(stderr, "FILE | delete '%s'\n", path);
    return remove(path) == 0 ? 0 : -1;
}

#ifdef PJVM_PAGED
static FILE *pjvm_file_handle;

static void pjvm_host_read(uint32_t file_offset, uint8_t *buf, uint16_t len, void *ctx) {
    FILE *f = (FILE *)ctx;
    fseek(f, (long)file_offset, SEEK_SET);
    fread(buf, 1, len, f);
}
#endif

void pjvm_platform_trap(uint8_t op, uint16_t pc) {
    if (op == 0xFE) {
        extern uint8_t pjvm_trace_enabled;
        extern uint32_t trace_idx;
        extern uint32_t trace_pc[];
        extern uint8_t trace_op[], trace_mi[], trace_sp[];
        extern uint16_t trace_stk0[];
        fprintf(stderr, "STEP LIMIT hit at pc=%u, mi=%u, sp=%u, fdepth=%d\n",
                (unsigned)pc, (unsigned)g_pjvm->cur_mi,
                (unsigned)g_pjvm->sp, (int)g_pjvm->fdepth);
        fprintf(stderr, "Last %d instructions:\n", trace_idx < 32 ? (int)trace_idx : 32);
        uint32_t start = trace_idx > 32 ? trace_idx - 32 : 0;
        for (uint32_t i = start; i < trace_idx; i++) {
            uint32_t ti = i % 32;
            fprintf(stderr, "  [%u] pc=%u mi=%u op=0x%02X sp=%u stk0=%u\n",
                    (unsigned)i, (unsigned)trace_pc[ti],
                    (unsigned)trace_mi[ti], (unsigned)trace_op[ti],
                    (unsigned)trace_sp[ti], (unsigned)trace_stk0[ti]);
        }
        exit(2);
    } else if (op == 0xFF) {
        fprintf(stderr, "Unknown native method at bytecode offset %u\n",
                (unsigned)pc);
    } else {
        fprintf(stderr, "Unimplemented opcode 0x%02X at bytecode offset %u\n",
                op, (unsigned)pc);
    }
    exit(1);
}

static void preload_raw(const char *spec) {
    /* Parse "ADDR:FILE" — load file into raw_mem at ADDR, store length at ADDR-2 */
    char *colon = strchr(spec, ':');
    if (!colon) {
        fprintf(stderr, "Bad --preload format, expected ADDR:FILE\n");
        exit(1);
    }
    unsigned long addr = strtoul(spec, NULL, 0);
    const char *path = colon + 1;
    FILE *f = fopen(path, "rb");
    if (!f) { perror(path); exit(1); }
    fseek(f, 0, SEEK_END);
    size_t len = (size_t)ftell(f);
    fseek(f, 0, SEEK_SET);
    if (addr + len > RAW_MEM_SIZE) {
        fprintf(stderr, "Preload overflow: 0x%lx + %zu > %u\n",
                addr, len, (unsigned)RAW_MEM_SIZE);
        exit(1);
    }
    if (fread(raw_mem + addr, 1, len, f) != len) {
        perror("fread preload");
        exit(1);
    }
    fclose(f);
    /* Store length at addr-4 as 32-bit LE (supports source > 64K) */
    if (addr >= 4) {
        raw_mem[addr - 4] = (uint8_t)(len & 0xFF);
        raw_mem[addr - 3] = (uint8_t)((len >> 8) & 0xFF);
        raw_mem[addr - 2] = (uint8_t)((len >> 16) & 0xFF);
        raw_mem[addr - 1] = (uint8_t)((len >> 24) & 0xFF);
    }
    fprintf(stderr, "PRELOAD | 0x%04lx: %zu bytes from %s\n", addr, len, path);
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

    if (prog_data[0] != 0x85 || (prog_data[1] != 0x4A && prog_data[1] != 0x4B && prog_data[1] != 0x4C)) {
        fprintf(stderr, "Bad .pjvm magic\n");
        exit(1);
    }

    pjvm_prog = prog_data;
    pjvm_prog_size = (uint32_t)prog_size;
    pjvm_parse(prog_data);
}

int main(int argc, char **argv) {
    PJVMCtx ctx = {0};
    const char *save_spec = NULL;
    int prog_argi = argc;

    if (argc < 2) {
        fprintf(stderr, "Usage: picojvm <program.pjvm> [vm options] [-- program args]\n");
        return 1;
    }

    for (int i = 2; i < argc; i++) {
        if (strcmp(argv[i], "--") == 0) {
            prog_argi = i + 1;
            break;
        }
    }

    /* Pre-parse --preload and --save-raw before loading .pjvm */
    for (int i = 2; i < prog_argi; i++) {
        if (strcmp(argv[i], "--preload") == 0 && i + 1 < prog_argi) {
            preload_raw(argv[++i]);
        } else if (strncmp(argv[i], "--preload=", 10) == 0) {
            preload_raw(argv[i] + 10);
        } else if (strcmp(argv[i], "--save-raw") == 0 && i + 1 < prog_argi) {
            save_spec = argv[++i];
        } else if (strncmp(argv[i], "--save-raw=", 11) == 0) {
            save_spec = argv[i] + 11;
        } else if (strncmp(argv[i], "--step-limit=", 13) == 0) {
            extern uint32_t pjvm_step_limit;
            pjvm_step_limit = (uint32_t)strtoul(argv[i] + 13, NULL, 0);
        } else if (strcmp(argv[i], "--trace") == 0) {
            extern uint8_t pjvm_trace_enabled;
            pjvm_trace_enabled = 1;
        } else if (strncmp(argv[i], "--watch=", 8) == 0) {
            watch_addr = (uint16_t)strtoul(argv[i] + 8, NULL, 0);
        }
    }

    const char *dump_raw_path = NULL;
    for (int i = 2; i < prog_argi; i++) {
        if (strncmp(argv[i], "--dump-raw=", 11) == 0)
            dump_raw_path = argv[i] + 11;
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

    for (int i = 2; i < prog_argi; i++) {
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
    for (int i = 2; i < prog_argi; i++) {
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
    ctx.prog_argc = (uint8_t)(argc - prog_argi);
    ctx.prog_argv = (const char **)(argv + prog_argi);
    pjvm_run(&ctx);
    fflush(stdout);

    /* Save raw memory region after execution */
    if (save_spec) {
        char *colon = strchr(save_spec, ':');
        if (colon) {
            unsigned long addr = strtoul(save_spec, NULL, 0);
            const char *spath = colon + 1;
            /* Read length from addr-4 (32-bit LE) */
            uint32_t len = 0;
            if (addr >= 4)
                len = (uint32_t)raw_mem[addr - 4] |
                      ((uint32_t)raw_mem[addr - 3] << 8) |
                      ((uint32_t)raw_mem[addr - 2] << 16) |
                      ((uint32_t)raw_mem[addr - 1] << 24);
            if (len > 0 && addr + len <= RAW_MEM_SIZE) {
                FILE *sf = fopen(spath, "wb");
                if (sf) {
                    fwrite(raw_mem + addr, 1, len, sf);
                    fclose(sf);
                    fprintf(stderr, "SAVE | 0x%04lx: %u bytes to %s\n",
                            addr, (unsigned)len, spath);
                }
            }
        }
    }

    if (dump_raw_path) {
        FILE *df = fopen(dump_raw_path, "wb");
        if (df) {
            fwrite(raw_mem, 1, RAW_MEM_SIZE, df);
            fclose(df);
            fprintf(stderr, "DUMP | raw_mem (%u bytes) to %s\n",
                    (unsigned)RAW_MEM_SIZE, dump_raw_path);
        }
    }

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
