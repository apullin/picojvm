/*
 * pjvm.c — picoJVM interpreter core.
 *
 * Portable bytecode interpreter. Platform-specific callbacks (heap, I/O)
 * are provided by the platform .c file linked alongside this.
 */
#include "pjvm.h"
#ifdef PJVM_BOUNDS_CHECK
#include <stdio.h>
#include <stdlib.h>
#endif

#define NI __attribute__((noinline))

/* --- opcodes ---------------------------------------------------------- */
enum {
    OP_NOP = 0x00, OP_ACONST_NULL = 0x01,
    OP_ICONST_M1 = 0x02, OP_ICONST_0 = 0x03, OP_ICONST_1 = 0x04,
    OP_ICONST_2 = 0x05, OP_ICONST_3 = 0x06, OP_ICONST_4 = 0x07,
    OP_ICONST_5 = 0x08,
    OP_BIPUSH = 0x10, OP_SIPUSH = 0x11, OP_LDC = 0x12, OP_LDC_W = 0x13,
    OP_ILOAD = 0x15, OP_ALOAD = 0x19,
    OP_ILOAD_0 = 0x1A, OP_ILOAD_1 = 0x1B, OP_ILOAD_2 = 0x1C, OP_ILOAD_3 = 0x1D,
    OP_ALOAD_0 = 0x2A, OP_ALOAD_1 = 0x2B, OP_ALOAD_2 = 0x2C, OP_ALOAD_3 = 0x2D,
    OP_IALOAD = 0x2E, OP_AALOAD = 0x32,
    OP_BALOAD = 0x33, OP_CALOAD = 0x34, OP_SALOAD = 0x35,
    OP_ISTORE = 0x36, OP_ASTORE = 0x3A,
    OP_ISTORE_0 = 0x3B, OP_ISTORE_1 = 0x3C, OP_ISTORE_2 = 0x3D, OP_ISTORE_3 = 0x3E,
    OP_IASTORE = 0x4F, OP_AASTORE = 0x53,
    OP_BASTORE = 0x54, OP_CASTORE = 0x55, OP_SASTORE = 0x56,
    OP_ASTORE_0 = 0x4B, OP_ASTORE_1 = 0x4C, OP_ASTORE_2 = 0x4D, OP_ASTORE_3 = 0x4E,
    OP_POP = 0x57, OP_POP2 = 0x58, OP_DUP = 0x59,
    OP_DUP_X1 = 0x5A, OP_DUP_X2 = 0x5B, OP_DUP2 = 0x5C,
    OP_SWAP = 0x5F,
    OP_IADD = 0x60, OP_ISUB = 0x64, OP_IMUL = 0x68,
    OP_IDIV = 0x6C, OP_IREM = 0x70, OP_INEG = 0x74,
    OP_ISHL = 0x78, OP_ISHR = 0x7A, OP_IUSHR = 0x7C,
    OP_IAND = 0x7E, OP_IOR = 0x80, OP_IXOR = 0x82,
    OP_IINC = 0x84,
    OP_I2B = 0x91, OP_I2C = 0x92, OP_I2S = 0x93,
    OP_IFEQ = 0x99, OP_IFNE = 0x9A, OP_IFLT = 0x9B,
    OP_IFGE = 0x9C, OP_IFGT = 0x9D, OP_IFLE = 0x9E,
    OP_IF_ICMPEQ = 0x9F, OP_IF_ICMPNE = 0xA0,
    OP_IF_ICMPLT = 0xA1, OP_IF_ICMPGE = 0xA2,
    OP_IF_ICMPGT = 0xA3, OP_IF_ICMPLE = 0xA4,
    OP_IF_ACMPEQ = 0xA5, OP_IF_ACMPNE = 0xA6,
    OP_GOTO = 0xA7,
    OP_TABLESWITCH = 0xAA, OP_LOOKUPSWITCH = 0xAB,
    OP_IRETURN = 0xAC, OP_ARETURN = 0xB0, OP_RETURN = 0xB1,
    OP_GETSTATIC = 0xB2, OP_PUTSTATIC = 0xB3,
    OP_GETFIELD = 0xB4, OP_PUTFIELD = 0xB5,
    OP_INVOKEVIRTUAL = 0xB6, OP_INVOKESPECIAL = 0xB7, OP_INVOKESTATIC = 0xB8,
    OP_INVOKEINTERFACE = 0xB9,
    OP_ATHROW = 0xBF,
    OP_NEW = 0xBB, OP_NEWARRAY = 0xBC, OP_ANEWARRAY = 0xBD,
    OP_ARRAYLENGTH = 0xBE,
    OP_CHECKCAST = 0xC0, OP_INSTANCEOF = 0xC1,
    OP_WIDE = 0xC4,
    OP_MULTIANEWARRAY = 0xC5,
    OP_IFNULL = 0xC6, OP_IFNONNULL = 0xC7,
};

enum {
    NATIVE_PUTCHAR = 0, NATIVE_IN = 1, NATIVE_OUT = 2,
    NATIVE_PEEK = 3, NATIVE_POKE = 4, NATIVE_HALT = 5,
    NATIVE_OBJECT_INIT = 6,
    NATIVE_STR_LENGTH = 7, NATIVE_STR_CHARAT = 8,
    NATIVE_STR_EQUALS = 9, NATIVE_STR_TOSTRING = 10,
    NATIVE_PRINT = 11,
    NATIVE_STR_HASHCODE = 12,
    NATIVE_ARRAYCOPY = 13,
    NATIVE_MEMCMP = 14,
    NATIVE_WRITE_BYTES = 15,
    NATIVE_STRING_FROM_BYTES = 16,
    NATIVE_FILE_OPEN = 17,
    NATIVE_FILE_READ_BYTE = 18,
    NATIVE_FILE_WRITE_BYTE = 19,
    NATIVE_FILE_READ = 20,
    NATIVE_FILE_WRITE = 21,
    NATIVE_FILE_CLOSE = 22,
    NATIVE_FILE_DELETE = 23,
    NATIVE_TERM_INFO = 24,
    NATIVE_KEY_READ = 25,
    NATIVE_TICKS = 26,
    NATIVE_ENUM_INIT = 27,
    NATIVE_ENUM_NAME = 28,
    NATIVE_ENUM_ORDINAL = 29,
    NATIVE_ENUM_TOSTRING = 30,
    NATIVE_ENUM_VALUEOF = 31,
    NATIVE_ARRAY_CLONE = 32,
};

/* --- globals (extern-declared in pjvm.h) ------------------------------ */
uint8_t *pjvm_prog;
uint32_t pjvm_prog_size;
pjvm_method_id_t n_methods, main_mi;
pjvm_class_id_t n_classes;
uint32_t bytecodes_size;
uint32_t bc_off, cpr_off, ic_off, sc_off, et_off, cd_off;
PJVMCtx *g_pjvm;

#if PJVM_USE_ASM_CPREAD
uint8_t *cpr;
#endif
#if PJVM_USE_ASM_FETCH_HELPERS || PJVM_USE_ASM_CPREAD
uint8_t *bc;
#endif
#if PJVM_USE_ASM_ROM_STRING_DATA || PJVM_USE_ASM_STRING_LEN || PJVM_USE_ASM_STRING_BYTE
uint8_t *sc;
#endif

/* --- internal globals (file-scope) ------------------------------------ */
static uint16_t n_static_fields;
static pjvm_count_t n_int_constants, n_string_constants;
uint8_t  region_flags;  /* byte 9: bit0=pin_hints, bit1=ref bitmaps, bit2=const_data */
static uint8_t pjvm_format_v4;
static pjvm_count_t m_ml[PJVM_METHOD_CAP], m_ac[PJVM_METHOD_CAP];
static pjvm_flags_t m_fl[PJVM_METHOD_CAP];
static pjvm_vslot_t m_vs[PJVM_METHOD_CAP];
static pjvm_vmid_t m_vmid[PJVM_METHOD_CAP];
static pjvm_count_t m_ec[PJVM_METHOD_CAP], m_eo[PJVM_METHOD_CAP];
static uint32_t m_co[PJVM_METHOD_CAP];
static pjvm_cpbase_t m_cb[PJVM_METHOD_CAP];
static pjvm_class_id_t cls_pid[PJVM_CLASS_CAP];
pjvm_count_t cls_nf[PJVM_CLASS_CAP];
static pjvm_count_t cls_vb[PJVM_CLASS_CAP], cls_vs[PJVM_CLASS_CAP];
static pjvm_method_id_t cls_ci[PJVM_CLASS_CAP];
pjvm_rbo_t cls_rbo[PJVM_CLASS_CAP];
static pjvm_method_id_t vt[PJVM_VTABLE_CAP];

/* --- program image access macro --------------------------------------- */
#ifdef PJVM_PAGED
static uint8_t prog_fetch(uint32_t offset);
#define PROG(off) prog_fetch(off)
#else
#define PROG(off) pjvm_prog[(off)]
#endif
#define BC(a) PROG(bc_off + (a))
#define PROG16(off) ((uint16_t)PROG(off) | ((uint16_t)PROG((off) + 1) << 8))
#if PJVM_USE_CONST_OBJECT_ARRAYS
#define ROM_OFF(hi, lo) (((uint32_t)((hi) - 1) << 16) | (lo))

static pjvm_class_id_t pjvm_ref_class_id(uint16_t lo, uint16_t hi) {
    if (PJVM_REF_IS_ROM_OBJECT(hi))
        return (pjvm_class_id_t)PROG16(PJVM_ROM_OBJECT_OFF(hi, lo));
    return (pjvm_class_id_t)r16(lo);
}
#else
#define ROM_OFF(hi, lo) (((uint32_t)((hi) - 1) << 16) | (lo))
#endif

/* --- paged mode implementation ---------------------------------------- */
#ifdef PJVM_PAGED

static uint8_t pjvm_find_victim(PJVMPager *p) {
    uint8_t best = 0xFF, best_age = 0xFF;
    for (uint8_t i = 0; i < p->n_pages; i++) {
        if (p->pinned[i]) continue;
        if (p->tag[i] == 0xFFFF) return i;
        if (p->age[i] < best_age) {
            best_age = p->age[i];
            best = i;
        }
    }
    return best;
}

static void pjvm_load_page(PJVMPager *p, uint8_t slot, uint16_t chunk) {
    uint32_t file_offset = (uint32_t)chunk << p->page_shift;
    uint16_t len = p->page_size;
    if (file_offset + len > p->file_size)
        len = (uint16_t)(p->file_size - file_offset);
    p->read_fn(file_offset, p->pool + (uint32_t)slot * p->page_size, len, p->read_ctx);
    p->tag[slot] = chunk;
    p->age[slot] = ++p->lru_clock;
}

