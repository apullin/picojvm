/*
 * pjvm_gc.c -- GC trigger policy and optional mark/sweep collector.
 *
 * Roots are exact. Heap scanning is exact for ref arrays and for object
 * instances when the optional per-class ref bitmaps are present in the .pjvm
 * image. Older binaries without bitmap metadata fall back to conservative
 * object-slot scanning so GC remains backward-compatible.
 */

#define PJVM_GC_IMPL 1
#include "pjvm.h"

#if PJVM_GC_ENABLED
#if PJVM_GC_TRIGGERS & PJVM_GC_TRIG_RANDOM_ABOVE_WATERMARK
static uint16_t pjvm_gc_next_random(PJVMCtx *j) {
    uint16_t lfsr = j->gc_lfsr ? j->gc_lfsr : 0xACE1u;
    uint16_t bit = (uint16_t)(((lfsr >> 0) ^ (lfsr >> 2) ^ (lfsr >> 3) ^ (lfsr >> 5)) & 1u);
    lfsr = (uint16_t)((lfsr >> 1) | (bit << 15));
    j->gc_lfsr = lfsr;
    return lfsr;
}
#endif

#if PJVM_GC_TRIGGERS & (PJVM_GC_TRIG_WATERMARK | PJVM_GC_TRIG_RANDOM_ABOVE_WATERMARK)
static uint32_t pjvm_gc_heap_limit_value(const PJVMCtx *j) {
    return j->heap_limit ? (uint32_t)j->heap_limit : 65536u;
}

static uint8_t pjvm_gc_above_watermark(PJVMCtx *j, uint16_t alloc_size) {
    uint32_t limit = pjvm_gc_heap_limit_value(j);
    uint32_t base = j->heap_base;
    uint32_t cap = limit > base ? limit - base : 0;
    uint32_t used = (uint32_t)j->heap_used + alloc_size;
    return cap != 0 && used * 100u >= cap * PJVM_GC_WATERMARK_PCT;
}
#endif
#endif

#if PJVM_HEAP_MODE == PJVM_HEAP_FREELIST
static uint16_t pjvm_gc_blk_size(uint16_t blk) {
    return (uint16_t)(r16(blk) & (uint16_t)~PJVM_HEAP_ALLOC_FLAG);
}

static uint8_t pjvm_gc_blk_is_allocated(uint16_t blk) {
    return (uint8_t)((r16(blk) & PJVM_HEAP_ALLOC_FLAG) != 0);
}

static void pjvm_gc_blk_set_size(uint16_t blk, uint16_t size, uint8_t allocated) {
    w16(blk, (uint16_t)(size | (allocated ? PJVM_HEAP_ALLOC_FLAG : 0u)));
}

static uint16_t pjvm_gc_blk_meta(uint16_t blk) {
    return r16((uint16_t)(blk + 2));
}

static void pjvm_gc_blk_set_meta(uint16_t blk, uint16_t meta) {
    w16((uint16_t)(blk + 2), meta);
}

static void pjvm_gc_blk_set_next(uint16_t blk, uint16_t next) {
    w16((uint16_t)(blk + 2), next);
}

static uint32_t pjvm_gc_heap_limit_full(const PJVMCtx *j) {
    return j->heap_limit ? (uint32_t)j->heap_limit : 65536u;
}

static uint8_t pjvm_gc_mark_ref(PJVMCtx *j, uint16_t lo, uint16_t hi) {
    uint32_t end;
    uint32_t blk32;

    if (hi != 0 || lo == 0) return 0;

    end = pjvm_gc_heap_limit_full(j);
    if (lo < (uint16_t)(j->heap_base + PJVM_HEAP_ALLOC_HDR) || (uint32_t)lo >= end)
        return 0;

    for (blk32 = j->heap_base; blk32 < end; ) {
        uint16_t blk = (uint16_t)blk32;
        uint16_t size = pjvm_gc_blk_size(blk);
        if (size < PJVM_HEAP_FREE_HDR || blk32 + size > end) return 0;
        if (pjvm_gc_blk_is_allocated(blk)) {
            uint16_t payload = (uint16_t)(blk + PJVM_HEAP_ALLOC_HDR);
            if (payload == lo) {
                uint16_t meta = pjvm_gc_blk_meta(blk);
                if ((meta & PJVM_HEAP_META_MARK) == 0) {
                    uint8_t kind = (uint8_t)(meta & PJVM_HEAP_META_KIND_MASK);
                    meta |= PJVM_HEAP_META_MARK;
                    if (kind == PJVM_HEAP_KIND_OBJECT || kind == PJVM_HEAP_KIND_REF_ARRAY)
                        meta |= PJVM_HEAP_META_PENDING;
                    pjvm_gc_blk_set_meta(blk, meta);
                    return 1;
                }
                return 0;
            }
        }
        blk32 += size;
    }
    return 0;
}

