/*
 * pjvm_heap.c -- shared heap backends for picoJVM.
 *
 * The core still asks the platform for heap_alloc(), but the allocator
 * strategy itself lives here so targets can switch between simple bump
 * allocation and a small coalescing free-list backend without rewriting the
 * platform shims.
 */

#include "pjvm.h"

#define PJVM_HEAP_ALLOC_FLAG  0x0001u
#define PJVM_HEAP_ALLOC_HDR   2u
#define PJVM_HEAP_FREE_HDR    4u
#define PJVM_HEAP_MIN_SPLIT   6u

static uint32_t pjvm_heap_limit_value(const PJVMCtx *j) {
    return j->heap_limit ? (uint32_t)j->heap_limit : 65536u;
}

static void pjvm_heap_zero(uint16_t a, uint16_t size) {
    for (uint16_t i = 0; i < size; i++) w8((uint16_t)(a + i), 0);
}

#if PJVM_HEAP_MODE == PJVM_HEAP_FREELIST
static uint16_t pjvm_heap_align2(uint16_t v) {
    return (uint16_t)((v + 1u) & ~1u);
}

static uint16_t pjvm_blk_size(uint16_t blk) {
    return (uint16_t)(r16(blk) & (uint16_t)~PJVM_HEAP_ALLOC_FLAG);
}

static void pjvm_blk_set_size(uint16_t blk, uint16_t size, uint8_t allocated) {
    w16(blk, (uint16_t)(size | (allocated ? PJVM_HEAP_ALLOC_FLAG : 0u)));
}

static uint16_t pjvm_blk_next(uint16_t blk) {
    return r16((uint16_t)(blk + 2));
}

static void pjvm_blk_set_next(uint16_t blk, uint16_t next) {
    w16((uint16_t)(blk + 2), next);
}
#endif

void pjvm_heap_init(PJVMCtx *j, uint16_t start, uint16_t limit) {
    j->heap_ptr = start;
    j->heap_base = start;
    j->heap_limit = limit;
    j->heap_free_head = 0;
    j->heap_used = 0;
    pjvm_gc_init(j);

#if PJVM_HEAP_MODE == PJVM_HEAP_FREELIST
    uint16_t base = pjvm_heap_align2(start);
    uint32_t end = pjvm_heap_limit_value(j);
    uint32_t span = end > base ? end - base : 0;
    uint16_t total = (uint16_t)(span & ~1u);
    if (total >= PJVM_HEAP_FREE_HDR) {
        j->heap_free_head = base;
        pjvm_blk_set_size(base, total, 0);
        pjvm_blk_set_next(base, 0);
    }
#endif
}

#if PJVM_HEAP_MODE == PJVM_HEAP_BUMP
static uint16_t pjvm_heap_alloc_bump(PJVMCtx *j, uint16_t size) {
#if PJVM_GC_ENABLED
    pjvm_gc_maybe(j,
                  (uint8_t)(PJVM_GC_TRIG_WATERMARK | PJVM_GC_TRIG_RANDOM_ABOVE_WATERMARK),
                  size);

    uint32_t end = (uint32_t)j->heap_ptr + size;
    if (end > pjvm_heap_limit_value(j)) {
        pjvm_gc_maybe(j, PJVM_GC_TRIG_ALLOC_FAIL, size);
        end = (uint32_t)j->heap_ptr + size;
        if (end > pjvm_heap_limit_value(j)) return 0;
    }
#else
    uint32_t end = (uint32_t)j->heap_ptr + size;
    if (end > pjvm_heap_limit_value(j)) return 0;
#endif

    uint16_t a = j->heap_ptr;
    j->heap_ptr = (uint16_t)end;
    j->heap_used = (uint16_t)(j->heap_ptr - j->heap_base);
    pjvm_heap_zero(a, size);
    return a;
}

static void pjvm_heap_free_bump(PJVMCtx *j, uint16_t a) {
    (void)j;
    (void)a;
}
#endif