static uint8_t prog_fetch(uint32_t offset) {
    PJVMPager *p = g_pjvm->pager;
    uint16_t chunk = (uint16_t)(offset >> p->page_shift);
    uint16_t within = (uint16_t)(offset & (p->page_size - 1));

    for (uint8_t i = 0; i < p->n_pages; i++) {
        if (p->tag[i] == chunk) {
            p->age[i] = ++p->lru_clock;
            p->hits++;
            return p->pool[(uint32_t)i * p->page_size + within];
        }
    }

    p->misses++;
    uint8_t victim = pjvm_find_victim(p);
    p->slot_misses[victim]++;
    pjvm_load_page(p, victim, chunk);
    return p->pool[(uint32_t)victim * p->page_size + within];
}

void pjvm_pager_init(PJVMPager *p) {
    for (uint8_t i = 0; i < PJVM_MAX_PAGES; i++) {
        p->tag[i] = 0xFFFF;
        p->age[i] = 0;
        p->pinned[i] = 0;
        p->slot_misses[i] = 0;
    }
    p->lru_clock = 0;
    p->hits = 0;
    p->misses = 0;
}

void pjvm_pin_chunk(PJVMPager *p, uint16_t chunk) {
    for (uint8_t i = 0; i < p->n_pages; i++) {
        if (p->tag[i] == chunk) {
            p->pinned[i] = 1;
            return;
        }
    }
    uint8_t slot = pjvm_find_victim(p);
    pjvm_load_page(p, slot, chunk);
    p->pinned[slot] = 1;
}

#endif /* PJVM_PAGED */

uint8_t pjvm_prog_read(uint32_t off) {
    return PROG(off);
}

/* --- noinline helpers for code size ----------------------------------- */

#if PJVM_USE_ASM_STACK_HELPERS
/* Provided by i8085_helpers.S */
extern void     spush(uint16_t lo, uint16_t hi);
extern uint16_t spop_lo(void);
extern uint16_t spop_hi(void);
extern void     lload(uint8_t slot);
extern void     lstore(uint8_t slot);
#else
NI static void spush(uint16_t lo, uint16_t hi) {
    g_pjvm->stk_lo[g_pjvm->sp] = lo;
    g_pjvm->stk_hi[g_pjvm->sp] = hi;
    g_pjvm->sp++;
    if (g_pjvm->sp > g_pjvm->sp_max) g_pjvm->sp_max = g_pjvm->sp;
}

NI static uint16_t spop_lo(void) {
    g_pjvm->sp--;
    return g_pjvm->stk_lo[g_pjvm->sp];
}

NI static uint16_t spop_hi(void) {
    return g_pjvm->stk_hi[g_pjvm->sp];
}

NI static void lload(uint8_t slot) {
    uint16_t i = g_pjvm->cur_lb + slot;
    spush(g_pjvm->loc_lo[i], g_pjvm->loc_hi[i]);
}

NI static void lstore(uint8_t slot) {
    uint16_t i = g_pjvm->cur_lb + slot;
    g_pjvm->sp--;
    g_pjvm->loc_lo[i] = g_pjvm->stk_lo[g_pjvm->sp];
    g_pjvm->loc_hi[i] = g_pjvm->stk_hi[g_pjvm->sp];
}
#endif

#if PJVM_USE_OP_WIDE
NI static void lload_w(uint16_t slot) {
    uint16_t i = g_pjvm->cur_lb + slot;
    spush(g_pjvm->loc_lo[i], g_pjvm->loc_hi[i]);
}

NI static void lstore_w(uint16_t slot) {
    uint16_t i = g_pjvm->cur_lb + slot;
    g_pjvm->sp--;
    g_pjvm->loc_lo[i] = g_pjvm->stk_lo[g_pjvm->sp];
    g_pjvm->loc_hi[i] = g_pjvm->stk_hi[g_pjvm->sp];
}
#endif

#if PJVM_USE_ASM_FETCH_HELPERS
extern uint8_t  bcread(void);
extern int16_t  bread(void);
#else
NI static uint8_t bcread(void) {
    return BC(g_pjvm->pc++);
}

NI static int16_t bread(void) {
    int16_t o = (int16_t)((BC(g_pjvm->pc) << 8) | BC(g_pjvm->pc + 1));
    g_pjvm->pc += 2;
    return o;
}
#endif

#if PJVM_USE_ASM_CPREAD
extern uint16_t cpread(void);
#endif
#if PJVM_USE_ASM_ROM_STRING_DATA
extern uint32_t pjvm_rom_string_data(uint16_t idx, uint16_t *len_out);
#endif
#if PJVM_USE_ASM_STRING_LEN
extern uint16_t pjvm_string_len(uint16_t lo, uint16_t hi);
#endif
#if PJVM_USE_ASM_STRING_BYTE
extern uint8_t  pjvm_string_byte(uint16_t lo, uint16_t hi, uint16_t idx);
#endif
/* Native handler ASM implementations */
extern void     pjvm_native_arraycopy(void);
extern void     pjvm_native_memcmp(void);
extern void     pjvm_native_write_bytes(void);
extern void     pjvm_native_string_from_bytes(void);

/* Common JVM stack pop shapes: one full 32-bit slot or one low-half value. */
#define SPOP32(lo, hi) do { \
    (lo) = spop_lo(); \
    (hi) = spop_hi(); \
} while (0)

#define SPOP_U16(lo) do { \
    (lo) = spop_lo(); \
    (void)spop_hi(); \
} while (0)

/* High half of a sign-extended 8/16-bit JVM int value. */
#define SIGN8_HI(v)  ((v) < 0 ? 0xFFFFu : 0u)
#define SIGN16_HI(v) ((int16_t)(v) < 0 ? 0xFFFFu : 0u)

/* cpread: resolve 16-bit CP index to global index via per-method CP table */
#if 0
NI static uint16_t cpread(void) {
    uint16_t idx = (BC(g_pjvm->pc) << 8) | BC(g_pjvm->pc + 1);
    g_pjvm->pc += 2;
    uint32_t off = cpr_off + (uint32_t)g_pjvm->cur_cb + (uint32_t)idx * 2;
    return PROG16(off);
}
#endif

#if !PJVM_USE_ASM_CPREAD
NI static uint16_t cpread(void) {
    uint16_t idx = (BC(g_pjvm->pc) << 8) | BC(g_pjvm->pc + 1);
    g_pjvm->pc += 2;
    uint32_t off = cpr_off + (uint32_t)g_pjvm->cur_cb + (uint32_t)idx * 2;
    return PROG16(off);
}
#endif

static int32_t pjvm_to32(uint16_t lo, uint16_t hi) {
    return (int32_t)((uint32_t)lo | ((uint32_t)hi << 16));
}

NI static void pjvm_push32(int32_t v) {
    spush((uint16_t)v, (uint16_t)((uint32_t)v >> 16));
}

static uint16_t pjvm_make_string(PJVMCtx *j, const uint8_t *buf, uint16_t len) {
    uint16_t a = heap_alloc(j, (uint16_t)(PJVM_OBJ_HEADER + len), PJVM_HEAP_KIND_STRING);
    w16(a, len);
    w16((uint16_t)(a + 2), 0);
    for (uint16_t i = 0; i < len; i++) w8((uint16_t)(a + PJVM_OBJ_HEADER + i), buf[i]);
    return a;
}

/* String literals stay in the program image and are addressed by index. */
static uint8_t pjvm_is_rom_string(uint16_t hi) {
    return hi == PJVM_REF_ROM_STRING;
}

#if !PJVM_USE_ASM_ROM_STRING_DATA
static uint32_t pjvm_rom_string_data(uint16_t idx, uint16_t *len_out) {
    uint32_t off = sc_off;
    for (uint16_t i = 0; i < idx; i++) {
        off += 2 + PROG16(off);
    }
    *len_out = PROG16(off);
    return off + 2;
}
#endif

#if !PJVM_USE_ASM_STRING_LEN
static uint16_t pjvm_string_len(uint16_t lo, uint16_t hi) {
    if (pjvm_is_rom_string(hi)) {
        uint16_t len = 0;
        pjvm_rom_string_data(lo, &len);
        return len;
    }
    return r16(lo);
}
#endif

#if !PJVM_USE_ASM_STRING_BYTE
static uint8_t pjvm_string_byte(uint16_t lo, uint16_t hi, uint16_t idx) {
    if (pjvm_is_rom_string(hi)) {
        uint16_t len = 0;
        uint32_t data = pjvm_rom_string_data(lo, &len);
        (void)len;
        return PROG(data + idx);
    }
    return r8((uint16_t)(lo + PJVM_OBJ_HEADER + idx));
}
#endif

static uint16_t pjvm_make_main_args(PJVMCtx *j) {
    uint16_t argc = j->prog_argc;
    uint16_t a = heap_alloc(j, (uint16_t)(PJVM_OBJ_HEADER + argc * 4),
                            PJVM_HEAP_KIND_REF_ARRAY);
    w16(a, argc);
    w16((uint16_t)(a + 2), 0);
    for (uint16_t i = 0; i < argc; i++) {
        const char *arg = j->prog_argv ? j->prog_argv[i] : 0;
        uint16_t len = 0;
        if (arg != 0) while (arg[len] != 0) len++;
        uint16_t sref = pjvm_make_string(j, (const uint8_t *)arg, len);
        w16((uint16_t)(a + PJVM_OBJ_HEADER + i * 4), sref);
        w16((uint16_t)(a + PJVM_OBJ_HEADER + i * 4 + 2), 0);
    }
    return a;
}

/* --- .pjvm loader ----------------------------------------------------- */
static uint8_t pjvm_check_caps(void) {
    if ((uint32_t)n_methods > (uint32_t)PJVM_METHOD_CAP ||
        (uint32_t)n_classes > (uint32_t)PJVM_CLASS_CAP ||
        (uint32_t)n_static_fields > (uint32_t)PJVM_STATIC_CAP)
        return 0;
    return 1;
}