static void pjvm_gc_scan_words(PJVMCtx *j, uint16_t start, uint16_t size) {
    for (uint16_t off = 0; off + 3u < size; off = (uint16_t)(off + 4u)) {
        uint16_t lo = r16((uint16_t)(start + off));
        uint16_t hi = r16((uint16_t)(start + off + 2u));
        (void)pjvm_gc_mark_ref(j, lo, hi);
    }
}

static void pjvm_gc_scan_object(PJVMCtx *j, uint16_t payload, uint16_t payload_size) {
    uint8_t ci;
    uint8_t nf;
    uint16_t bitmap_off;

    if (payload_size <= PJVM_OBJ_HEADER) return;

    ci = (uint8_t)r16(payload);
    if (ci >= n_classes) {
        pjvm_gc_scan_words(j,
                           (uint16_t)(payload + PJVM_OBJ_HEADER),
                           (uint16_t)(payload_size - PJVM_OBJ_HEADER));
        return;
    }

    nf = cls_nf[ci];
    bitmap_off = cls_rbo[ci];
    if ((region_flags & PJVM_RF_REF_BITMAPS) == 0 || bitmap_off == 0) {
        pjvm_gc_scan_words(j,
                           (uint16_t)(payload + PJVM_OBJ_HEADER),
                           (uint16_t)(payload_size - PJVM_OBJ_HEADER));
        return;
    }

    for (uint8_t slot = 0; slot < nf; slot++) {
        uint8_t bits = pjvm_prog_read((uint32_t)bitmap_off + (uint32_t)(slot >> 3));
        if ((bits & (uint8_t)(1u << (slot & 7u))) != 0) {
            uint16_t addr = (uint16_t)(payload + PJVM_OBJ_HEADER + (uint16_t)slot * 4u);
            uint16_t lo = r16(addr);
            uint16_t hi = r16((uint16_t)(addr + 2u));
            (void)pjvm_gc_mark_ref(j, lo, hi);
        }
    }
}

static void pjvm_gc_scan_block(PJVMCtx *j, uint16_t blk) {
    uint16_t meta = pjvm_gc_blk_meta(blk);
    uint8_t kind = (uint8_t)(meta & PJVM_HEAP_META_KIND_MASK);
    uint16_t payload = (uint16_t)(blk + PJVM_HEAP_ALLOC_HDR);
    uint16_t payload_size = (uint16_t)(pjvm_gc_blk_size(blk) - PJVM_HEAP_ALLOC_HDR);

    pjvm_gc_blk_set_meta(blk, (uint16_t)(meta & (uint16_t)~PJVM_HEAP_META_PENDING));

    if (payload_size <= PJVM_OBJ_HEADER) return;

    if (kind == PJVM_HEAP_KIND_OBJECT) {
        pjvm_gc_scan_object(j, payload, payload_size);
    } else if (kind == PJVM_HEAP_KIND_REF_ARRAY) {
        pjvm_gc_scan_words(j,
                           (uint16_t)(payload + PJVM_OBJ_HEADER),
                           (uint16_t)(payload_size - PJVM_OBJ_HEADER));
    }
}

static void pjvm_gc_mark_roots(PJVMCtx *j) {
    for (uint16_t i = 0; i < j->sp; i++)
        (void)pjvm_gc_mark_ref(j, j->stk_lo[i], j->stk_hi[i]);

    for (uint16_t i = 0; i < j->lt; i++)
        (void)pjvm_gc_mark_ref(j, j->loc_lo[i], j->loc_hi[i]);

    for (uint16_t i = 0; i < PJVM_STATIC_CAP; i++)
        (void)pjvm_gc_mark_ref(j, j->sf_lo[i], j->sf_hi[i]);
}

static void pjvm_gc_trace(PJVMCtx *j) {
    uint32_t end = pjvm_gc_heap_limit_full(j);
    uint8_t progress;

    do {
        progress = 0;
        for (uint32_t blk32 = j->heap_base; blk32 < end; ) {
            uint16_t blk = (uint16_t)blk32;
            uint16_t size = pjvm_gc_blk_size(blk);
            if (size < PJVM_HEAP_FREE_HDR || blk32 + size > end) return;
            if (pjvm_gc_blk_is_allocated(blk)) {
                uint16_t meta = pjvm_gc_blk_meta(blk);
                if ((meta & (PJVM_HEAP_META_MARK | PJVM_HEAP_META_PENDING)) ==
                    (PJVM_HEAP_META_MARK | PJVM_HEAP_META_PENDING)) {
                    pjvm_gc_scan_block(j, blk);
                    progress = 1;
                }
            }
            blk32 += size;
        }
    } while (progress);
}