#if PJVM_HEAP_MODE == PJVM_HEAP_FREELIST
static void pjvm_heap_insert_free(PJVMCtx *j, uint16_t blk) {
    uint16_t prev = 0;
    uint16_t cur = j->heap_free_head;

    while (cur != 0 && cur < blk) {
        prev = cur;
        cur = pjvm_blk_next(cur);
    }

    pjvm_blk_set_next(blk, cur);
    if (prev != 0) pjvm_blk_set_next(prev, blk);
    else j->heap_free_head = blk;

    if (cur != 0 && (uint16_t)(blk + pjvm_blk_size(blk)) == cur) {
        pjvm_blk_set_size(blk, (uint16_t)(pjvm_blk_size(blk) + pjvm_blk_size(cur)), 0);
        pjvm_blk_set_next(blk, pjvm_blk_next(cur));
    }

    if (prev != 0 && (uint16_t)(prev + pjvm_blk_size(prev)) == blk) {
        pjvm_blk_set_size(prev, (uint16_t)(pjvm_blk_size(prev) + pjvm_blk_size(blk)), 0);
        pjvm_blk_set_next(prev, pjvm_blk_next(blk));
    }
}

static uint16_t pjvm_heap_alloc_freelist(PJVMCtx *j, uint16_t size) {
    uint16_t want = pjvm_heap_align2((uint16_t)(size + PJVM_HEAP_ALLOC_HDR));
    uint16_t prev = 0;
    uint16_t cur = j->heap_free_head;

#if PJVM_GC_ENABLED
    pjvm_gc_maybe(j,
                  (uint8_t)(PJVM_GC_TRIG_WATERMARK | PJVM_GC_TRIG_RANDOM_ABOVE_WATERMARK),
                  size);
#endif

#if PJVM_GC_ENABLED
retry:
#endif
    while (cur != 0) {
        uint16_t blk_size = pjvm_blk_size(cur);
        if (blk_size >= want) {
            uint16_t next = pjvm_blk_next(cur);
            uint16_t remain = (uint16_t)(blk_size - want);

            if (remain >= PJVM_HEAP_MIN_SPLIT) {
                uint16_t new_free = (uint16_t)(cur + want);
                pjvm_blk_set_size(new_free, remain, 0);
                pjvm_blk_set_next(new_free, next);
                if (prev != 0) pjvm_blk_set_next(prev, new_free);
                else j->heap_free_head = new_free;
                pjvm_blk_set_size(cur, want, 1);
            } else {
                if (prev != 0) pjvm_blk_set_next(prev, next);
                else j->heap_free_head = next;
                pjvm_blk_set_size(cur, blk_size, 1);
                want = blk_size;
            }

            j->heap_used = (uint16_t)(j->heap_used + want);
            uint16_t payload = (uint16_t)(cur + PJVM_HEAP_ALLOC_HDR);
            pjvm_heap_zero(payload, (uint16_t)(want - PJVM_HEAP_ALLOC_HDR));
            return payload;
        }
        prev = cur;
        cur = pjvm_blk_next(cur);
    }

#if PJVM_GC_ENABLED
    pjvm_gc_maybe(j, PJVM_GC_TRIG_ALLOC_FAIL, size);
    prev = 0;
    cur = j->heap_free_head;
    goto retry;
#endif
    return 0;
}

static void pjvm_heap_free_freelist(PJVMCtx *j, uint16_t a) {
    if (a == 0 || a < (uint16_t)(j->heap_base + PJVM_HEAP_ALLOC_HDR)) return;

    uint16_t blk = (uint16_t)(a - PJVM_HEAP_ALLOC_HDR);
    uint16_t size = pjvm_blk_size(blk);
    if (size < PJVM_HEAP_ALLOC_HDR) return;

    pjvm_blk_set_size(blk, size, 0);
    j->heap_used = (uint16_t)(j->heap_used - size);
    pjvm_heap_insert_free(j, blk);
}
#endif

uint16_t pjvm_heap_alloc(PJVMCtx *j, uint16_t size) {
#if PJVM_HEAP_MODE == PJVM_HEAP_FREELIST
    return pjvm_heap_alloc_freelist(j, size);
#else
    return pjvm_heap_alloc_bump(j, size);
#endif
}

void pjvm_heap_free(PJVMCtx *j, uint16_t a) {
#if PJVM_HEAP_MODE == PJVM_HEAP_FREELIST
    pjvm_heap_free_freelist(j, a);
#else
    pjvm_heap_free_bump(j, a);
#endif
}
