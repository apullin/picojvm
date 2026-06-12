/*
 * pjvm_heap.c -- shared heap backends for picoJVM.
 *
 * The core still asks the platform for heap_alloc(), but the allocator
 * strategy itself lives here so targets can switch between simple bump
 * allocation and a small coalescing free-list backend without rewriting the
 * platform shims.
 */

#include "pjvm.h"

static uint32_t pjvm_heap_limit_value(void) {
    return g_pjvm->heap_limit ? (uint32_t)g_pjvm->heap_limit : 65536u;
}

static void pjvm_heap_zero(uint16_t a, uint16_t size) {
    for (uint16_t i = 0; i < size; i++) w8((uint16_t)(a + i), 0);
}

#if PJVM_GC_ALLOC_BITMAP
/* Exact allocation bitmap: one bit per 2-byte-aligned payload start. */
uint8_t pjvm_gc_alloc_bm[PJVM_GC_BITMAP_SPAN >> 4];

void pjvm_gc_bm_set(uint16_t payload) {
    uint16_t bit = (uint16_t)((uint16_t)(payload - g_pjvm->heap_base) >> 1);
    pjvm_gc_alloc_bm[bit >> 3] |= (uint8_t)(1u << (bit & 7u));
}

void pjvm_gc_bm_clear(uint16_t payload) {
    uint16_t bit = (uint16_t)((uint16_t)(payload - g_pjvm->heap_base) >> 1);
    pjvm_gc_alloc_bm[bit >> 3] &= (uint8_t)~(1u << (bit & 7u));
}
#endif

#if PJVM_HEAP_MODE == PJVM_HEAP_FREELIST
static uint16_t pjvm_heap_align2(uint16_t v) {
    return (uint16_t)((v + 1u) & ~1u);
}

static uint16_t pjvm_blk_size(uint16_t blk) {
    return (uint16_t)(r16(blk) & (uint16_t)~PJVM_HEAP_ALLOC_FLAG);
}

static uint8_t pjvm_blk_is_allocated(uint16_t blk) {
    return (uint8_t)((r16(blk) & PJVM_HEAP_ALLOC_FLAG) != 0);
}

static void pjvm_blk_set_size(uint16_t blk, uint16_t size, uint8_t allocated) {
    w16(blk, (uint16_t)(size | (allocated ? PJVM_HEAP_ALLOC_FLAG : 0u)));
}

static void pjvm_blk_set_meta(uint16_t blk, uint16_t meta) {
    w16((uint16_t)(blk + 2), meta);
}

static uint16_t pjvm_blk_next(uint16_t blk) {
    return r16((uint16_t)(blk + 2));
}

static void pjvm_blk_set_next(uint16_t blk, uint16_t next) {
    w16((uint16_t)(blk + 2), next);
}

static void pjvm_heap_insert_free(uint16_t blk) {
    uint16_t prev = 0;
    uint16_t cur = g_pjvm->heap_free_head;

    while (cur != 0 && cur < blk) {
        prev = cur;
        cur = pjvm_blk_next(cur);
    }

    pjvm_blk_set_next(blk, cur);
    if (prev != 0) pjvm_blk_set_next(prev, blk);
    else g_pjvm->heap_free_head = blk;

    if (cur != 0 && (uint16_t)(blk + pjvm_blk_size(blk)) == cur) {
        pjvm_blk_set_size(blk, (uint16_t)(pjvm_blk_size(blk) + pjvm_blk_size(cur)), 0);
        pjvm_blk_set_next(blk, pjvm_blk_next(cur));
    }

    if (prev != 0 && (uint16_t)(prev + pjvm_blk_size(prev)) == blk) {
        pjvm_blk_set_size(prev, (uint16_t)(pjvm_blk_size(prev) + pjvm_blk_size(blk)), 0);
        pjvm_blk_set_next(prev, pjvm_blk_next(blk));
    }
}
#endif

void pjvm_heap_init(PJVMCtx *j, uint16_t start, uint16_t limit) {
    g_pjvm = j;
    g_pjvm->heap_limit = limit;
    g_pjvm->heap_free_head = 0;
    g_pjvm->heap_used = 0;

#if PJVM_HEAP_MODE == PJVM_HEAP_FREELIST
    {
        uint16_t base = pjvm_heap_align2(start);
        uint32_t end = pjvm_heap_limit_value();
        uint32_t span = end > base ? end - base : 0;
        uint16_t total = (uint16_t)(span & ~1u);

        g_pjvm->heap_ptr = base;
        g_pjvm->heap_base = base;
        pjvm_gc_init();

#if PJVM_GC_ALLOC_BITMAP
        if (span > PJVM_GC_BITMAP_SPAN) {
            pjvm_platform_trap(PJVM_TRAP_CAPACITY, (uint16_t)(span >> 8));
            return;
        }
        for (uint16_t i = 0; i < (uint16_t)(sizeof pjvm_gc_alloc_bm); i++)
            pjvm_gc_alloc_bm[i] = 0;
#endif

        if (total >= PJVM_HEAP_FREE_HDR) {
            g_pjvm->heap_free_head = base;
            pjvm_blk_set_size(base, total, 0);
            pjvm_blk_set_next(base, 0);
        }
    }
#else
    g_pjvm->heap_ptr = start;
    g_pjvm->heap_base = start;
    pjvm_gc_init();
#endif
}