static void pjvm_parse_v3(uint8_t *data) {
    pjvm_format_v4 = 0;
    n_methods = data[2];
    main_mi = data[3];
    n_static_fields = RD16LE(data + 4);
    n_int_constants = data[6];
    n_classes = data[7];
    n_string_constants = data[8];
    region_flags = data[9];
    bytecodes_size = RD32LE(data + 10);
    if (!pjvm_check_caps()) {
        pjvm_platform_trap(PJVM_TRAP_CAPACITY, data[1]);
        return;
    }

    uint8_t *p = data + PJVM_HDR_SIZE_V3;
    uint32_t vo = 0;

    for (pjvm_class_id_t i = 0; i < n_classes; i++) {
        uint8_t pid = *p++;
        cls_pid[i] = pid == 0xFFu ? PJVM_NO_CLASS : pid;
        cls_nf[i] = *p++;
        cls_vs[i] = *p++;
        {
            uint8_t ci = *p++;
            cls_ci[i] = ci == 0xFFu ? PJVM_NO_CLINIT : ci;
        }
        if ((uint32_t)vo + cls_vs[i] > PJVM_VTABLE_CAP) {
            pjvm_platform_trap(PJVM_TRAP_CAPACITY, data[1]);
            return;
        }
        cls_vb[i] = (pjvm_count_t)vo;
        for (pjvm_count_t jj = 0; jj < cls_vs[i]; jj++)
            vt[vo++] = *p++;
        if (region_flags & PJVM_RF_REF_BITMAPS) {
            cls_rbo[i] = (pjvm_rbo_t)(p - data);
            p += (uint16_t)((cls_nf[i] + 7u) >> 3);
        } else {
            cls_rbo[i] = 0;
        }
    }

    for (pjvm_method_id_t i = 0; i < n_methods; i++) {
        m_ml[i] = p[0]; m_ac[i] = p[2]; m_fl[i] = p[3];
        m_co[i] = RD32LE(p + 4);
        m_cb[i] = RD16LE(p + 8);
        m_vs[i] = p[10] == 0xFFu ? PJVM_NO_VTABLE : p[10];
        m_vmid[i] = p[11] == 0xFFu ? PJVM_NO_VTABLE : p[11];
        m_ec[i] = p[12]; m_eo[i] = p[13]; p += PJVM_MT_ENTRY;
    }

    uint16_t cpc = RD16LE(p);
    p += 2;
    cpr_off = (uint32_t)(p - data); p += cpc;
    ic_off  = (uint32_t)(p - data); p += (uint16_t)n_int_constants * 4;
    sc_off  = (uint32_t)(p - data);
    for (pjvm_count_t i = 0; i < n_string_constants; i++) {
        uint16_t slen = RD16LE(p);
        p += 2 + slen;
    }
    bc_off = (uint32_t)(p - data); p += bytecodes_size;
    et_off = (uint32_t)(p - data);

    /* Skip past exception table and optional pin hints to find const_data */
    {
        /* Count exception entries: sum of m_ec[] */
        uint32_t n_exc = 0;
        for (pjvm_method_id_t i = 0; i < n_methods; i++) n_exc += m_ec[i];
        p += n_exc * PJVM_ET_ENTRY;

        /* Skip pin hints if present (1 byte per method) */
        if (region_flags & PJVM_RF_PIN_HINTS)
            p += n_methods;

        /* const_data section offset */
        if (region_flags & PJVM_RF_CONST_DATA)
            cd_off = (uint32_t)(p - data);
        else
            cd_off = 0;
    }

#if PJVM_USE_ASM_CPREAD
    cpr = data + cpr_off;
#endif
#if PJVM_USE_ASM_FETCH_HELPERS || PJVM_USE_ASM_CPREAD
    bc  = data + bc_off;
#endif
#if PJVM_USE_ASM_ROM_STRING_DATA || PJVM_USE_ASM_STRING_LEN || PJVM_USE_ASM_STRING_BYTE
    sc  = data + sc_off;
#endif
}

#if PJVM_ENABLE_V4
static uint32_t pjvm_read_uleb(uint8_t **pp) {
    uint32_t v = 0;
    uint8_t shift = 0;

    for (;;) {
        uint8_t b = *(*pp)++;
        v |= (uint32_t)(b & 0x7Fu) << shift;
        if ((b & 0x80u) == 0)
            return v;
        shift += 7;
    }
}

static void pjvm_parse_v4(uint8_t *data) {
    pjvm_format_v4 = 1;
    n_methods = RD16LE(data + 2);
    main_mi = RD16LE(data + 4);
    n_static_fields = RD16LE(data + 6);
    n_int_constants = RD16LE(data + 8);
    n_classes = RD16LE(data + 10);
    n_string_constants = RD16LE(data + 12);
    region_flags = (uint8_t)RD16LE(data + 14);
    bytecodes_size = RD32LE(data + 16);
    if (!pjvm_check_caps()) {
        pjvm_platform_trap(PJVM_TRAP_CAPACITY, data[1]);
        return;
    }

    uint8_t *p = data + PJVM_HDR_SIZE_V4;
    uint32_t vo = 0;

    for (pjvm_class_id_t i = 0; i < n_classes; i++) {
        uint16_t pid = RD16LE(p); p += 2;
        cls_pid[i] = pid == 0xFFFFu ? PJVM_NO_CLASS : pid;
        cls_nf[i] = RD16LE(p); p += 2;
        cls_vs[i] = RD16LE(p); p += 2;
        {
            uint16_t ci = RD16LE(p); p += 2;
            cls_ci[i] = ci == 0xFFFFu ? PJVM_NO_CLINIT : ci;
        }
        if ((uint32_t)vo + cls_vs[i] > PJVM_VTABLE_CAP) {
            pjvm_platform_trap(PJVM_TRAP_CAPACITY, data[1]);
            return;
        }
        cls_vb[i] = (pjvm_count_t)vo;
        for (pjvm_count_t jj = 0; jj < cls_vs[i]; jj++) {
            vt[vo++] = RD16LE(p);
            p += 2;
        }
        if (region_flags & PJVM_RF_REF_BITMAPS) {
            cls_rbo[i] = (pjvm_rbo_t)(p - data);
            p += (uint16_t)((cls_nf[i] + 7u) >> 3);
        } else {
            cls_rbo[i] = 0;
        }
    }

    if (region_flags & PJVM_RF_PACKED_METHOD_TABLE) {
        uint8_t *mt_end = p + 4 + RD32LE(p);
        uint32_t prev_code_offset = 0;
        uint32_t prev_cp_base = 0;
        uint16_t prev_exc_offset = 0;
        uint8_t have_prev_cp = 0;
        uint8_t have_prev_exc = 0;
        p += 4;

        for (pjvm_method_id_t i = 0; i < n_methods; i++) {
            uint32_t vs_code, vmid_code;
            m_ml[i] = (pjvm_count_t)pjvm_read_uleb(&p);
            (void)pjvm_read_uleb(&p); /* max_stack is not needed after parse */
            m_ac[i] = (pjvm_count_t)pjvm_read_uleb(&p);
            m_fl[i] = (pjvm_flags_t)pjvm_read_uleb(&p);
            vs_code = pjvm_read_uleb(&p);
            vmid_code = pjvm_read_uleb(&p);
            m_vs[i] = vs_code == 0 ? PJVM_NO_VTABLE : (pjvm_vslot_t)(vs_code - 1u);
            m_vmid[i] = vmid_code == 0 ? PJVM_NO_VTABLE : (pjvm_vmid_t)(vmid_code - 1u);

            if (m_fl[i] & 1) {
                m_co[i] = 0;
                m_cb[i] = 0;
                m_ec[i] = 0;
                m_eo[i] = 0;
            } else {
                uint32_t cp_code, exc_code;
                prev_code_offset += pjvm_read_uleb(&p);
                m_co[i] = prev_code_offset;

                cp_code = pjvm_read_uleb(&p);
                if (cp_code == 0 && have_prev_cp) {
                    m_cb[i] = (pjvm_cpbase_t)prev_cp_base;
                } else {
                    prev_cp_base = cp_code - 1u;
                    have_prev_cp = 1;
                    m_cb[i] = (pjvm_cpbase_t)prev_cp_base;
                }

                m_ec[i] = (pjvm_count_t)pjvm_read_uleb(&p);
                exc_code = pjvm_read_uleb(&p);
                if (exc_code == 0 && have_prev_exc) {
                    m_eo[i] = (pjvm_count_t)prev_exc_offset;
                } else {
                    prev_exc_offset = (uint16_t)(exc_code - 1u);
                    have_prev_exc = 1;
                    m_eo[i] = (pjvm_count_t)prev_exc_offset;
                }
            }
        }
        p = mt_end;
    } else {
        for (pjvm_method_id_t i = 0; i < n_methods; i++) {
            m_ml[i] = RD16LE(p);
            m_ac[i] = RD16LE(p + 4);
            m_fl[i] = RD16LE(p + 6);
            m_co[i] = RD32LE(p + 8);
            m_cb[i] = RD32LE(p + 12);
            {
                uint16_t vs = RD16LE(p + 16);
                uint16_t vmid = RD16LE(p + 18);
                m_vs[i] = vs == 0xFFFFu ? PJVM_NO_VTABLE : vs;
                m_vmid[i] = vmid == 0xFFFFu ? PJVM_NO_VTABLE : vmid;
            }
            m_ec[i] = RD16LE(p + 20);
            m_eo[i] = RD16LE(p + 22);
            p += PJVM_MT_ENTRY_V4;
        }
    }

    uint32_t cpc = RD32LE(p);
    p += 4;
    cpr_off = (uint32_t)(p - data); p += cpc;
    ic_off  = (uint32_t)(p - data); p += (uint32_t)n_int_constants * 4u;
    sc_off  = (uint32_t)(p - data);
    for (pjvm_count_t i = 0; i < n_string_constants; i++) {
        uint16_t slen = RD16LE(p);
        p += 2 + slen;
    }
    bc_off = (uint32_t)(p - data); p += bytecodes_size;
    et_off = (uint32_t)(p - data);

    {
        uint32_t n_exc = 0;
        for (pjvm_method_id_t i = 0; i < n_methods; i++) n_exc += m_ec[i];
        p += n_exc * PJVM_ET_ENTRY_V4;
        if (region_flags & PJVM_RF_PIN_HINTS)
            p += n_methods;
        cd_off = (region_flags & PJVM_RF_CONST_DATA) ? (uint32_t)(p - data) : 0;
    }

#if PJVM_USE_ASM_CPREAD
    cpr = data + cpr_off;
#endif
#if PJVM_USE_ASM_FETCH_HELPERS || PJVM_USE_ASM_CPREAD
    bc  = data + bc_off;
#endif
#if PJVM_USE_ASM_ROM_STRING_DATA || PJVM_USE_ASM_STRING_LEN || PJVM_USE_ASM_STRING_BYTE
    sc  = data + sc_off;
#endif
}
#endif

void pjvm_parse(uint8_t *data) {
    if (data[1] == PJVM_VERSION_V3) {
        pjvm_parse_v3(data);
        return;
    }
#if PJVM_ENABLE_V4
    if (data[1] == PJVM_VERSION_V4) {
        pjvm_parse_v4(data);
        return;
    }
#endif
    pjvm_platform_trap(PJVM_TRAP_BAD_VERSION, data[1]);
}

