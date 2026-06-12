/*
 * pjvm.h — picoJVM public types and API.
 *
 * Include this from platform files and from core.c.
 * Capacity macros have defaults; override with -D flags if needed.
 */
#ifndef PJVM_H
#define PJVM_H

#include <stdint.h>
#include "pjvm_opts.h"

/* --- little-endian read helpers --------------------------------------- */
#define RD16LE(p) ((uint16_t)(p)[0] | ((uint16_t)(p)[1] << 8))
#define RD32LE(p) ((uint32_t)(p)[0] | ((uint32_t)(p)[1] << 8) \
                 | ((uint32_t)(p)[2] << 16) | ((uint32_t)(p)[3] << 24))

/* --- capacity defaults (override with -D flags) ----------------------- */
#ifndef PJVM_METHOD_CAP
#define PJVM_METHOD_CAP 256
#endif
#ifndef PJVM_CLASS_CAP
#define PJVM_CLASS_CAP 64
#endif
#ifndef PJVM_VTABLE_CAP
#define PJVM_VTABLE_CAP 256
#endif
#ifndef PJVM_STATIC_CAP
#define PJVM_STATIC_CAP 1024
#endif
#ifndef PJVM_MAX_STACK
#define PJVM_MAX_STACK 256
#endif
#ifndef PJVM_MAX_LOCALS
#define PJVM_MAX_LOCALS 1024
#endif
#ifndef PJVM_MAX_FRAMES
#define PJVM_MAX_FRAMES 64
#endif
/* fdepth is int8_t (PJVMCtx layout is frozen for the 8085 asm helpers), so
 * the enforceable call depth tops out at 127 even if PJVM_MAX_FRAMES is
 * configured larger. */
#define PJVM_FDEPTH_LIMIT (PJVM_MAX_FRAMES < 127 ? PJVM_MAX_FRAMES : 127)
/* Operand-stack headroom required at each invoke. Per-method max stack is
 * not carried in the .pjvm image, so this is the conservative bound on what
 * one frame may consume; size tiny-config PJVM_MAX_STACK accordingly. */
#ifndef PJVM_STACK_HEADROOM
#define PJVM_STACK_HEADROOM 32
#endif
#ifndef PJVM_ENABLE_V4
#define PJVM_ENABLE_V4 0
#endif
/* Loader formats are independent build options: v3-only (default), v4-only
 * (-DPJVM_ENABLE_V4=1 -DPJVM_ENABLE_V3=0, drops ~3KB of loader on 8085), or
 * both. Programs stay universal — pjvmpack --format picks the image format. */
#ifndef PJVM_ENABLE_V3
#define PJVM_ENABLE_V3 1
#endif
#if !PJVM_ENABLE_V3 && !PJVM_ENABLE_V4
#error "At least one of PJVM_ENABLE_V3 / PJVM_ENABLE_V4 must be enabled"
#endif
#ifndef PJVM_USE_OP_WIDE
#define PJVM_USE_OP_WIDE PJVM_ENABLE_V4
#endif
#ifndef PJVM_USE_CONST_STRING_ARRAYS
#define PJVM_USE_CONST_STRING_ARRAYS 0
#endif
#ifndef PJVM_USE_CONST_OBJECT_ARRAYS
#define PJVM_USE_CONST_OBJECT_ARRAYS 0
#endif
#if PJVM_ENABLE_V4 && defined(PJVM_ASM_HELPERS)
#error "PJVM_ENABLE_V4 changes PJVMCtx layout; disable 8085 ASM helpers for v4 builds"
#endif

#define PJVM_PC_HALT 0xFFFFFFFFu

/* trap codes for pjvm_platform_trap (op argument) */
#define PJVM_TRAP_DIV_ZERO     0xF8 /* integer division by zero */
#define PJVM_TRAP_STACK_OVERFLOW 0xF9 /* frames/locals/operand stack exhausted */
#define PJVM_TRAP_UNSUPPORTED  0xFA /* image needs a compiled-out feature */
#define PJVM_TRAP_BAD_METHOD   0xFB
#define PJVM_TRAP_CAPACITY     0xFC
#define PJVM_TRAP_BAD_VERSION  0xFD
#define PJVM_TRAP_STEP_LIMIT   0xFE
#define PJVM_TRAP_BAD_NATIVE   0xFF

/* --- .pjvm binary format constants ----------------------------------- */
#define PJVM_MAGIC        0x85
#define PJVM_VERSION_V3   0x4C
#define PJVM_VERSION_V4   0x4D

