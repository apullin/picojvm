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

#define PJVM_PC_HALT 0xFFFFFFFFu

/* trap codes for pjvm_platform_trap (op argument) */
#define PJVM_TRAP_BAD_VERSION  0xFD
#define PJVM_TRAP_STEP_LIMIT   0xFE
#define PJVM_TRAP_BAD_NATIVE   0xFF

/* --- .pjvm v3 binary format constants -------------------------------- */
#define PJVM_MAGIC        0x85
#define PJVM_VERSION_V3   0x4C

#define PJVM_HDR_SIZE_V3  16
#define PJVM_MT_ENTRY     14   /* method table entry size */
#define PJVM_ET_ENTRY     7    /* exception table entry size */

/* region_flags (header byte 9) */
#define PJVM_RF_PIN_HINTS  0x01   /* bit 0: pin hints present */
#define PJVM_RF_CONST_DATA 0x04   /* bit 2: const_data section present */

/* CP resolution string flag / mask (16-bit) */
#define PJVM_CP_STR_FLAG_16  0x8000
#define PJVM_CP_STR_MASK_16  0x7FFF

/* Tagged 32-bit refs stored in VM slots.
 * Heap refs use hi=0 and lo=heap address.
 * ROM string refs use hi=0x8000 and lo=string constant index. */
#define PJVM_REF_ROM_STRING  0x8000

/* sentinel values */
#define PJVM_NO_CLASS     0xFF   /* parent_class_id / class_id = none */
#define PJVM_NO_VTABLE    0xFF   /* vtable_slot = not virtual */
#define PJVM_NO_CLINIT    0xFF   /* clinit_mi = no <clinit> */

/* const_data elem_type codes */
#define PJVM_ELEM_BYTE    0
#define PJVM_ELEM_CHAR    1
#define PJVM_ELEM_SHORT   2
#define PJVM_ELEM_INT     3

/* object/array memory layout */
#define PJVM_OBJ_HEADER   4     /* bytes before element data in arrays */

/* --- per-execution context -------------------------------------------- */
typedef struct {
    uint32_t pc;
    uint16_t cb;
    uint16_t lb;
    uint16_t so;
    uint8_t  mi;
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
    uint16_t cur_cb;
    uint16_t sp, lt, cur_lb;
    uint8_t  cur_mi;
    int8_t   fdepth;
    uint16_t heap_ptr;
    uint16_t heap_base;
    uint16_t heap_limit;      /* exclusive end; 0 means 0x10000 */
    uint16_t heap_used;       /* allocator-private; estimated live bytes */
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
extern uint8_t  n_methods, main_mi, n_classes;
extern uint32_t bytecodes_size;
extern uint32_t bc_off, cpr_off, ic_off, sc_off, et_off, cd_off;
extern PJVMCtx *g_pjvm;

#ifdef PJVM_ASM_HELPERS
/* 8085 ASM helpers need direct pointers (non-paged target only) */
extern uint8_t *cpr;
extern uint8_t *bc;
extern uint8_t *sc;
#endif

/* --- core API (implemented in core.c) --------------------------------- */
void pjvm_parse(uint8_t *data);
void pjvm_run(PJVMCtx *j);
void pjvm_heap_init(PJVMCtx *j, uint16_t start, uint16_t limit);
uint16_t pjvm_heap_alloc(PJVMCtx *j, uint16_t size);
void pjvm_heap_free(PJVMCtx *j, uint16_t a);

#ifdef PJVM_PAGED
void pjvm_pager_init(PJVMPager *p);
void pjvm_pin_chunk(PJVMPager *p, uint16_t chunk);
#endif

/* --- platform callbacks (implemented by each platform .c) ------------- */
uint16_t heap_alloc(PJVMCtx *j, uint16_t size);
uint8_t  r8(uint16_t a);
void     w8(uint16_t a, uint8_t v);
uint16_t r16(uint16_t a);
void     w16(uint16_t a, uint16_t v);
void     pjvm_platform_putchar(uint8_t ch);
uint8_t  pjvm_platform_peek8(uint32_t a);
void     pjvm_platform_poke8(uint32_t a, uint8_t v);
void     pjvm_platform_trap(uint8_t op, uint16_t pc);
void     pjvm_platform_out(uint16_t port, uint16_t val);

/* --- file I/O callbacks (implemented by each platform .c) ------------- */
int32_t  pjvm_platform_file_open(const uint8_t *name, uint8_t nameLen, uint8_t mode);
int32_t  pjvm_platform_file_read_byte(void);
void     pjvm_platform_file_write_byte(uint8_t b);
void     pjvm_platform_file_close(uint8_t mode); /* 0=both, 1=read, 2=write */
int32_t  pjvm_platform_file_delete(const uint8_t *name, uint8_t nameLen);

#endif /* PJVM_H */
