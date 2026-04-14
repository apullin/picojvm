#include "../src/pjvm.h"

#include <stdio.h>
#include <string.h>

static uint8_t heap_mem[256];
static int failed;

static const uint8_t legacy_prog[] = {
    0x85, 0x4C, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x01,
    0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00,
    0xFF, 0x02, 0x00, 0xFF,
    0x00, 0x00,
};

static const uint8_t exact_prog[] = {
    0x85, 0x4C, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x01,
    0x00, PJVM_RF_REF_BITMAPS, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00,
    0xFF, 0x02, 0x00, 0xFF,
    0x01,
    0x00, 0x00,
};

uint16_t heap_alloc(PJVMCtx *j, uint16_t size, uint8_t kind) {
    return pjvm_heap_alloc(j, size, kind);
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

void pjvm_platform_putchar(uint8_t ch) { (void)ch; }
uint8_t pjvm_platform_peek8(uint32_t a) { (void)a; return 0; }
void pjvm_platform_poke8(uint32_t a, uint8_t v) { (void)a; (void)v; }
void pjvm_platform_trap(uint8_t op, uint16_t pc) {
    fprintf(stderr, "FAIL: trap op=0x%02X pc=%u\n", (unsigned)op, (unsigned)pc);
    failed = 1;
}
void pjvm_platform_out(uint16_t port, uint16_t val) { (void)port; (void)val; }
int32_t pjvm_platform_file_open(const uint8_t *name, uint8_t nameLen, uint8_t mode) {
    (void)name; (void)nameLen; (void)mode; return -1;
}
int32_t pjvm_platform_file_read_byte(void) { return -1; }
void pjvm_platform_file_write_byte(uint8_t b) { (void)b; }
void pjvm_platform_file_close(uint8_t mode) { (void)mode; }
int32_t pjvm_platform_file_delete(const uint8_t *name, uint8_t nameLen) {
    (void)name; (void)nameLen; return -1;
}

static void expect_true(const char *label, uint8_t cond) {
    printf("%s=%u\n", label, (unsigned)cond);
    if (!cond) {
        fprintf(stderr, "FAIL: %s expected true\n", label);
        failed = 1;
    }
}

static void expect_u16(const char *label, uint16_t got, uint16_t want) {
    printf("%s=%u\n", label, (unsigned)got);
    if (got != want) {
        fprintf(stderr, "FAIL: %s expected %u got %u\n",
                label, (unsigned)want, (unsigned)got);
        failed = 1;
    }
}

static void expect_addr_mode(const char *label, uint16_t got, uint16_t dead,
                             uint8_t expect_dead_reclaimed) {
    printf("%s=%u\n", label, (unsigned)got);
    if (expect_dead_reclaimed) {
        if (got != dead) {
            fprintf(stderr, "FAIL: %s expected reclaimed addr %u got %u\n",
                    label, (unsigned)dead, (unsigned)got);
            failed = 1;
        }
    } else if (got == dead) {
        fprintf(stderr, "FAIL: %s unexpectedly reclaimed addr %u\n",
                label, (unsigned)dead);
        failed = 1;
    }
}

static void run_case(const char *label, const uint8_t *prog, uint32_t prog_size_bytes,
                     uint8_t expect_dead_reclaimed) {
    PJVMCtx j;
    uint16_t root;
    uint16_t live;
    uint16_t dead;
    uint16_t fresh;

    memset(&j, 0, sizeof(j));
    memset(heap_mem, 0, sizeof(heap_mem));
    g_pjvm = &j;
    pjvm_prog = (uint8_t *)prog;
    pjvm_prog_size = prog_size_bytes;
    pjvm_parse((uint8_t *)prog);

    pjvm_heap_init(&j, 1, 129);

    root = pjvm_heap_alloc(&j, (uint16_t)(PJVM_OBJ_HEADER + 8), PJVM_HEAP_KIND_OBJECT);
    live = pjvm_heap_alloc(&j, (uint16_t)(PJVM_OBJ_HEADER + 4), PJVM_HEAP_KIND_STRING);
    dead = pjvm_heap_alloc(&j, (uint16_t)(PJVM_OBJ_HEADER + 4), PJVM_HEAP_KIND_STRING);

    w16(root, 0);
    w16((uint16_t)(root + 2), 0);
    w16(live, 4);
    w16((uint16_t)(live + 2), 0);
    w16(dead, 4);
    w16((uint16_t)(dead + 2), 0);

    w16((uint16_t)(root + PJVM_OBJ_HEADER), live);
    w16((uint16_t)(root + PJVM_OBJ_HEADER + 2), 0);
    w16((uint16_t)(root + PJVM_OBJ_HEADER + 4), dead);
    w16((uint16_t)(root + PJVM_OBJ_HEADER + 6), 0);

    j.loc_lo[0] = root;
    j.loc_hi[0] = 0;
    j.lt = 1;

    (void)pjvm_gc_collect(&j, PJVM_GC_TRIG_ALLOC_FAIL);
    fresh = pjvm_heap_alloc(&j, (uint16_t)(PJVM_OBJ_HEADER + 4), PJVM_HEAP_KIND_STRING);

    expect_addr_mode(label, fresh, dead, expect_dead_reclaimed);
    expect_u16("live_still_len", r16(live), 4);
    expect_true("root_kept", (uint8_t)(r16((uint16_t)(root + PJVM_OBJ_HEADER)) == live));
}

int main(void) {
    run_case("legacy_fresh_addr", legacy_prog, sizeof(legacy_prog), 0);
    run_case("exact_fresh_addr", exact_prog, sizeof(exact_prog), 1);
    return failed ? 1 : 0;
}