#define PJVM_HDR_SIZE_V3  16
#define PJVM_HDR_SIZE_V4  24
#define PJVM_MT_ENTRY     14   /* v3 method table entry size */
#define PJVM_MT_ENTRY_V4  24   /* v4 method table entry size */
#define PJVM_ET_ENTRY     7    /* v3 exception table entry size */
#define PJVM_ET_ENTRY_V4  8    /* v4 exception table entry size */

/* region_flags (header byte 9) */
#define PJVM_RF_PIN_HINTS  0x01   /* bit 0: pin hints present */
#define PJVM_RF_REF_BITMAPS 0x02  /* bit 1: per-class ref bitmaps present */
#define PJVM_RF_CONST_DATA 0x04   /* bit 2: const_data section present */
#define PJVM_RF_PACKED_METHOD_TABLE 0x08 /* bit 3: v4 ULEB method table */
#define PJVM_RF_STATIC_REF_BITMAP 0x10 /* bit 4: static-slot ref bitmap */

/* CP resolution string flag / mask (16-bit) */
#define PJVM_CP_STR_FLAG_16  0x8000
#define PJVM_CP_STR_MASK_16  0x7FFF

/* Tagged 32-bit refs stored in VM slots.
 * Heap refs use hi=0 and lo=heap address.
 * ROM string refs use hi=0x8000 and lo=string constant index.
 * ROM object refs use hi>=0x8001 and lo/hi encode a program offset. */
#define PJVM_REF_ROM_STRING  0x8000
#define PJVM_REF_ROM_OBJECT_BASE 0x8001
#define PJVM_REF_IS_ROM_OBJECT(hi) ((hi) >= PJVM_REF_ROM_OBJECT_BASE)
#define PJVM_ROM_OBJECT_HI(off) ((uint16_t)(PJVM_REF_ROM_OBJECT_BASE + ((off) >> 16)))
#define PJVM_ROM_OBJECT_OFF(hi, lo) \
    ((((uint32_t)((hi) - PJVM_REF_ROM_OBJECT_BASE)) << 16) | (lo))

/* sentinel values */
#if PJVM_ENABLE_V4
typedef uint16_t pjvm_method_id_t;
typedef uint16_t pjvm_class_id_t;
typedef uint16_t pjvm_vslot_t;
typedef uint16_t pjvm_vmid_t;
typedef uint16_t pjvm_count_t;
typedef uint16_t pjvm_flags_t;
typedef uint32_t pjvm_cpbase_t;
typedef uint32_t pjvm_rbo_t;
#define PJVM_NO_CLASS     0xFFFFu /* parent_class_id / class_id = none */
#define PJVM_NO_VTABLE    0xFFFFu /* vtable_slot = not virtual */
#define PJVM_NO_CLINIT    0xFFFFu /* clinit_mi = no <clinit> */
#else
typedef uint8_t pjvm_method_id_t;
typedef uint8_t pjvm_class_id_t;
typedef uint8_t pjvm_vslot_t;
typedef uint8_t pjvm_vmid_t;
typedef uint8_t pjvm_count_t;
typedef uint8_t pjvm_flags_t;
typedef uint16_t pjvm_cpbase_t;
typedef uint16_t pjvm_rbo_t;
#define PJVM_NO_CLASS     0xFFu   /* parent_class_id / class_id = none */
#define PJVM_NO_VTABLE    0xFFu   /* vtable_slot = not virtual */
#define PJVM_NO_CLINIT    0xFFu   /* clinit_mi = no <clinit> */
#endif

/* const_data elem_type codes */
#define PJVM_ELEM_BYTE    0
#define PJVM_ELEM_CHAR    1
#define PJVM_ELEM_SHORT   2
#define PJVM_ELEM_INT     3
#define PJVM_ELEM_STRING_REF 4
#define PJVM_ELEM_OBJECT_REF 5
#define PJVM_CONST_NULL_REF 0xFFFFu

/* object/array memory layout */
#define PJVM_OBJ_HEADER   4     /* bytes before element data in arrays */

/* heap allocation kinds for GC metadata */
#define PJVM_HEAP_KIND_OBJECT      1u
#define PJVM_HEAP_KIND_BYTE_ARRAY  2u
#define PJVM_HEAP_KIND_SHORT_ARRAY 3u
#define PJVM_HEAP_KIND_INT_ARRAY   4u
#define PJVM_HEAP_KIND_REF_ARRAY   5u
#define PJVM_HEAP_KIND_STRING      6u