#if PJVM_HEAP_MODE == PJVM_HEAP_BUMP
static uint16_t pjvm_heap_alloc_bump(uint16_t size, uint8_t kind) {
    (void)kind;
#if PJVM_GC_ENABLED
    pjvm_gc_maybe(
                  (uint8_t)(PJVM_GC_TRIG_WATERMARK | PJVM_GC_TRIG_RANDOM_ABOVE_WATERMARK),
                  size);
#endif

    {
        uint32_t end = (uint32_t)g_pjvm->heap_ptr + size;
        if (end > pjvm_heap_limit_value()) {
#if PJVM_GC_ENABLED
            pjvm_gc_maybe(PJVM_GC_TRIG_ALLOC_FAIL, size);
            end = (uint32_t)g_pjvm->heap_ptr + size;
            if (end > pjvm_heap_limit_value()) return 0;
#else
            return 0;
#endif
        }

        {
            uint16_t a = g_pjvm->heap_ptr;
            g_pjvm->heap_ptr = (uint16_t)end;
            g_pjvm->heap_used = (uint16_t)(g_pjvm->heap_ptr - g_pjvm->heap_base);
            pjvm_heap_zero(a, size);
            return a;
        }
    }
}

static void pjvm_heap_free_bump(uint16_t a) {
    (void)a;
}
#endif

#if PJVM_HEAP_MODE == PJVM_HEAP_FREELIST
static uint16_t pjvm_heap_alloc_freelist(uint16_t size, uint8_t kind) {
    uint16_t want = pjvm_heap_align2((uint16_t)(size + PJVM_HEAP_ALLOC_HDR));

#if PJVM_GC_ENABLED
    uint8_t tried_gc = 0;
    pjvm_gc_maybe(
                  (uint8_t)(PJVM_GC_TRIG_WATERMARK | PJVM_GC_TRIG_RANDOM_ABOVE_WATERMARK),
                  size);
#endif

    for (;;) {
        uint16_t prev = 0;
        uint16_t cur = g_pjvm->heap_free_head;

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
                    else g_pjvm->heap_free_head = new_free;
                    pjvm_blk_set_size(cur, want, 1);
                } else {
                    if (prev != 0) pjvm_blk_set_next(prev, next);
                    else g_pjvm->heap_free_head = next;
                    pjvm_blk_set_size(cur, blk_size, 1);
                    want = blk_size;
                }

                pjvm_blk_set_meta(cur, (uint16_t)(kind & PJVM_HEAP_META_KIND_MASK));
                g_pjvm->heap_used = (uint16_t)(g_pjvm->heap_used + want);
                {
                    uint16_t payload = (uint16_t)(cur + PJVM_HEAP_ALLOC_HDR);
#if PJVM_GC_ALLOC_BITMAP
                    pjvm_gc_bm_set(payload);
#endif
                    pjvm_heap_zero(payload, (uint16_t)(want - PJVM_HEAP_ALLOC_HDR));
                    return payload;
                }
            }
            prev = cur;
            cur = pjvm_blk_next(cur);
        }

#if PJVM_GC_ENABLED
        if (!tried_gc) {
            tried_gc = 1;
            pjvm_gc_maybe(PJVM_GC_TRIG_ALLOC_FAIL, size);
            continue;
        }
#endif
        return 0;
    }
}

static void pjvm_heap_free_freelist(uint16_t a) {
    uint16_t blk;
    uint16_t size;

    if (a == 0 || a < (uint16_t)(g_pjvm->heap_base + PJVM_HEAP_ALLOC_HDR)) return;

    blk = (uint16_t)(a - PJVM_HEAP_ALLOC_HDR);
    if (!pjvm_blk_is_allocated(blk)) return;

    size = pjvm_blk_size(blk);
    if (size < PJVM_HEAP_ALLOC_HDR) return;

    pjvm_blk_set_size(blk, size, 0);
    pjvm_blk_set_next(blk, 0);
    if (g_pjvm->heap_used >= size) g_pjvm->heap_used = (uint16_t)(g_pjvm->heap_used - size);
    else g_pjvm->heap_used = 0;
#if PJVM_GC_ALLOC_BITMAP
    pjvm_gc_bm_clear(a);
#endif
    pjvm_heap_insert_free(blk);
}
#endif

uint16_t pjvm_heap_alloc(uint16_t size, uint8_t kind) {
#if PJVM_HEAP_MODE == PJVM_HEAP_FREELIST
    return pjvm_heap_alloc_freelist(size, kind);
#else
    return pjvm_heap_alloc_bump(size, kind);
#endif
}

void pjvm_heap_free(uint16_t a) {
#if PJVM_HEAP_MODE == PJVM_HEAP_FREELIST
    pjvm_heap_free_freelist(a);
#else
    pjvm_heap_free_bump(a);
#endif
}
