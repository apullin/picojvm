/*
 * pjvm_heap.c -- shared heap backend for picoJVM.
 *
 * The core still asks the platform for heap_alloc(), but the allocation
 * policy itself lives here so targets share the same bounds/zeroing logic.
 * This initial backend keeps the existing bump allocator behavior.
 */

#include "pjvm.h"

static uint32_t pjvm_heap_limit_value(const PJVMCtx *j) {
    return j->heap_limit ? (uint32_t)j->heap_limit : 65536u;
}

static void pjvm_heap_zero(uint16_t a, uint16_t size) {
    for (uint16_t i = 0; i < size; i++) w8((uint16_t)(a + i), 0);
}

void pjvm_heap_init(PJVMCtx *j, uint16_t start, uint16_t limit) {
    j->heap_ptr = start;
    j->heap_base = start;
    j->heap_limit = limit;
    j->heap_used = 0;
}

uint16_t pjvm_heap_alloc(PJVMCtx *j, uint16_t size) {
    uint32_t end = (uint32_t)j->heap_ptr + size;
    if (end > pjvm_heap_limit_value(j)) return 0;

    uint16_t a = j->heap_ptr;
    j->heap_ptr = (uint16_t)end;
    j->heap_used = (uint16_t)(j->heap_ptr - j->heap_base);
    pjvm_heap_zero(a, size);
    return a;
}

void pjvm_heap_free(PJVMCtx *j, uint16_t a) {
    (void)j;
    (void)a;
}
