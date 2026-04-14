#define PJVM_GC_IMPL 1

#include "../src/pjvm.h"

#include <stdio.h>
#include <string.h>

static int failed;

static void gc_demo_init(PJVMCtx *j, uint16_t used) {
    memset(j, 0, sizeof(*j));
    j->heap_base = 1;
    j->heap_limit = 101; /* capacity = 100 bytes */
    j->heap_used = used;
    pjvm_gc_init(j);
}

static void gc_demo_expect(const char *label, uint16_t got, uint16_t want) {
    printf("%s=%u\n", label, (unsigned)got);
    if (got != want) {
        fprintf(stderr, "FAIL: %s expected %u got %u\n",
                label, (unsigned)want, (unsigned)got);
        failed = 1;
    }
}

int main(void) {
    PJVMCtx j;
    uint16_t expected;
    uint16_t attempts;

    printf("triggers=0x%02X enabled=%u watermark=%u random_mask=0x%04X\n",
           (unsigned)PJVM_GC_TRIGGERS,
           (unsigned)PJVM_GC_ENABLED,
           (unsigned)PJVM_GC_WATERMARK_PCT,
           (unsigned)PJVM_GC_RANDOM_MASK);

    gc_demo_init(&j, 0);
    (void)pjvm_gc_collect(&j, 0x80);
    gc_demo_expect("manual_collect", j.gc_count, 1);

    gc_demo_init(&j, 0);
    pjvm_gc_maybe(&j, PJVM_GC_TRIG_ALLOC_FAIL, 0);
    expected = (PJVM_GC_TRIGGERS & PJVM_GC_TRIG_ALLOC_FAIL) ? 1 : 0;
    gc_demo_expect("alloc_fail", j.gc_count, expected);

    gc_demo_init(&j, 73);
    pjvm_gc_maybe(&j, PJVM_GC_TRIG_WATERMARK, 1);
    gc_demo_expect("watermark_below", j.gc_count, 0);

    gc_demo_init(&j, 74);
    pjvm_gc_maybe(&j, PJVM_GC_TRIG_WATERMARK, 1);
    expected = (PJVM_GC_TRIGGERS & PJVM_GC_TRIG_WATERMARK) ? 1 : 0;
    gc_demo_expect("watermark_at", j.gc_count, expected);

    gc_demo_init(&j, 10);
    pjvm_gc_maybe(&j, PJVM_GC_TRIG_RETURN, 0);
    expected = (PJVM_GC_TRIGGERS & PJVM_GC_TRIG_RETURN) ? 1 : 0;
    gc_demo_expect("return_trigger", j.gc_count, expected);

    gc_demo_init(&j, 90);
    attempts = 0;
    while (attempts < 256 && j.gc_count == 0) {
        pjvm_gc_maybe(&j, PJVM_GC_TRIG_RANDOM_ABOVE_WATERMARK, 0);
        attempts++;
    }
    printf("random_attempts=%u\n", (unsigned)attempts);
    expected = (PJVM_GC_TRIGGERS & PJVM_GC_TRIG_RANDOM_ABOVE_WATERMARK) ? 1 : 0;
    gc_demo_expect("random_trigger", j.gc_count ? 1 : 0, expected);

    return failed ? 1 : 0;
}