/* --- invoke / return -------------------------------------------------- */
static void pjvm_exec(void);

#if defined(PJVM_DEBUG_TOOLS) || defined(PJVM_BOUNDS_CHECK)
#define PJVM_CHECK_METHOD_ID(mi) do { \
    if ((uint32_t)(mi) >= (uint32_t)n_methods) { \
        pjvm_platform_trap(PJVM_TRAP_BAD_METHOD, (uint16_t)g_pjvm->pc); \
        return; \
    } \
} while (0)
#else
#define PJVM_CHECK_METHOD_ID(mi) do { } while (0)
#endif

static void pjvm_inv(pjvm_method_id_t mi) {
    uint16_t alo, ahi, blo, bhi;

    PJVM_CHECK_METHOD_ID(mi);

    if (m_fl[mi] & 1) {
        uint8_t nid = (uint8_t)(m_fl[mi] >> 1);
        switch (nid) {
        case NATIVE_PUTCHAR:
            alo = spop_lo();
            pjvm_platform_putchar((uint8_t)alo);
            break;
        case NATIVE_IN:
            spop_lo();
            spush(0, 0);
            break;
        case NATIVE_OUT: {
            uint16_t ov, op;
            SPOP_U16(ov);
            SPOP_U16(op);
            pjvm_platform_out(op, ov);
            break;
        }
        case NATIVE_PEEK:
            SPOP32(alo, ahi);
            spush(pjvm_platform_peek8((uint32_t)alo | ((uint32_t)ahi << 16)), 0);
            break;
        case NATIVE_POKE:
            blo = spop_lo();
            SPOP32(alo, ahi);
            pjvm_platform_poke8((uint32_t)alo | ((uint32_t)ahi << 16), (uint8_t)blo);
            break;
        case NATIVE_HALT:
            g_pjvm->fdepth = 0; g_pjvm->pc = PJVM_PC_HALT;
            break;
        case NATIVE_OBJECT_INIT:
            g_pjvm->sp--;
            break;
        case NATIVE_STR_LENGTH:
            SPOP32(alo, ahi);
            spush(pjvm_string_len(alo, ahi), 0);
            break;
        case NATIVE_STR_CHARAT:
            SPOP_U16(blo);
            SPOP32(alo, ahi);
            spush(pjvm_string_byte(alo, ahi, blo), 0);
            break;
        case NATIVE_STR_EQUALS: {
            SPOP32(blo, bhi);
            SPOP32(alo, ahi);
            if (alo == blo && ahi == bhi) { spush(1, 0); break; }
            if (blo == 0 && bhi == 0) { spush(0, 0); break; }
            uint16_t la = pjvm_string_len(alo, ahi), lb = pjvm_string_len(blo, bhi);
            uint8_t eq = (la == lb) ? 1 : 0;
            for (uint16_t i = 0; eq && i < la; i++)
                if (pjvm_string_byte(alo, ahi, i) != pjvm_string_byte(blo, bhi, i)) eq = 0;
            spush(eq, 0);
            break;
        }
        case NATIVE_STR_TOSTRING:
            break;
        case NATIVE_PRINT: {
            SPOP32(alo, ahi);
            uint16_t slen = pjvm_string_len(alo, ahi);
            for (uint16_t i = 0; i < slen; i++)
                pjvm_platform_putchar(pjvm_string_byte(alo, ahi, i));
            break;
        }
        case NATIVE_STR_HASHCODE: {
            SPOP32(alo, ahi);
            uint16_t slen = pjvm_string_len(alo, ahi);
            uint32_t h = 0;
            for (uint16_t i = 0; i < slen; i++)
                h = h * 31 + (uint32_t)pjvm_string_byte(alo, ahi, i);
            pjvm_push32((int32_t)h);
            break;
        }
        case NATIVE_ARRAYCOPY:
#if PJVM_USE_ASM_ARRAYCOPY
            pjvm_native_arraycopy();
#else
        {
            /* arraycopy(byte[] src, int srcOff, byte[] dst, int dstOff, int len)
             * Copies forward only — src and dst must not overlap with dst > src. */
            uint16_t len, dstOff, dst, srcOff, src;
            SPOP_U16(len);
            SPOP_U16(dstOff);
            dst = spop_lo();
            SPOP_U16(srcOff);
            src = spop_lo();
            for (uint16_t i = 0; i < len; i++)
                w8(dst + PJVM_OBJ_HEADER + dstOff + i, r8(src + PJVM_OBJ_HEADER + srcOff + i));
        }
#endif
            break;
        case NATIVE_MEMCMP:
#if PJVM_USE_ASM_MEMCMP
            pjvm_native_memcmp();
#else
        {
            /* memcmp(byte[] a, int aOff, byte[] b, int bOff, int len)
             * Returns <0, 0, or >0 (signed difference of first mismatch). */
            uint16_t len, bOff, bref, aOff, aref;
            SPOP_U16(len);
            SPOP_U16(bOff);
            bref = spop_lo();
            SPOP_U16(aOff);
            aref = spop_lo();
            int32_t result = 0;
            for (uint16_t i = 0; i < len; i++) {
                uint8_t av = r8(aref + PJVM_OBJ_HEADER + aOff + i);
                uint8_t bv = r8(bref + PJVM_OBJ_HEADER + bOff + i);
                if (av != bv) { result = (int32_t)av - (int32_t)bv; break; }
            }
            pjvm_push32(result);
        }
#endif
            break;
        case NATIVE_WRITE_BYTES:
#if PJVM_USE_ASM_WRITE_BYTES
            pjvm_native_write_bytes();
#else
        {
            /* writeBytes(byte[] buf, int off, int len) */
            uint16_t len, off, ref;
            SPOP_U16(len);
            SPOP_U16(off);
            ref = spop_lo();
            for (uint16_t i = 0; i < len; i++)
                pjvm_platform_putchar(r8(ref + PJVM_OBJ_HEADER + off + i));
        }
#endif
            break;
        case NATIVE_STRING_FROM_BYTES:
#if PJVM_USE_ASM_STRING_FROM_BYTES
            pjvm_native_string_from_bytes();
#else
        {
            /* new String(byte[] src, int off, int len) → String ref */
            uint16_t len, off, src;
            SPOP_U16(len);
            SPOP_U16(off);
            src = spop_lo();
            uint16_t a = heap_alloc(g_pjvm, (uint16_t)(PJVM_OBJ_HEADER + len),
                                    PJVM_HEAP_KIND_STRING);
            w16(a, len); w16((uint16_t)(a + 2), 0);
            for (uint16_t i = 0; i < len; i++)
                w8(a + PJVM_OBJ_HEADER + i, r8(src + PJVM_OBJ_HEADER + off + i));
            spush(a, 0);
        }
#endif
            break;
        case NATIVE_FILE_OPEN: {
            /* fileOpen(byte[] name, int nameLen, int mode) → int status */
            uint16_t mode, nameLen, nameRef;
            SPOP_U16(mode);
            SPOP_U16(nameLen);
            nameRef = spop_lo();
            uint8_t nameBuf[64];
            uint16_t nl = nameLen > 63 ? 63 : nameLen;
            for (uint16_t i = 0; i < nl; i++)
                nameBuf[i] = r8(nameRef + PJVM_OBJ_HEADER + i);
            nameBuf[nl] = 0;
            int32_t result = pjvm_platform_file_open(nameBuf, (uint8_t)nl, (uint8_t)mode);
            pjvm_push32(result);
            break;
        }
        case NATIVE_FILE_READ_BYTE: {
            /* fileReadByte() → int (-1 on EOF) */
            int32_t ch = pjvm_platform_file_read_byte();
            pjvm_push32(ch);
            break;
        }
        case NATIVE_FILE_WRITE_BYTE: {
            /* fileWriteByte(int b) */
            alo = spop_lo();
            pjvm_platform_file_write_byte((uint8_t)alo);
            break;
        }
        case NATIVE_FILE_READ: {
            /* fileRead(byte[] buf, int off, int len) → int bytesRead */
            uint16_t len, off, ref;
            SPOP_U16(len);
            SPOP_U16(off);
            ref = spop_lo();
            int32_t total = 0;
            for (uint16_t i = 0; i < len; i++) {
                int32_t ch = pjvm_platform_file_read_byte();
                if (ch < 0) break;
                w8(ref + PJVM_OBJ_HEADER + off + i, (uint8_t)ch);
                total++;
            }
            pjvm_push32(total);
            break;
        }
        case NATIVE_FILE_WRITE: {
            /* fileWrite(byte[] buf, int off, int len) */
            uint16_t len, off, ref;
            SPOP_U16(len);
            SPOP_U16(off);
            ref = spop_lo();
            for (uint16_t i = 0; i < len; i++)
                pjvm_platform_file_write_byte(r8(ref + PJVM_OBJ_HEADER + off + i));
            break;
        }
        case NATIVE_FILE_CLOSE: {
            /* fileClose(int mode) — 0=both, 1=read, 2=write */
            uint16_t cmode;
            SPOP_U16(cmode);
            pjvm_platform_file_close((uint8_t)cmode);
            break;
        }
        case NATIVE_FILE_DELETE: {
            /* fileDelete(byte[] name, int nameLen) → int status */
            uint16_t nameLen, nameRef;
            SPOP_U16(nameLen);
            nameRef = spop_lo();
            uint8_t nameBuf[64];
            uint16_t nl = nameLen > 63 ? 63 : nameLen;
            for (uint16_t i = 0; i < nl; i++)
                nameBuf[i] = r8(nameRef + PJVM_OBJ_HEADER + i);
            nameBuf[nl] = 0;
            int32_t result = pjvm_platform_file_delete(nameBuf, (uint8_t)nl);
            pjvm_push32(result);
            break;
        }
        case NATIVE_TERM_INFO: {
            uint16_t code;
            SPOP_U16(code);
            pjvm_push32(pjvm_platform_term_info(code));
            break;
        }
        case NATIVE_KEY_READ:
            pjvm_push32(pjvm_platform_key_read());
            break;
        case NATIVE_TICKS:
            pjvm_push32(pjvm_platform_ticks());
            break;
        case NATIVE_ENUM_INIT: {
            uint16_t obj, obj_hi, name_lo, name_hi, ord_lo, ord_hi;
            SPOP32(ord_lo, ord_hi);
            SPOP32(name_lo, name_hi);
            SPOP32(obj, obj_hi);
            if (obj_hi || obj == 0) { pjvm_platform_trap(OP_INVOKESPECIAL, g_pjvm->pc); break; }
            w16((uint16_t)(obj + PJVM_OBJ_HEADER), name_lo);
            w16((uint16_t)(obj + PJVM_OBJ_HEADER + 2), name_hi);
            w16((uint16_t)(obj + PJVM_OBJ_HEADER + 4), ord_lo);
            w16((uint16_t)(obj + PJVM_OBJ_HEADER + 6), ord_hi);
            break;
        }
        case NATIVE_ENUM_NAME:
        case NATIVE_ENUM_TOSTRING:
            SPOP32(alo, ahi);
            if (ahi || alo == 0) { pjvm_platform_trap(OP_INVOKEVIRTUAL, g_pjvm->pc); break; }
            spush(r16((uint16_t)(alo + PJVM_OBJ_HEADER)),
                  r16((uint16_t)(alo + PJVM_OBJ_HEADER + 2)));
            break;
        case NATIVE_ENUM_ORDINAL:
            SPOP32(alo, ahi);
            if (ahi || alo == 0) { pjvm_platform_trap(OP_INVOKEVIRTUAL, g_pjvm->pc); break; }
            spush(r16((uint16_t)(alo + PJVM_OBJ_HEADER + 4)),
                  r16((uint16_t)(alo + PJVM_OBJ_HEADER + 6)));
            break;
        case NATIVE_ENUM_VALUEOF:
            pjvm_platform_trap(OP_INVOKESTATIC, g_pjvm->pc);
            break;
        case NATIVE_ARRAY_CLONE: {
            uint16_t src, src_hi;
            SPOP32(src, src_hi);
            if (src_hi || src == 0) { pjvm_platform_trap(OP_INVOKEVIRTUAL, g_pjvm->pc); break; }
            uint16_t len = r16(src);
            uint16_t dst = heap_alloc(g_pjvm, (uint16_t)(PJVM_OBJ_HEADER + len * 4),
                                      PJVM_HEAP_KIND_REF_ARRAY);
            w16(dst, len);
            w16((uint16_t)(dst + 2), 0);
            for (uint16_t i = 0; i < len * 4; i++)
                w8((uint16_t)(dst + PJVM_OBJ_HEADER + i),
                   r8((uint16_t)(src + PJVM_OBJ_HEADER + i)));
            spush(dst, 0);
            break;
        }
        default:
            pjvm_platform_trap(PJVM_TRAP_BAD_NATIVE, g_pjvm->pc);
            break;
        }
        return;
    }

    PJVMFrame *f = &g_pjvm->frames[g_pjvm->fdepth];
    f->pc = g_pjvm->pc; f->mi = g_pjvm->cur_mi; f->lb = g_pjvm->cur_lb;
    f->so = (uint16_t)(g_pjvm->sp - m_ac[mi]); f->cb = g_pjvm->cur_cb;
    g_pjvm->fdepth++;
    if (g_pjvm->fdepth > g_pjvm->fdepth_max) g_pjvm->fdepth_max = (uint8_t)g_pjvm->fdepth;

    uint16_t nb = g_pjvm->lt;
    g_pjvm->lt += m_ml[mi];
    if (g_pjvm->lt > g_pjvm->lt_max) g_pjvm->lt_max = g_pjvm->lt;
#if PJVM_GC_ENABLED
    for (pjvm_count_t i = 0; i < m_ml[mi]; i++) {
        g_pjvm->loc_lo[nb + i] = 0;
        g_pjvm->loc_hi[nb + i] = 0;
    }
#endif
    for (int32_t i = (int32_t)m_ac[mi] - 1; i >= 0; i--) {
        g_pjvm->sp--;
        g_pjvm->loc_lo[nb + i] = g_pjvm->stk_lo[g_pjvm->sp];
        g_pjvm->loc_hi[nb + i] = g_pjvm->stk_hi[g_pjvm->sp];
    }
    g_pjvm->cur_mi = mi; g_pjvm->cur_lb = nb;
    g_pjvm->cur_cb = m_cb[mi]; g_pjvm->pc = m_co[mi];
}