#if PJVM_HEAP_MODE == PJVM_HEAP_FREELIST
#define PJVM_HEAP_ALLOC_FLAG  0x0001u
#define PJVM_HEAP_ALLOC_HDR   4u
#define PJVM_HEAP_FREE_HDR    4u
#define PJVM_HEAP_MIN_SPLIT   8u
#define PJVM_HEAP_META_KIND_MASK 0x000Fu
#define PJVM_HEAP_META_MARK      0x0010u
#define PJVM_HEAP_META_PENDING   0x0020u
#endif

#if PJVM_GC_ALLOC_BITMAP && \
    (!PJVM_GC_ENABLED || PJVM_HEAP_MODE != PJVM_HEAP_FREELIST)
#error "PJVM_GC_ALLOC_BITMAP requires the freelist heap and GC triggers"
#endif

/* Boot overlay: with a linker script that places .pjvmboot inside the
 * heap window (ldscripts/i8085-64k-flat-boot.ld), the .pjvm loader is
 * reclaimed as heap after it runs - it executes exactly once, before
 * pjvm_heap_init. No-op unless the build opts in. */
#ifndef PJVM_BOOT_OVERLAY
#define PJVM_BOOT_OVERLAY 0
#endif
#if PJVM_BOOT_OVERLAY
#define PJVM_BOOT_FN __attribute__((section(".pjvmboot")))
#else
#define PJVM_BOOT_FN
#endif

/* --- per-execution context -------------------------------------------- */
typedef struct {
    uint32_t pc;
    pjvm_cpbase_t cb;
    uint16_t lb;
    uint16_t so;
    pjvm_method_id_t mi;
} PJVMFrame;

#ifdef PJVM_PAGED
/* --- fixed-page pager ------------------------------------------------- */
#ifndef PJVM_MAX_PAGES
#define PJVM_MAX_PAGES 16
#endif

typedef struct {
    uint8_t  *pool;         /* flat buffer: n_pages * page_size bytes */
    uint16_t  page_size;    /* 256, 512, 1024, 4096, etc. */
    uint8_t   page_shift;   /* log2(page_size) — bit-shift division */
    uint8_t   n_pages;      /* number of page slots in pool */
    uint32_t  file_size;    /* total .pjvm file size */
    uint16_t  tag[PJVM_MAX_PAGES];   /* which chunk is in each slot (0xFFFF = empty) */
    uint8_t   age[PJVM_MAX_PAGES];   /* LRU counter */
    uint8_t   pinned[PJVM_MAX_PAGES]; /* 1 = don't evict */
    uint8_t   lru_clock;
    /* platform read callback */
    void (*read_fn)(uint32_t file_offset, uint8_t *buf, uint16_t len, void *ctx);
    void *read_ctx;
    /* stats */
    uint32_t  hits;
    uint32_t  misses;
    uint8_t   slot_misses[PJVM_MAX_PAGES];
} PJVMPager;
#endif /* PJVM_PAGED */

typedef struct {
    uint16_t stk_lo[PJVM_MAX_STACK], stk_hi[PJVM_MAX_STACK];
    uint16_t loc_lo[PJVM_MAX_LOCALS], loc_hi[PJVM_MAX_LOCALS];
    uint16_t sf_lo[PJVM_STATIC_CAP], sf_hi[PJVM_STATIC_CAP];
    PJVMFrame frames[PJVM_MAX_FRAMES];
    uint32_t pc;
    pjvm_cpbase_t cur_cb;
    uint16_t sp, lt, cur_lb;
    pjvm_method_id_t cur_mi;
    int8_t   fdepth;
    uint16_t heap_ptr;
    uint16_t heap_base;
    uint16_t heap_limit;      /* exclusive end; 0 means 0x10000 */
    uint16_t heap_free_head;  /* allocator-private; free-list head */
    uint16_t heap_used;       /* allocator-private; estimated live bytes */
    uint16_t gc_lfsr;         /* GC trigger PRNG state */
    uint16_t gc_count;        /* number of completed collections */
    uint16_t sp_max, lt_max;
    uint8_t  fdepth_max;
    const char **prog_argv;
    uint8_t prog_argc;
#ifdef PJVM_PAGED
    PJVMPager *pager;  /* NULL = non-paged */
#endif
} PJVMCtx;

/* --- globals (defined in core.c, readable by platform) ---------------- */
extern uint8_t *pjvm_prog;
extern uint32_t pjvm_prog_size;
extern pjvm_method_id_t n_methods, main_mi;
extern pjvm_class_id_t n_classes;
extern uint32_t bytecodes_size;
extern uint32_t bc_off, cpr_off, ic_off, sc_off, et_off, cd_off;
extern PJVMCtx *g_pjvm;
extern uint8_t  region_flags;
extern uint16_t n_static_fields;
extern uint32_t pjvm_srb_off;  /* static-ref bitmap offset (0 = absent) */
extern pjvm_count_t cls_nf[PJVM_CLASS_CAP];
extern pjvm_rbo_t cls_rbo[PJVM_CLASS_CAP];

