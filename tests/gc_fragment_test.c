#include "../src/pjvm.h"

#include <stdio.h>
#include <string.h>

static uint8_t heap_mem[256];
static int failed;

uint8_t *pjvm_prog;
uint32_t pjvm_prog_size;
uint8_t n_methods, main_mi, n_classes;
uint32_t bytecodes_size;
uint32_t bc_off, cpr_off, ic_off, sc_off, et_off, cd_off;
PJVMCtx *g_pjvm;

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
void pjvm_platform_trap(uint8_t op, uint16_t pc) { (void)op; (void)pc; }
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

static void expect_u16(const char *label, uint16_t got, uint16_t want) {
    printf("%s=%u\n", label, (unsigned)got);
    if (got != want) {
        fprintf(stderr, "FAIL: %s expected %u got %u\n",
                label, (unsigned)want, (unsigned)got);
        failed = 1;
    }
}

static void expect_true(const char *label, uint8_t cond) {
    printf("%s=%u\n", label, (unsigned)cond);
    if (!cond) {
        fprintf(stderr, "FAIL: %s expected true\n", label);
        failed = 1;
    }
}

int main(void) {
    PJVMCtx j;
    uint16_t root;
    uint16_t live;
    uint16_t dead_a;
    uint16_t dead_b;
    uint16_t dead_c;
    uint16_t fresh;
    uint16_t used_before;

    memset(&j, 0, sizeof(j));
    memset(heap_mem, 0, sizeof(heap_mem));
    g_pjvm = &j;

    pjvm_heap_init(&j, 1, 129);

    root = pjvm_heap_alloc(&j, (uint16_t)(PJVM_OBJ_HEADER + 4), PJVM_HEAP_KIND_REF_ARRAY);
    live = pjvm_heap_alloc(&j, (uint16_t)(PJVM_OBJ_HEADER + 4), PJVM_HEAP_KIND_BYTE_ARRAY);
    dead_a = pjvm_heap_alloc(&j, (uint16_t)(PJVM_OBJ_HEADER + 4), PJVM_HEAP_KIND_REF_ARRAY);
    dead_b = pjvm_heap_alloc(&j, (uint16_t)(PJVM_OBJ_HEADER + 4), PJVM_HEAP_KIND_REF_ARRAY);
    dead_c = pjvm_heap_alloc(&j, (uint16_t)(PJVM_OBJ_HEADER + 20), PJVM_HEAP_KIND_STRING);

    w16(root, 1);
    w16(live, 4);
    w16(dead_a, 1);
    w16(dead_b, 1);
    w16(dead_c, 20);

    w8((uint16_t)(live + PJVM_OBJ_HEADER), 0x5A);
    w16((uint16_t)(root + PJVM_OBJ_HEADER), live);
    w16((uint16_t)(root + PJVM_OBJ_HEADER + 2), 0);

    w16((uint16_t)(dead_a + PJVM_OBJ_HEADER), dead_b);
    w16((uint16_t)(dead_a + PJVM_OBJ_HEADER + 2), 0);
    w16((uint16_t)(dead_b + PJVM_OBJ_HEADER), dead_a);
    w16((uint16_t)(dead_b + PJVM_OBJ_HEADER + 2), 0);

    j.loc_lo[0] = root;
    j.loc_hi[0] = 0;
    j.lt = 1;

    used_before = j.heap_used;
    (void)pjvm_gc_collect(&j, PJVM_GC_TRIG_ALLOC_FAIL);

    expect_u16("gc_count", j.gc_count, 1);
    expect_true("heap_used_dropped", (uint8_t)(j.heap_used < used_before));
    expect_u16("root_live", r16((uint16_t)(root + PJVM_OBJ_HEADER)), live);
    expect_u16("live_marker", r8((uint16_t)(live + PJVM_OBJ_HEADER)), 0x5A);

    fresh = pjvm_heap_alloc(&j, (uint16_t)(PJVM_OBJ_HEADER + 44), PJVM_HEAP_KIND_STRING);
    expect_true("fresh_allocated", (uint8_t)(fresh != 0));
    expect_u16("fresh_reuses_coalesced_dead", fresh, dead_a);

    return failed ? 1 : 0;
}