static void pjvm_ret(uint8_t has_val) {
    uint16_t rlo = 0, rhi = 0;
    if (has_val) {
        SPOP32(rlo, rhi);
    }
    g_pjvm->lt = g_pjvm->cur_lb;
    g_pjvm->fdepth--;
    if (g_pjvm->fdepth < 0) {
        g_pjvm->pc = PJVM_PC_HALT;
        if (has_val) spush(rlo, rhi);
        return;
    }
    PJVMFrame *f = &g_pjvm->frames[g_pjvm->fdepth];
    g_pjvm->pc = f->pc; g_pjvm->cur_mi = f->mi; g_pjvm->cur_lb = f->lb;
    g_pjvm->cur_cb = f->cb; g_pjvm->sp = f->so;
    if (has_val) spush(rlo, rhi);
    pjvm_gc_maybe(g_pjvm, PJVM_GC_TRIG_RETURN, 0);
}

static void pjvm_throw(uint16_t exc_ref, uint32_t throw_pc) {
    pjvm_class_id_t ci = (pjvm_class_id_t)r16(exc_ref);

    for (;;) {
        pjvm_count_t count = m_ec[g_pjvm->cur_mi];
        pjvm_count_t base = m_eo[g_pjvm->cur_mi];
        uint32_t rel_pc = throw_pc - m_co[g_pjvm->cur_mi];

        for (pjvm_count_t i = 0; i < count; i++) {
            uint32_t eoff = et_off + (uint32_t)(base + i) *
                (pjvm_format_v4 ? PJVM_ET_ENTRY_V4 : PJVM_ET_ENTRY);
            uint16_t e_start = PROG16(eoff);
            uint16_t e_end   = PROG16(eoff + 2);
            uint16_t e_handler = PROG16(eoff + 4);
            pjvm_class_id_t e_catch;
#if PJVM_ENABLE_V4
            if (pjvm_format_v4)
                e_catch = PROG16(eoff + 6);
            else
#endif
                e_catch = PROG(eoff + 6) == 0xFFu ? PJVM_NO_CLASS : PROG(eoff + 6);

            if (rel_pc >= e_start && rel_pc < e_end) {
                uint8_t match = 0;
                if (e_catch == PJVM_NO_CLASS) {
                    match = 1;
                } else {
                    pjvm_class_id_t walk = ci;
                    while (walk != PJVM_NO_CLASS) {
                        if (walk == e_catch) { match = 1; break; }
                        walk = cls_pid[walk];
                    }
                }
                if (match) {
                    g_pjvm->sp = g_pjvm->fdepth > 0
                        ? g_pjvm->frames[g_pjvm->fdepth - 1].so
                        : 0;
                    spush(exc_ref, 0);
                    g_pjvm->pc = m_co[g_pjvm->cur_mi] + e_handler;
                    return;
                }
            }
        }

        g_pjvm->lt = g_pjvm->cur_lb;
        g_pjvm->fdepth--;
        if (g_pjvm->fdepth < 0) {
            pjvm_platform_trap(0xBF, throw_pc);
            g_pjvm->pc = PJVM_PC_HALT;
            return;
        }
        PJVMFrame *f = &g_pjvm->frames[g_pjvm->fdepth];
        throw_pc = f->pc - 1;
        g_pjvm->pc = f->pc; g_pjvm->cur_mi = f->mi; g_pjvm->cur_lb = f->lb;
        g_pjvm->cur_cb = f->cb; g_pjvm->sp = f->so;
    }
}

static uint16_t pjvm_multi_alloc(uint16_t *sizes, uint8_t depth, uint8_t dims) {
    uint16_t count = sizes[depth];
    uint16_t a = heap_alloc(g_pjvm, (uint16_t)(PJVM_OBJ_HEADER + count * 4),
                            PJVM_HEAP_KIND_REF_ARRAY);
    w16(a, count); w16((uint16_t)(a + 2), 0);
    if (depth + 1 < dims) {
        for (uint16_t i = 0; i < count; i++) {
            uint16_t inner = pjvm_multi_alloc(sizes, depth + 1, dims);
            w16(a + PJVM_OBJ_HEADER + i * 4, inner);
            w16((uint16_t)(a + PJVM_OBJ_HEADER + i * 4 + 2), 0);
        }
    }
    return a;
}

/* --- interpreter loop ------------------------------------------------- */
void pjvm_run(PJVMCtx *j) {
    g_pjvm = j;

    /* Pre-initialize static fields from const_data init table (ROM refs) */
    if (cd_off) {
        uint32_t p_cd = cd_off;
        uint16_t n_ca = PROG16(p_cd);
        p_cd += 2;
        /* Skip past array entries to reach init table */
        for (uint16_t i = 0; i < n_ca; i++) {
            uint16_t ne = PROG16(p_cd);
            uint8_t et = PROG(p_cd + 2);
            p_cd += 4; /* skip 4-byte header */
            uint16_t dsz = ne;
            if (et == PJVM_ELEM_CHAR || et == PJVM_ELEM_SHORT
#if PJVM_USE_CONST_STRING_ARRAYS
                || et == PJVM_ELEM_STRING_REF
#endif
            )
                dsz = (uint16_t)(ne * 2);
            else if (et == 3) dsz = (uint16_t)(ne * 4);
#if PJVM_USE_CONST_OBJECT_ARRAYS
            else if (et == PJVM_ELEM_OBJECT_REF) {
                uint16_t nf = PROG16(p_cd + 2);
                dsz = (uint16_t)(4 + ne * (uint16_t)(4 + nf * 4));
            }
#endif
            p_cd += dsz;
        }
        /* Read init table: n_init entries of (slot:2, lo:2, hi:2) */
        uint16_t n_init = PROG16(p_cd);
        p_cd += 2;
        for (uint16_t i = 0; i < n_init; i++) {
            uint16_t slot = PROG16(p_cd);
            uint16_t lo   = PROG16(p_cd + 2);
            uint16_t hi   = PROG16(p_cd + 4);
            j->sf_lo[slot] = lo;
            j->sf_hi[slot] = hi;
            p_cd += 6;
        }
    }

    j->sp_max = 0; j->lt_max = 0; j->fdepth_max = 0;

    /* Run static initializers (<clinit>) */
    for (pjvm_class_id_t ci = 0; ci < n_classes; ci++) {
        if (cls_ci[ci] == PJVM_NO_CLINIT) continue;
        pjvm_method_id_t mi = cls_ci[ci];
        j->cur_mi = mi; j->cur_lb = 0; j->cur_cb = m_cb[mi];
        j->lt = m_ml[mi]; j->pc = m_co[mi];
        j->fdepth = 0; j->sp = 0;
#if PJVM_GC_ENABLED
        for (pjvm_count_t i = 0; i < m_ml[mi]; i++) {
            j->loc_lo[i] = 0;
            j->loc_hi[i] = 0;
        }
#endif
        if (j->lt > j->lt_max) j->lt_max = j->lt;
        pjvm_exec();
    }

    /* Run main */
    j->cur_mi = main_mi; j->cur_lb = 0; j->cur_cb = m_cb[main_mi];
    j->lt = m_ml[main_mi]; j->pc = m_co[main_mi];
    j->fdepth = 0; j->sp = 0;
#if PJVM_GC_ENABLED
    for (pjvm_count_t i = 0; i < m_ml[main_mi]; i++) {
        j->loc_lo[i] = 0;
        j->loc_hi[i] = 0;
    }
#endif
    if (j->lt > j->lt_max) j->lt_max = j->lt;
    if (m_ac[main_mi] > 0) {
        uint16_t args_ref = pjvm_make_main_args(j);
        j->loc_lo[0] = args_ref;
        j->loc_hi[0] = 0;
    }
    pjvm_exec();
}