#ifdef PJVM_ASM_HELPERS
/* 8085 ASM helpers need direct pointers (non-paged target only) */
extern uint8_t *cpr;
extern uint8_t *bc;
extern uint8_t *sc;
#endif

/* --- core API (implemented in core.c) --------------------------------- */
void pjvm_parse(uint8_t *data);
void pjvm_run(PJVMCtx *j);
uint8_t pjvm_prog_read(uint32_t off);
/* Binds j as the current VM (g_pjvm).  The heap, GC, and interpreter all
 * operate on the bound context, so a future scheduler switches green
 * threads by rebinding before resuming.  Call before pjvm_run(). */
void pjvm_heap_init(PJVMCtx *j, uint16_t start, uint16_t limit);
uint16_t pjvm_heap_alloc(uint16_t size, uint8_t kind);
void pjvm_heap_free(uint16_t a);
#if PJVM_GC_ENABLED || defined(PJVM_GC_IMPL)
void pjvm_gc_init(void);
uint8_t pjvm_gc_collect(uint8_t reason);
void pjvm_gc_maybe(uint8_t reason, uint16_t alloc_size);
#else
#define pjvm_gc_init() ((void)0)
#define pjvm_gc_collect(reason) ((void)(reason), 0)
#define pjvm_gc_maybe(reason, alloc_size) \
    ((void)(reason), (void)(alloc_size))
#endif

/*
 * GC temp roots: pin refs held only in C locals across an allocation. Once a
 * ref is popped off the Java stack the collector cannot see it, so any
 * heap_alloc between the pop and the last use of that ref could free it.
 * The collector is non-moving, so pinning only prevents reclamation; the
 * C-held address stays valid. Compiles to nothing when GC is disabled.
 */
#if PJVM_GC_ENABLED
#define PJVM_GC_TEMP_ROOT_CAP 8
extern uint16_t pjvm_gc_temp_lo[PJVM_GC_TEMP_ROOT_CAP];
extern uint16_t pjvm_gc_temp_hi[PJVM_GC_TEMP_ROOT_CAP];
extern uint8_t pjvm_gc_temp_count;
void pjvm_gc_protect(uint16_t lo, uint16_t hi);
#define PJVM_GC_PROTECT(lo, hi) pjvm_gc_protect((lo), (hi))
#define PJVM_GC_UNPROTECT(n) \
    (pjvm_gc_temp_count = (uint8_t)(pjvm_gc_temp_count - (n)))
#else
#define PJVM_GC_PROTECT(lo, hi) ((void)(lo), (void)(hi))
#define PJVM_GC_UNPROTECT(n) ((void)0)
#endif

#if PJVM_GC_ALLOC_BITMAP
extern uint8_t pjvm_gc_alloc_bm[PJVM_GC_BITMAP_SPAN >> 4];
void pjvm_gc_bm_set(uint16_t payload);
void pjvm_gc_bm_clear(uint16_t payload);
#endif

#ifdef PJVM_PAGED
void pjvm_pager_init(PJVMPager *p);
void pjvm_pin_chunk(PJVMPager *p, uint16_t chunk);
#endif

/* --- platform callbacks (implemented by each platform .c) ------------- */
uint16_t heap_alloc(uint16_t size, uint8_t kind);
uint8_t  r8(uint16_t a);
void     w8(uint16_t a, uint8_t v);
uint16_t r16(uint16_t a);
void     w16(uint16_t a, uint16_t v);
void     pjvm_platform_putchar(uint8_t ch);
uint8_t  pjvm_platform_peek8(uint32_t a);
void     pjvm_platform_poke8(uint32_t a, uint8_t v);
void     pjvm_platform_trap(uint8_t op, uint16_t pc);
void     pjvm_platform_out(uint16_t port, uint16_t val);
int32_t  pjvm_platform_term_info(uint16_t code);
int32_t  pjvm_platform_key_read(void);
int32_t  pjvm_platform_ticks(void);

/* --- file I/O callbacks (implemented by each platform .c) ------------- */
int32_t  pjvm_platform_file_open(const uint8_t *name, uint8_t nameLen, uint8_t mode);
int32_t  pjvm_platform_file_read_byte(void);
void     pjvm_platform_file_write_byte(uint8_t b);
void     pjvm_platform_file_close(uint8_t mode); /* 0=both, 1=read, 2=write */
int32_t  pjvm_platform_file_delete(const uint8_t *name, uint8_t nameLen);

#endif /* PJVM_H */