static uint8_t pjvm_gc_sweep(PJVMCtx *j) {
    uint32_t end = pjvm_gc_heap_limit_full(j);
    uint16_t free_head = 0;
    uint16_t free_tail = 0;
    uint16_t live_used = 0;
    uint8_t reclaimed = 0;

    for (uint32_t blk32 = j->heap_base; blk32 < end; ) {
        uint16_t blk = (uint16_t)blk32;
        uint16_t size = pjvm_gc_blk_size(blk);
        uint8_t make_free = 0;

        if (size < PJVM_HEAP_FREE_HDR || blk32 + size > end) break;

        if (pjvm_gc_blk_is_allocated(blk)) {
            uint16_t meta = pjvm_gc_blk_meta(blk);
            if ((meta & PJVM_HEAP_META_MARK) != 0) {
                pjvm_gc_blk_set_meta(blk,
                                     (uint16_t)(meta &
                                                (uint16_t)~(PJVM_HEAP_META_MARK |
                                                            PJVM_HEAP_META_PENDING)));
                live_used = (uint16_t)(live_used + size);
            } else {
                make_free = 1;
                reclaimed = 1;
                pjvm_gc_blk_set_size(blk, size, 0);
                pjvm_gc_blk_set_next(blk, 0);
            }
        } else {
            make_free = 1;
            pjvm_gc_blk_set_next(blk, 0);
        }

        if (make_free) {
            if (free_tail != 0 &&
                (uint16_t)(free_tail + pjvm_gc_blk_size(free_tail)) == blk) {
                pjvm_gc_blk_set_size(free_tail,
                                     (uint16_t)(pjvm_gc_blk_size(free_tail) + size),
                                     0);
            } else {
                if (free_tail != 0) pjvm_gc_blk_set_next(free_tail, blk);
                else free_head = blk;
                pjvm_gc_blk_set_size(blk, size, 0);
                pjvm_gc_blk_set_next(blk, 0);
                free_tail = blk;
            }
        }

        blk32 += size;
    }

    j->heap_free_head = free_head;
    j->heap_used = live_used;
    return reclaimed;
}
#endif

void pjvm_gc_init(PJVMCtx *j) {
    j->gc_lfsr = (uint16_t)(j->heap_base ? j->heap_base : 0xACE1u);
    j->gc_count = 0;
}

uint8_t pjvm_gc_collect(PJVMCtx *j, uint8_t reason) {
    uint8_t reclaimed = 0;
    (void)reason;

#if PJVM_HEAP_MODE == PJVM_HEAP_FREELIST
    pjvm_gc_mark_roots(j);
    pjvm_gc_trace(j);
    reclaimed = pjvm_gc_sweep(j);
#endif

    j->gc_count++;
    return reclaimed;
}

void pjvm_gc_maybe(PJVMCtx *j, uint8_t reason, uint16_t alloc_size) {
#if !PJVM_GC_ENABLED
    (void)j;
    (void)reason;
    (void)alloc_size;
#else
    uint8_t should_collect = 0;

#if !((PJVM_GC_TRIGGERS & PJVM_GC_TRIG_WATERMARK) || \
      (PJVM_GC_TRIGGERS & PJVM_GC_TRIG_RANDOM_ABOVE_WATERMARK))
    (void)alloc_size;
#endif

    if ((reason & PJVM_GC_TRIG_ALLOC_FAIL) != 0 &&
        (PJVM_GC_TRIGGERS & PJVM_GC_TRIG_ALLOC_FAIL) != 0)
        should_collect = 1;

#if PJVM_GC_TRIGGERS & PJVM_GC_TRIG_WATERMARK
    if (!should_collect &&
        (reason & PJVM_GC_TRIG_WATERMARK) != 0 &&
        pjvm_gc_above_watermark(j, alloc_size))
        should_collect = 1;
#endif

#if PJVM_GC_TRIGGERS & PJVM_GC_TRIG_RETURN
    if (!should_collect &&
        (reason & PJVM_GC_TRIG_RETURN) != 0)
        should_collect = 1;
#endif

#if PJVM_GC_TRIGGERS & PJVM_GC_TRIG_RANDOM_ABOVE_WATERMARK
    if (!should_collect &&
        (reason & PJVM_GC_TRIG_RANDOM_ABOVE_WATERMARK) != 0 &&
        pjvm_gc_above_watermark(j, alloc_size) &&
        (pjvm_gc_next_random(j) & PJVM_GC_RANDOM_MASK) == 0)
        should_collect = 1;
#endif

    if (should_collect) (void)pjvm_gc_collect(j, reason);
#endif
}