#ifdef PJVM_DEBUG_TOOLS
/* Host-only execution tracing and step limiting. */
uint32_t pjvm_step_limit;
uint8_t  pjvm_trace_enabled;
#define TRACE_BUF_SIZE 32
uint32_t trace_pc[TRACE_BUF_SIZE];
uint8_t  trace_op[TRACE_BUF_SIZE];
pjvm_method_id_t trace_mi[TRACE_BUF_SIZE];
uint8_t  trace_sp[TRACE_BUF_SIZE];
uint16_t trace_stk0[TRACE_BUF_SIZE];
uint32_t trace_idx;
#endif

/* --- opcode helper macros --------------------------------------------- */
/* Binary 32-bit op: pop b, pop a, push expr(a, b) */
#define BINOP32(expr) { \
    SPOP32(blo, bhi); \
    SPOP32(alo, ahi); \
    int32_t a = pjvm_to32(alo, ahi), b = pjvm_to32(blo, bhi); \
    pjvm_push32(expr); break; }

/* Shift op: pop shift amount (lo only), pop a, push expr(a, s) */
#define SHIFTOP(expr) { \
    blo = spop_lo(); \
    SPOP32(alo, ahi); \
    int32_t a = pjvm_to32(alo, ahi); uint8_t s = blo & 0x1F; \
    pjvm_push32(expr); break; }

/* Single-operand: pop one value, branch if cond(alo,ahi) is true */
#define BRANCH1(cond) { \
    int16_t o = bread(); \
    SPOP32(alo, ahi); \
    if (cond) g_pjvm->pc = opc + o; \
    break; }

/* Two-operand: pop b then a, branch if cond(alo,ahi,blo,bhi) is true */
#define BRANCH2(cond) { \
    int16_t o = bread(); \
    SPOP32(blo, bhi); \
    SPOP32(alo, ahi); \
    if (cond) g_pjvm->pc = opc + o; \
    break; }

