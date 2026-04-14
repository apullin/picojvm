/*
 * pjvm_gc.c -- GC trigger policy scaffolding for picoJVM.
 *
 * The actual collector is not implemented yet. This file centralizes trigger
 * policy so future GC work can plug in one collector while keeping the call
 * sites stable.
 */

#define PJVM_GC_IMPL 1
#include "pjvm.h"

#if PJVM_GC_ENABLED
static uint32_t pjvm_gc_heap_limit_value(const PJVMCtx *j) {
    return j->heap_limit ? (uint32_t)j->heap_limit : 65536u;
}

static uint16_t pjvm_gc_next_random(PJVMCtx *j) {
    uint16_t lfsr = j->gc_lfsr ? j->gc_lfsr : 0xACE1u;
    uint16_t bit = (uint16_t)(((lfsr >> 0) ^ (lfsr >> 2) ^ (lfsr >> 3) ^ (lfsr >> 5)) & 1u);
    lfsr = (uint16_t)((lfsr >> 1) | (bit << 15));
    j->gc_lfsr = lfsr;
    return lfsr;
}

static uint8_t pjvm_gc_above_watermark(PJVMCtx *j, uint16_t alloc_size) {
    uint32_t limit = pjvm_gc_heap_limit_value(j);
    uint32_t base = j->heap_base;
    uint32_t cap = limit > base ? limit - base : 0;
    uint32_t used = (uint32_t)j->heap_used + alloc_size;
    return cap != 0 && used * 100u >= cap * PJVM_GC_WATERMARK_PCT;
}
#endif

void pjvm_gc_init(PJVMCtx *j) {
#if PJVM_GC_ENABLED
    j->gc_lfsr = (uint16_t)(j->heap_base ? j->heap_base : 0xACE1u);
    j->gc_count = 0;
#else
    (void)j;
#endif
}

uint8_t pjvm_gc_collect(PJVMCtx *j, uint8_t reason) {
    (void)reason;
#if PJVM_GC_ENABLED
    j->gc_count++;
    return 0;
#else
    (void)j;
    return 0;
#endif
}

void pjvm_gc_maybe(PJVMCtx *j, uint8_t reason, uint16_t alloc_size) {
#if !PJVM_GC_ENABLED
    (void)j;
    (void)reason;
    (void)alloc_size;
#else
    uint8_t should_collect = 0;

    if ((reason & PJVM_GC_TRIG_ALLOC_FAIL) && (PJVM_GC_TRIGGERS & PJVM_GC_TRIG_ALLOC_FAIL))
        should_collect = 1;

    if (!should_collect &&
        (reason & PJVM_GC_TRIG_WATERMARK) &&
        (PJVM_GC_TRIGGERS & PJVM_GC_TRIG_WATERMARK) &&
        pjvm_gc_above_watermark(j, alloc_size))
        should_collect = 1;

    if (!should_collect &&
        (reason & PJVM_GC_TRIG_RETURN) &&
        (PJVM_GC_TRIGGERS & PJVM_GC_TRIG_RETURN))
        should_collect = 1;

    if (!should_collect &&
        (reason & PJVM_GC_TRIG_RANDOM_ABOVE_WATERMARK) &&
        (PJVM_GC_TRIGGERS & PJVM_GC_TRIG_RANDOM_ABOVE_WATERMARK) &&
        pjvm_gc_above_watermark(j, alloc_size) &&
        (pjvm_gc_next_random(j) & PJVM_GC_RANDOM_MASK) == 0)
        should_collect = 1;

    if (should_collect) (void)pjvm_gc_collect(j, reason);
#endif
}