static void pjvm_exec(void) {
#ifdef PJVM_DEBUG_TOOLS
    uint32_t steps = 0;
#endif
    while (g_pjvm->pc != PJVM_PC_HALT) {
#ifdef PJVM_DEBUG_TOOLS
        if (pjvm_step_limit && ++steps > pjvm_step_limit) {
            pjvm_platform_trap(PJVM_TRAP_STEP_LIMIT, g_pjvm->pc);
            return;
        }
#endif
        uint32_t opc = g_pjvm->pc;
        uint8_t op = BC(g_pjvm->pc++);
#ifdef PJVM_DEBUG_TOOLS
        if (pjvm_trace_enabled) {
            uint32_t ti = trace_idx % TRACE_BUF_SIZE;
            trace_pc[ti] = opc;
            trace_op[ti] = op;
            trace_mi[ti] = g_pjvm->cur_mi;
            trace_sp[ti] = g_pjvm->sp;
            trace_stk0[ti] = (g_pjvm->sp > 0) ? g_pjvm->stk_lo[g_pjvm->sp - 1] : 0xFFFF;
            trace_idx++;
        }
#endif
        uint16_t alo, ahi, blo, bhi;

        switch (op) {

        /* --- constants ------------------------------------------------ */
        case OP_NOP: break;
        case OP_ACONST_NULL: spush(0, 0); break;
        case OP_ICONST_M1:  spush(0xFFFF, 0xFFFF); break;
        case OP_ICONST_0:   spush(0, 0); break;
        case OP_ICONST_1:   spush(1, 0); break;
        case OP_ICONST_2:   spush(2, 0); break;
        case OP_ICONST_3:   spush(3, 0); break;
        case OP_ICONST_4:   spush(4, 0); break;
        case OP_ICONST_5:   spush(5, 0); break;

        case OP_BIPUSH: {
            int8_t v = (int8_t)bcread();
            spush((uint16_t)(int16_t)v, SIGN8_HI(v));
            break;
        }
        case OP_SIPUSH: {
            int16_t v = bread();
            spush((uint16_t)v, SIGN16_HI(v));
            break;
        }
        case OP_LDC: {
            uint8_t raw = bcread();
            uint32_t off = cpr_off + (uint32_t)g_pjvm->cur_cb + (uint32_t)raw * 2;
            uint16_t ci = PROG16(off);
            if (ci & PJVM_CP_STR_FLAG_16) {
                spush(ci & PJVM_CP_STR_MASK_16, PJVM_REF_ROM_STRING);
            } else {
                uint32_t base = ic_off + (uint32_t)ci * 4;
                spush(PROG16(base), PROG16(base + 2));
            }
            break;
        }
        case OP_LDC_W: {
            uint16_t ci = cpread();
            if (ci & PJVM_CP_STR_FLAG_16) {
                spush(ci & PJVM_CP_STR_MASK_16, PJVM_REF_ROM_STRING);
            } else {
                uint32_t base = ic_off + (uint32_t)ci * 4;
                spush(PROG16(base), PROG16(base + 2));
            }
            break;
        }

        /* --- loads & stores ----------------------------------------------- */
        case OP_ILOAD: case OP_ALOAD: lload(bcread()); break;
        case OP_ILOAD_0: case OP_ALOAD_0: lload(0); break;
        case OP_ILOAD_1: case OP_ALOAD_1: lload(1); break;
        case OP_ILOAD_2: case OP_ALOAD_2: lload(2); break;
        case OP_ILOAD_3: case OP_ALOAD_3: lload(3); break;

        /* --- array loads (heap + ROM) ------------------------------------- */
        case OP_IALOAD: case OP_AALOAD: {
            alo = spop_lo();
            SPOP32(blo, bhi);
            if (bhi) {
                /* ROM array: decode 32-bit offset, read via PROG() */
                uint32_t base = ROM_OFF(bhi, blo);
#if PJVM_USE_CONST_STRING_ARRAYS
                if (op == OP_AALOAD && PROG(base + 2) == PJVM_ELEM_STRING_REF) {
                    uint16_t s = PROG16(base + PJVM_OBJ_HEADER + (uint32_t)alo * 2);
                    if (s == PJVM_CONST_NULL_REF) spush(0, 0);
                    else spush(s, PJVM_REF_ROM_STRING);
                } else {
#endif
#if PJVM_USE_CONST_OBJECT_ARRAYS
                if (op == OP_AALOAD && PROG(base + 2) == PJVM_ELEM_OBJECT_REF) {
                    uint16_t nf = PROG16(base + 6);
                    uint32_t rec = base + 8 + (uint32_t)alo * (uint32_t)(4 + nf * 4);
                    uint16_t ci = PROG16(rec);
                    if (ci == PJVM_CONST_NULL_REF) spush(0, 0);
                    else spush((uint16_t)rec, PJVM_ROM_OBJECT_HI(rec));
                } else {
#endif
                    uint32_t off = base + PJVM_OBJ_HEADER + (uint32_t)alo * 4;
                    spush(PROG16(off), PROG16(off + 2));
#if PJVM_USE_CONST_OBJECT_ARRAYS
                }
#endif
#if PJVM_USE_CONST_STRING_ARRAYS
                }
#endif
            } else {
                uint16_t addr = blo + PJVM_OBJ_HEADER + alo * 4;
                spush(r16(addr), r16((uint16_t)(addr + 2)));
            }
            break;
        }
        case OP_BALOAD: {
            alo = spop_lo();
            SPOP32(blo, bhi);
            if (bhi) {
                int8_t bv = (int8_t)PROG(ROM_OFF(bhi, blo) + PJVM_OBJ_HEADER + alo);
                spush((uint16_t)(int16_t)bv, SIGN8_HI(bv));
            } else {
                int8_t bv = (int8_t)r8(blo + PJVM_OBJ_HEADER + alo);
                spush((uint16_t)(int16_t)bv, SIGN8_HI(bv));
            }
            break;
        }
        case OP_CALOAD: {
            alo = spop_lo();
            SPOP32(blo, bhi);
            if (bhi) {
                uint32_t off = ROM_OFF(bhi, blo) + PJVM_OBJ_HEADER + (uint32_t)alo * 2;
                spush(PROG16(off), 0);
            } else {
                spush(r16(blo + PJVM_OBJ_HEADER + alo * 2), 0);
            }
            break;
        }
        case OP_SALOAD: {
            alo = spop_lo();
            SPOP32(blo, bhi);
            if (bhi) {
                uint32_t off = ROM_OFF(bhi, blo) + PJVM_OBJ_HEADER + (uint32_t)alo * 2;
                uint16_t sv = PROG16(off);
                spush(sv, SIGN16_HI(sv));
            } else {
                uint16_t sv = r16(blo + PJVM_OBJ_HEADER + alo * 2);
                spush(sv, SIGN16_HI(sv));
            }
            break;
        }

        case OP_ISTORE: case OP_ASTORE: lstore(bcread()); break;
        case OP_ISTORE_0: case OP_ASTORE_0: lstore(0); break;
        case OP_ISTORE_1: case OP_ASTORE_1: lstore(1); break;
        case OP_ISTORE_2: case OP_ASTORE_2: lstore(2); break;
        case OP_ISTORE_3: case OP_ASTORE_3: lstore(3); break;

        /* --- array stores ------------------------------------------------- */
        case OP_IASTORE: case OP_AASTORE: {
            SPOP32(alo, ahi);
            blo = spop_lo();
            uint16_t aref, aref_hi;
            SPOP32(aref, aref_hi);
            if (aref_hi) { pjvm_platform_trap(op, (uint16_t)g_pjvm->pc); break; }
            #ifdef PJVM_BOUNDS_CHECK
            { uint16_t alen = r16(aref);
              if (blo >= alen) {
                fprintf(stderr, "BOUNDS | IASTORE oob: aref=%u index=%u len=%u mi=%u pc=%u\n",
                        (unsigned)aref, (unsigned)blo, (unsigned)alen,
                        (unsigned)g_pjvm->cur_mi, (unsigned)g_pjvm->pc);
              }
            }
            #endif
            uint16_t addr = aref + PJVM_OBJ_HEADER + blo * 4;
            w16(addr, alo); w16((uint16_t)(addr + 2), ahi);
            break;
        }
        case OP_BASTORE: {
            SPOP_U16(alo);
            blo = spop_lo();
            uint16_t aref, aref_hi;
            SPOP32(aref, aref_hi);
            if (aref_hi) { pjvm_platform_trap(op, (uint16_t)g_pjvm->pc); break; }
            #ifdef PJVM_BOUNDS_CHECK
            { uint16_t alen = r16(aref);
              if (blo >= alen) {
                fprintf(stderr, "BOUNDS | BASTORE oob: aref=%u index=%u len=%u mi=%u pc=%u\n",
                        (unsigned)aref, (unsigned)blo, (unsigned)alen,
                        (unsigned)g_pjvm->cur_mi, (unsigned)g_pjvm->pc);
              }
            }
            #endif
            w8(aref + PJVM_OBJ_HEADER + blo, (uint8_t)alo);
            break;
        }
        case OP_CASTORE: case OP_SASTORE: {
            SPOP_U16(alo);
            blo = spop_lo();
            uint16_t aref, aref_hi;
            SPOP32(aref, aref_hi);
            if (aref_hi) { pjvm_platform_trap(op, (uint16_t)g_pjvm->pc); break; }
            w16(aref + PJVM_OBJ_HEADER + blo * 2, alo);
            break;
        }

        /* --- stack manipulation ------------------------------------------- */
        case OP_POP: g_pjvm->sp--; break;
#if PJVM_USE_OP_POP2
        case OP_POP2: g_pjvm->sp -= 2; break;
#endif
        case OP_DUP: {
            uint16_t t = g_pjvm->sp - 1;
            spush(g_pjvm->stk_lo[t], g_pjvm->stk_hi[t]);
            break;
        }
#if PJVM_USE_OP_DUP_X1
        case OP_DUP_X1: {
            uint16_t s1 = g_pjvm->sp - 1, s2 = g_pjvm->sp - 2;
            uint16_t v1l = g_pjvm->stk_lo[s1], v1h = g_pjvm->stk_hi[s1];
            g_pjvm->stk_lo[s1] = g_pjvm->stk_lo[s2]; g_pjvm->stk_hi[s1] = g_pjvm->stk_hi[s2];
            g_pjvm->stk_lo[s2] = v1l; g_pjvm->stk_hi[s2] = v1h;
            spush(v1l, v1h);
            break;
        }
#endif
        case OP_DUP_X2: {
            uint16_t s1 = g_pjvm->sp - 1, s2 = g_pjvm->sp - 2, s3 = g_pjvm->sp - 3;
            uint16_t v1l = g_pjvm->stk_lo[s1], v1h = g_pjvm->stk_hi[s1];
            g_pjvm->stk_lo[s1] = g_pjvm->stk_lo[s2]; g_pjvm->stk_hi[s1] = g_pjvm->stk_hi[s2];
            g_pjvm->stk_lo[s2] = g_pjvm->stk_lo[s3]; g_pjvm->stk_hi[s2] = g_pjvm->stk_hi[s3];
            g_pjvm->stk_lo[s3] = v1l; g_pjvm->stk_hi[s3] = v1h;
            spush(v1l, v1h);
            break;
        }
        case OP_DUP2: {
            uint16_t s1 = g_pjvm->sp - 1, s2 = g_pjvm->sp - 2;
            spush(g_pjvm->stk_lo[s2], g_pjvm->stk_hi[s2]);
            spush(g_pjvm->stk_lo[s1], g_pjvm->stk_hi[s1]);
            break;
        }
#if PJVM_USE_OP_SWAP
        case OP_SWAP: {
            uint16_t t = g_pjvm->sp - 1, u = g_pjvm->sp - 2;
            alo = g_pjvm->stk_lo[t]; ahi = g_pjvm->stk_hi[t];
            g_pjvm->stk_lo[t] = g_pjvm->stk_lo[u]; g_pjvm->stk_hi[t] = g_pjvm->stk_hi[u];
            g_pjvm->stk_lo[u] = alo; g_pjvm->stk_hi[u] = ahi;
            break;
        }
#endif

        /* --- arithmetic --------------------------------------------------- */
        case OP_IADD: {
            SPOP32(blo, bhi);
            SPOP32(alo, ahi);
            uint16_t rlo = alo + blo;
            spush(rlo, ahi + bhi + (rlo < alo ? 1 : 0));
            break;
        }
        case OP_ISUB: {
            SPOP32(blo, bhi);
            SPOP32(alo, ahi);
            uint16_t rlo = alo - blo;
            spush(rlo, ahi - bhi - (alo < blo ? 1 : 0));
            break;
        }
        case OP_INEG: {
            SPOP32(alo, ahi);
            uint16_t rlo = ~alo + 1;
            spush(rlo, ~ahi + (rlo == 0 ? 1 : 0));
            break;
        }
        case OP_IAND:
            SPOP32(blo, bhi);
            SPOP32(alo, ahi);
            spush(alo & blo, ahi & bhi); break;
        case OP_IOR:
            SPOP32(blo, bhi);
            SPOP32(alo, ahi);
            spush(alo | blo, ahi | bhi); break;
        case OP_IXOR:
            SPOP32(blo, bhi);
            SPOP32(alo, ahi);
            spush(alo ^ blo, ahi ^ bhi); break;

        case OP_IINC: {
            uint8_t idx = bcread();
            int8_t v = (int8_t)bcread();
            uint16_t i = g_pjvm->cur_lb + idx;
            uint16_t old = g_pjvm->loc_lo[i];
            uint16_t inc = (uint16_t)(int16_t)v;
            uint16_t nlo = old + inc;
            uint16_t carry = (nlo < old) ? 1 : 0;
            g_pjvm->loc_lo[i] = nlo;
            g_pjvm->loc_hi[i] += SIGN8_HI(v) + carry;
            break;
        }

#if PJVM_USE_OP_WIDE
        case OP_WIDE: {
            uint8_t wop = bcread();
            uint16_t idx = (uint16_t)bread();
            if (wop == OP_ILOAD || wop == OP_ALOAD) {
                lload_w(idx);
            } else if (wop == OP_ISTORE || wop == OP_ASTORE) {
                lstore_w(idx);
            } else if (wop == OP_IINC) {
                int16_t v = bread();
                uint16_t i = g_pjvm->cur_lb + idx;
                uint16_t old = g_pjvm->loc_lo[i];
                uint16_t inc = (uint16_t)v;
                uint16_t nlo = old + inc;
                uint16_t carry = (nlo < old) ? 1 : 0;
                g_pjvm->loc_lo[i] = nlo;
                g_pjvm->loc_hi[i] += SIGN16_HI(v) + carry;
            } else {
                pjvm_platform_trap(op, opc);
                return;
            }
            break;
        }
#endif

        case OP_IMUL:  BINOP32(a * b)
        case OP_IDIV:  BINOP32(a / b)
        case OP_IREM:  BINOP32(a % b)
        case OP_ISHL:  SHIFTOP(a << s)
        case OP_ISHR:  SHIFTOP(a >> s)
        case OP_IUSHR: SHIFTOP((int32_t)((uint32_t)a >> s))

        /* --- conversions -------------------------------------------------- */
        case OP_I2B: {
            alo = spop_lo();
            int8_t v = (int8_t)alo;
            spush((uint16_t)(int16_t)v, SIGN8_HI(v)); break;
        }
        case OP_I2C:
            alo = spop_lo();
            spush(alo, 0); break;
        case OP_I2S:
            alo = spop_lo();
            spush(alo, SIGN16_HI(alo)); break;

        /* --- branches ----------------------------------------------------- */
        case OP_IFEQ:      BRANCH1(alo == 0 && ahi == 0)
        case OP_IFNE:      BRANCH1(alo != 0 || ahi != 0)
#if PJVM_USE_OP_IFLT
        case OP_IFLT:      BRANCH1(ahi & 0x8000)
#endif
#if PJVM_USE_OP_IFGE
        case OP_IFGE:      BRANCH1(!(ahi & 0x8000))
#endif
#if PJVM_USE_OP_IFGT
        case OP_IFGT:      BRANCH1(!(ahi & 0x8000) && (alo | ahi))
#endif
#if PJVM_USE_OP_IFLE
        case OP_IFLE:      BRANCH1((ahi & 0x8000) || (alo == 0 && ahi == 0))
#endif
#if PJVM_USE_OP_IFNULL
        case OP_IFNULL:    BRANCH1(alo == 0 && ahi == 0)
#endif
#if PJVM_USE_OP_IFNONNULL
        case OP_IFNONNULL: BRANCH1(alo != 0 || ahi != 0)
#endif

        case OP_IF_ICMPEQ: BRANCH2(alo == blo && ahi == bhi)
        case OP_IF_ICMPNE: BRANCH2(alo != blo || ahi != bhi)
        case OP_IF_ICMPLT: BRANCH2((int16_t)ahi < (int16_t)bhi || (ahi == bhi && alo < blo))
        case OP_IF_ICMPGE: BRANCH2((int16_t)ahi > (int16_t)bhi || (ahi == bhi && alo >= blo))
        case OP_IF_ICMPGT: BRANCH2((int16_t)ahi > (int16_t)bhi || (ahi == bhi && alo > blo))
        case OP_IF_ICMPLE: BRANCH2((int16_t)ahi < (int16_t)bhi || (ahi == bhi && alo <= blo))

        case OP_IF_ACMPEQ: {
            int16_t o = bread();
            SPOP32(blo, bhi);
            SPOP32(alo, ahi);
            if (alo == blo && ahi == bhi) g_pjvm->pc = opc + o; break;
        }
        case OP_IF_ACMPNE: {
            int16_t o = bread();
            SPOP32(blo, bhi);
            SPOP32(alo, ahi);
            if (alo != blo || ahi != bhi) g_pjvm->pc = opc + o; break;
        }
        case OP_GOTO: {
            int16_t o = bread();
            g_pjvm->pc = opc + o; break;
        }

        /* --- switches ----------------------------------------------------- */
        case OP_TABLESWITCH: {
            uint32_t base = m_co[g_pjvm->cur_mi];
            g_pjvm->pc = base + (((g_pjvm->pc - base) + 3) & ~3u);
            g_pjvm->pc += 2; int16_t def_off = bread();
            g_pjvm->pc += 2; int16_t low_lo = bread();
            g_pjvm->pc += 2; int16_t high_lo = bread();
            SPOP_U16(alo);
            int16_t val = (int16_t)alo;
            if (val >= low_lo && val <= high_lo) {
                uint16_t idx = (uint16_t)(val - low_lo);
                g_pjvm->pc += idx * 4 + 2;
                int16_t off = bread();
                g_pjvm->pc = opc + off;
            } else {
                g_pjvm->pc = opc + def_off;
            }
            break;
        }
        case OP_LOOKUPSWITCH: {
            uint32_t base = m_co[g_pjvm->cur_mi];
            g_pjvm->pc = base + (((g_pjvm->pc - base) + 3) & ~3u);
            g_pjvm->pc += 2; int16_t def_off = bread();
            g_pjvm->pc += 2; int16_t npairs = bread();
            SPOP32(alo, ahi);
            uint8_t v0 = (uint8_t)(ahi >> 8), v1 = (uint8_t)ahi,
                    v2 = (uint8_t)(alo >> 8), v3 = (uint8_t)alo;
            uint8_t found = 0;
            for (int16_t i = 0; i < npairs; i++) {
                uint8_t m0 = BC(g_pjvm->pc), m1 = BC(g_pjvm->pc+1),
                        m2 = BC(g_pjvm->pc+2), m3 = BC(g_pjvm->pc+3);
                g_pjvm->pc += 4;
                g_pjvm->pc += 2; int16_t off = bread();
                if (!found && m0==v0 && m1==v1 && m2==v2 && m3==v3) {
                    g_pjvm->pc = opc + off;
                    found = 1;
                    break;
                }
            }
            if (!found) g_pjvm->pc = opc + def_off;
            break;
        }

        /* --- return ------------------------------------------------------- */
        case OP_IRETURN: case OP_ARETURN: pjvm_ret(1); break;
        case OP_RETURN: pjvm_ret(0); break;

        /* --- fields ------------------------------------------------------- */
        case OP_GETSTATIC: {
            uint16_t s = cpread();
            spush(g_pjvm->sf_lo[s], g_pjvm->sf_hi[s]); break;
        }
        case OP_PUTSTATIC: {
            uint16_t s = cpread();
            SPOP32(alo, ahi);
#ifdef PJVM_TRACE_STATIC
            if (s == PJVM_TRACE_STATIC) {
                fprintf(stderr, "PUTSTATIC sf[%u] = %u (hi=%u) mi=%u pc=%u\n",
                    (unsigned)s, (unsigned)alo, (unsigned)ahi,
                    (unsigned)g_pjvm->cur_mi, (unsigned)g_pjvm->pc);
            }
#endif
            g_pjvm->sf_lo[s] = alo; g_pjvm->sf_hi[s] = ahi; break;
        }

        case OP_GETFIELD: {
            uint16_t s = cpread();
#if PJVM_USE_CONST_OBJECT_ARRAYS
            SPOP32(alo, ahi);
            if (PJVM_REF_IS_ROM_OBJECT(ahi)) {
                uint32_t addr = PJVM_ROM_OBJECT_OFF(ahi, alo) + PJVM_OBJ_HEADER + (uint32_t)s * 4;
                spush(PROG16(addr), PROG16(addr + 2));
            } else {
                uint16_t addr = alo + PJVM_OBJ_HEADER + s * 4;
                spush(r16(addr), r16((uint16_t)(addr + 2)));
            }
#else
            alo = spop_lo();
            uint16_t addr = alo + PJVM_OBJ_HEADER + s * 4;
            spush(r16(addr), r16((uint16_t)(addr + 2)));
#endif
            break;
        }
        case OP_PUTFIELD: {
            uint16_t s = cpread();
            SPOP32(alo, ahi);
#if PJVM_USE_CONST_OBJECT_ARRAYS
            SPOP32(blo, bhi);
            if (PJVM_REF_IS_ROM_OBJECT(bhi)) {
                pjvm_platform_trap(OP_PUTFIELD, opc);
                break;
            }
#else
            blo = spop_lo();
#endif
            uint16_t addr = blo + PJVM_OBJ_HEADER + s * 4;
            w16(addr, alo); w16((uint16_t)(addr + 2), ahi); break;
        }

        /* --- invocation --------------------------------------------------- */
        case OP_INVOKESTATIC: case OP_INVOKESPECIAL: {
            pjvm_method_id_t mi = cpread();
            pjvm_inv(mi); break;
        }
        case OP_INVOKEVIRTUAL: {
            pjvm_method_id_t bmi = cpread();
            PJVM_CHECK_METHOD_ID(bmi);
            pjvm_vslot_t vs = m_vs[bmi];
            if (vs == PJVM_NO_VTABLE) { pjvm_inv(bmi); }
            else {
                uint16_t argi = (uint16_t)(g_pjvm->sp - m_ac[bmi]);
#if PJVM_USE_CONST_OBJECT_ARRAYS
                pjvm_class_id_t ci = pjvm_ref_class_id(
                    g_pjvm->stk_lo[argi], g_pjvm->stk_hi[argi]);
#else
                pjvm_class_id_t ci = (pjvm_class_id_t)r16(g_pjvm->stk_lo[argi]);
#endif
                pjvm_inv(vt[cls_vb[ci] + vs]);
            }
            break;
        }
        case OP_INVOKEINTERFACE: {
            pjvm_method_id_t bmi = cpread();
            bcread(); bcread();
            PJVM_CHECK_METHOD_ID(bmi);
            pjvm_vmid_t vid = m_vmid[bmi];
            uint16_t argi = (uint16_t)(g_pjvm->sp - m_ac[bmi]);
#if PJVM_USE_CONST_OBJECT_ARRAYS
            pjvm_class_id_t ci = pjvm_ref_class_id(
                g_pjvm->stk_lo[argi], g_pjvm->stk_hi[argi]);
#else
            pjvm_class_id_t ci = (pjvm_class_id_t)r16(g_pjvm->stk_lo[argi]);
#endif
            pjvm_method_id_t found = PJVM_NO_VTABLE;
            for (pjvm_count_t k = 0; k < cls_vs[ci]; k++) {
                if (m_vmid[vt[cls_vb[ci] + k]] == vid) {
                    found = vt[cls_vb[ci] + k]; break;
                }
            }
            if (found != PJVM_NO_VTABLE) pjvm_inv(found);
            else pjvm_platform_trap(0xB9, g_pjvm->pc);
            break;
        }

        /* --- object creation & type --------------------------------------- */
        case OP_NEW: {
            pjvm_class_id_t ci = cpread();
            pjvm_count_t nf = ci < n_classes ? cls_nf[ci] : 0;
            uint16_t a = heap_alloc(g_pjvm, (uint16_t)(PJVM_OBJ_HEADER + nf * 4),
                                    PJVM_HEAP_KIND_OBJECT);
            w16(a, ci); w16((uint16_t)(a + 2), 0);
            spush(a, 0); break;
        }
        case OP_NEWARRAY: {
            uint8_t atype = bcread();
            alo = spop_lo();
            uint8_t esz = 4;
            uint8_t kind = PJVM_HEAP_KIND_INT_ARRAY;
            if (atype == 4 || atype == 8) esz = 1;
            else if (atype == 5 || atype == 9) esz = 2;
            if (esz == 1) kind = PJVM_HEAP_KIND_BYTE_ARRAY;
            else if (esz == 2) kind = PJVM_HEAP_KIND_SHORT_ARRAY;
            uint16_t a = heap_alloc(g_pjvm, (uint16_t)(PJVM_OBJ_HEADER + alo * esz), kind);
            w16(a, alo); w16((uint16_t)(a + 2), 0);
            spush(a, 0); break;
        }
        case OP_ANEWARRAY: {
            g_pjvm->pc += 2;
            alo = spop_lo();
            uint16_t a = heap_alloc(g_pjvm, (uint16_t)(PJVM_OBJ_HEADER + alo * 4),
                                    PJVM_HEAP_KIND_REF_ARRAY);
            w16(a, alo); w16((uint16_t)(a + 2), 0);
            spush(a, 0); break;
        }
        case OP_ARRAYLENGTH:
            SPOP32(alo, ahi);
            if (ahi) {
                spush(PROG16(ROM_OFF(ahi, alo)), 0);
            } else {
                spush(r16(alo), r16((uint16_t)(alo + 2)));
            }
            break;

        case OP_MULTIANEWARRAY: {
            g_pjvm->pc += 2;
            uint8_t ndims = bcread();
            uint16_t sizes[4];
            for (uint8_t d = ndims; d > 0; d--)
                sizes[d-1] = spop_lo();
            spush(pjvm_multi_alloc(sizes, 0, ndims), 0);
            break;
        }

        case OP_CHECKCAST: {
            pjvm_class_id_t tci = cpread();
            alo = g_pjvm->stk_lo[g_pjvm->sp - 1];
#if PJVM_USE_CONST_OBJECT_ARRAYS
            ahi = g_pjvm->stk_hi[g_pjvm->sp - 1];
            if (alo != 0 || ahi != 0) {
                pjvm_class_id_t ci = pjvm_ref_class_id(alo, ahi);
#else
            if (alo != 0) {
                pjvm_class_id_t ci = (pjvm_class_id_t)r16(alo);
#endif
                uint8_t ok = 0;
                while (ci != PJVM_NO_CLASS) {
                    if (ci == tci) { ok = 1; break; }
                    ci = cls_pid[ci];
                }
                if (!ok) pjvm_platform_trap(op, opc);
            }
            break;
        }
        case OP_INSTANCEOF: {
            pjvm_class_id_t tci = cpread();
#if PJVM_USE_CONST_OBJECT_ARRAYS
            SPOP32(alo, ahi);
            if (alo == 0 && ahi == 0) { spush(0, 0); }
            else {
                pjvm_class_id_t ci = pjvm_ref_class_id(alo, ahi);
#else
            SPOP_U16(alo);
            if (alo == 0) { spush(0, 0); }
            else {
                pjvm_class_id_t ci = (pjvm_class_id_t)r16(alo);
#endif
                uint8_t match = 0;
                while (ci != PJVM_NO_CLASS) {
                    if (ci == tci) { match = 1; break; }
                    ci = cls_pid[ci];
                }
                spush(match, 0);
            }
            break;
        }

        /* --- exceptions --------------------------------------------------- */
        case OP_ATHROW: {
            uint16_t exc_ref;
            SPOP_U16(exc_ref);
            if (exc_ref == 0) {
                pjvm_platform_trap(0xBF, opc);
                return;
            }
            pjvm_throw(exc_ref, opc);
            break;
        }

        default:
            pjvm_platform_trap(op, opc);
            return;
        }
    }
}
