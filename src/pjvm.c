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
};

/* --- globals (extern-declared in pjvm.h) ------------------------------ */
uint8_t *pjvm_prog;
uint32_t pjvm_prog_size;
uint8_t  n_methods, main_mi, n_classes;
uint32_t bytecodes_size;
uint32_t bc_off, cpr_off, ic_off, sc_off, et_off, cd_off;
PJVMCtx *g_pjvm;

#ifdef PJVM_ASM_HELPERS
uint8_t *cpr;
uint8_t *bc;
#endif

/* --- internal globals (file-scope) ------------------------------------ */
static uint16_t n_static_fields;
static uint8_t  n_int_constants, n_string_constants;
static uint8_t  cp16;          /* 0 = v1/v2 (8-bit CP), 1 = v3 (16-bit CP) */
static uint8_t  region_flags;  /* v3 byte 9: bit0=pin_hints, bit2=const_data */
static uint16_t cp_str_flag;   /* 0x80 (v1/v2) or 0x8000 (v3) */
static uint16_t cp_str_mask;   /* 0x7F (v1/v2) or 0x7FFF (v3) */
static uint8_t  m_ml[PJVM_METHOD_CAP], m_ac[PJVM_METHOD_CAP];
static uint8_t  m_fl[PJVM_METHOD_CAP], m_vs[PJVM_METHOD_CAP], m_vmid[PJVM_METHOD_CAP];
static uint8_t  m_ec[PJVM_METHOD_CAP], m_eo[PJVM_METHOD_CAP];
static uint32_t m_co[PJVM_METHOD_CAP];
static uint16_t m_cb[PJVM_METHOD_CAP];
static uint8_t  cls_pid[PJVM_CLASS_CAP], cls_nf[PJVM_CLASS_CAP];
static uint8_t  cls_vb[PJVM_CLASS_CAP], cls_vs[PJVM_CLASS_CAP], cls_ci[PJVM_CLASS_CAP];
static uint8_t  vt[PJVM_VTABLE_CAP];
static uint16_t str_refs[128];

/* --- program image access macro --------------------------------------- */
#ifdef PJVM_PAGED
static uint8_t prog_fetch(uint32_t offset);
#define PROG(off) prog_fetch(off)
#else
#define PROG(off) pjvm_prog[(off)]
#endif
#define BC(a) PROG(bc_off + (a))

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

/* --- noinline helpers for code size ----------------------------------- */

#ifdef PJVM_ASM_HELPERS
/* Provided by i8085_helpers.S */
extern void     spush(uint16_t lo, uint16_t hi);
extern uint16_t spop_lo(void);
extern uint16_t spop_hi(void);
extern void     lload(uint8_t slot);
extern void     lstore(uint8_t slot);
extern uint8_t  bcread(void);
extern int16_t  bread(void);
/* cpread is NOT in ASM — v3 format needs cp16 branching (see below) */
/* Native handler ASM implementations */
extern void     pjvm_native_arraycopy(void);
extern void     pjvm_native_memcmp(void);
extern void     pjvm_native_write_bytes(void);
extern void     pjvm_native_string_from_bytes(void);
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

NI static uint8_t bcread(void) {
    return BC(g_pjvm->pc++);
}

NI static int16_t bread(void) {
    int16_t o = (int16_t)((BC(g_pjvm->pc) << 8) | BC(g_pjvm->pc + 1));
    g_pjvm->pc += 2;
    return o;
}

#endif /* !PJVM_ASM_HELPERS */

/* cpread always in C — v3 format needs cp16 branching that ASM doesn't handle */
NI static uint16_t cpread(void) {
    uint16_t idx = (BC(g_pjvm->pc) << 8) | BC(g_pjvm->pc + 1);
    g_pjvm->pc += 2;
    if (cp16) {
        uint32_t off = cpr_off + (uint32_t)g_pjvm->cur_cb + (uint32_t)idx * 2;
        return (uint16_t)PROG(off) | ((uint16_t)PROG(off + 1) << 8);
    }
    return PROG(cpr_off + g_pjvm->cur_cb + idx);
}

static int32_t pjvm_to32(uint16_t lo, uint16_t hi) {
    return (int32_t)((uint32_t)lo | ((uint32_t)hi << 16));
}

NI static void pjvm_push32(int32_t v) {
    spush((uint16_t)v, (uint16_t)((uint32_t)v >> 16));
}

static uint16_t pjvm_make_string(PJVMCtx *j, const uint8_t *buf, uint16_t len) {
    uint16_t a = heap_alloc(j, (uint16_t)(4 + len));
    w16(a, len);
    w16((uint16_t)(a + 2), 0);
    for (uint16_t i = 0; i < len; i++) w8((uint16_t)(a + 4 + i), buf[i]);
    return a;
}

static uint16_t pjvm_make_main_args(PJVMCtx *j) {
    uint16_t argc = j->prog_argc;
    uint16_t a = heap_alloc(j, (uint16_t)(4 + argc * 4));
    w16(a, argc);
    w16((uint16_t)(a + 2), 0);
    for (uint16_t i = 0; i < argc; i++) {
        const char *arg = j->prog_argv ? j->prog_argv[i] : 0;
        uint16_t len = 0;
        if (arg != 0) while (arg[len] != 0) len++;
        uint16_t sref = pjvm_make_string(j, (const uint8_t *)arg, len);
        w16((uint16_t)(a + 4 + i * 4), sref);
        w16((uint16_t)(a + 4 + i * 4 + 2), 0);
    }
    return a;
}

/* --- .pjvm loader ----------------------------------------------------- */
void pjvm_parse(uint8_t *data) {
    uint8_t version = data[1];  /* 0x4A = v1, 0x4B = v2, 0x4C = v3 */
    n_methods = data[2];
    main_mi = data[3];

    uint8_t *p;
    if (version >= 0x4C) {
        /* v3: 16-bit n_static_fields, 16-bit CP entries */
        n_static_fields = (uint16_t)data[4] | ((uint16_t)data[5] << 8);
        n_int_constants = data[6];
        n_classes = data[7];
        n_string_constants = data[8];
        region_flags = data[9];
        bytecodes_size = (uint32_t)data[10] | ((uint32_t)data[11] << 8)
                       | ((uint32_t)data[12] << 16) | ((uint32_t)data[13] << 24);
        cp16 = 1; cp_str_flag = 0x8000; cp_str_mask = 0x7FFF;
        p = data + 16;
    } else {
        n_static_fields = data[4];
        n_int_constants = data[5];
        n_classes = data[6];
        n_string_constants = data[7];
        cp16 = 0; cp_str_flag = 0x80; cp_str_mask = 0x7F;
        if (version == 0x4B) {
            bytecodes_size = (uint32_t)data[8] | ((uint32_t)data[9] << 8)
                           | ((uint32_t)data[10] << 16) | ((uint32_t)data[11] << 24);
            p = data + 14;
        } else {
            bytecodes_size = (uint32_t)((uint16_t)data[8] | ((uint16_t)data[9] << 8));
            p = data + 10;
        }
    }

    uint8_t vo = 0;

    for (uint8_t i = 0; i < n_classes; i++) {
        cls_pid[i] = *p++;
        cls_nf[i] = *p++;
        cls_vs[i] = *p++;
        cls_ci[i] = *p++;
        cls_vb[i] = vo;
        for (uint8_t jj = 0; jj < cls_vs[i]; jj++)
            vt[vo++] = *p++;
    }

    for (uint8_t i = 0; i < n_methods; i++) {
        m_ml[i] = p[0]; m_ac[i] = p[2]; m_fl[i] = p[3];
        if (version >= 0x4B) {
            m_co[i] = (uint32_t)((uint32_t)p[4] | ((uint32_t)p[5] << 8)
                     | ((uint32_t)p[6] << 16) | ((uint32_t)p[7] << 24));
            m_cb[i] = (uint16_t)p[8] | ((uint16_t)p[9] << 8);
            m_vs[i] = p[10]; m_vmid[i] = p[11];
            m_ec[i] = p[12]; m_eo[i] = p[13]; p += 14;
        } else {
            m_co[i] = (uint32_t)((uint16_t)p[4] | ((uint16_t)p[5] << 8));
            m_cb[i] = (uint16_t)p[6] | ((uint16_t)p[7] << 8);
            m_vs[i] = p[8]; m_vmid[i] = p[9];
            m_ec[i] = p[10]; m_eo[i] = p[11]; p += 12;
        }
    }

    uint16_t cpc = (uint16_t)p[0] | ((uint16_t)p[1] << 8);
    p += 2;
    cpr_off = (uint32_t)(p - data); p += cpc;
    ic_off  = (uint32_t)(p - data); p += (uint16_t)n_int_constants * 4;
    sc_off  = (uint32_t)(p - data);
    for (uint8_t i = 0; i < n_string_constants; i++) {
        uint16_t slen = (uint16_t)p[0] | ((uint16_t)p[1] << 8);
        p += 2 + slen;
    }
    bc_off = (uint32_t)(p - data); p += bytecodes_size;
    et_off = (uint32_t)(p - data);

    /* Skip past exception table and optional pin hints to find const_data */
    {
        /* Count exception entries: sum of m_ec[] */
        uint16_t n_exc = 0;
        for (uint8_t i = 0; i < n_methods; i++) n_exc += m_ec[i];
        p += n_exc * 7;  /* 7 bytes per exception entry */

        /* Skip pin hints if present (1 byte per method) */
        if (region_flags & 0x01)
            p += n_methods;

        /* const_data section offset */
        if (region_flags & 0x04)
            cd_off = (uint32_t)(p - data);
        else
            cd_off = 0;
    }

#ifdef PJVM_ASM_HELPERS
    cpr = data + cpr_off;
    bc  = data + bc_off;
#endif
}

/* --- invoke / return -------------------------------------------------- */
static void pjvm_exec(void);

static void pjvm_inv(uint8_t mi) {
    uint16_t alo, ahi, blo;

    if (m_fl[mi] & 1) {
        uint8_t nid = m_fl[mi] >> 1;
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
            uint16_t ov = spop_lo(); spop_hi();
            uint16_t op = spop_lo(); spop_hi();
            pjvm_platform_out(op, ov);
            break;
        }
        case NATIVE_PEEK:
            alo = spop_lo(); ahi = spop_hi();
            spush(pjvm_platform_peek8((uint32_t)alo | ((uint32_t)ahi << 16)), 0);
            break;
        case NATIVE_POKE:
            blo = spop_lo();
            alo = spop_lo(); ahi = spop_hi();
            pjvm_platform_poke8((uint32_t)alo | ((uint32_t)ahi << 16), (uint8_t)blo);
            break;
        case NATIVE_HALT:
            g_pjvm->fdepth = 0; g_pjvm->pc = PJVM_PC_HALT;
            break;
        case NATIVE_OBJECT_INIT:
            g_pjvm->sp--;
            break;
        case NATIVE_STR_LENGTH:
            alo = spop_lo();
            spush(r16(alo), r16((uint16_t)(alo + 2)));
            break;
        case NATIVE_STR_CHARAT:
            blo = spop_lo(); spop_hi();
            alo = spop_lo();
            spush(r8(alo + 4 + blo), 0);
            break;
        case NATIVE_STR_EQUALS: {
            blo = spop_lo(); spop_hi();
            alo = spop_lo();
            if (alo == blo) { spush(1, 0); break; }
            if (blo == 0) { spush(0, 0); break; }
            uint16_t la = r16(alo), lb = r16(blo);
            uint8_t eq = (la == lb) ? 1 : 0;
            for (uint16_t i = 0; eq && i < la; i++)
                if (r8(alo + 4 + i) != r8(blo + 4 + i)) eq = 0;
            spush(eq, 0);
            break;
        }
        case NATIVE_STR_TOSTRING:
            break;
        case NATIVE_PRINT: {
            alo = spop_lo();
            uint16_t slen = r16(alo);
            for (uint16_t i = 0; i < slen; i++)
                pjvm_platform_putchar(r8(alo + 4 + i));
            break;
        }
        case NATIVE_STR_HASHCODE: {
            alo = spop_lo();
            uint16_t slen = r16(alo);
            uint32_t h = 0;
            for (uint16_t i = 0; i < slen; i++)
                h = h * 31 + (uint32_t)r8(alo + 4 + i);
            pjvm_push32((int32_t)h);
            break;
        }
        case NATIVE_ARRAYCOPY:
#ifdef PJVM_ASM_HELPERS
            pjvm_native_arraycopy();
#else
        {
            /* arraycopy(byte[] src, int srcOff, byte[] dst, int dstOff, int len)
             * Copies forward only — src and dst must not overlap with dst > src. */
            uint16_t len    = spop_lo(); spop_hi();
            uint16_t dstOff = spop_lo(); spop_hi();
            uint16_t dst    = spop_lo();
            uint16_t srcOff = spop_lo(); spop_hi();
            uint16_t src    = spop_lo();
            for (uint16_t i = 0; i < len; i++)
                w8(dst + 4 + dstOff + i, r8(src + 4 + srcOff + i));
        }
#endif
            break;
        case NATIVE_MEMCMP:
#ifdef PJVM_ASM_HELPERS
            pjvm_native_memcmp();
#else
        {
            /* memcmp(byte[] a, int aOff, byte[] b, int bOff, int len)
             * Returns <0, 0, or >0 (signed difference of first mismatch). */
            uint16_t len  = spop_lo(); spop_hi();
            uint16_t bOff = spop_lo(); spop_hi();
            uint16_t bref = spop_lo();
            uint16_t aOff = spop_lo(); spop_hi();
            uint16_t aref = spop_lo();
            int32_t result = 0;
            for (uint16_t i = 0; i < len; i++) {
                uint8_t av = r8(aref + 4 + aOff + i);
                uint8_t bv = r8(bref + 4 + bOff + i);
                if (av != bv) { result = (int32_t)av - (int32_t)bv; break; }
            }
            pjvm_push32(result);
        }
#endif
            break;
        case NATIVE_WRITE_BYTES:
#ifdef PJVM_ASM_HELPERS
            pjvm_native_write_bytes();
#else
        {
            /* writeBytes(byte[] buf, int off, int len) */
            uint16_t len = spop_lo(); spop_hi();
            uint16_t off = spop_lo(); spop_hi();
            uint16_t ref = spop_lo();
            for (uint16_t i = 0; i < len; i++)
                pjvm_platform_putchar(r8(ref + 4 + off + i));
        }
#endif
            break;
        case NATIVE_STRING_FROM_BYTES:
#ifdef PJVM_ASM_HELPERS
            pjvm_native_string_from_bytes();
#else
        {
            /* new String(byte[] src, int off, int len) → String ref */
            uint16_t len = spop_lo(); spop_hi();
            uint16_t off = spop_lo(); spop_hi();
            uint16_t src = spop_lo();
            uint16_t a = heap_alloc(g_pjvm, (uint16_t)(4 + len));
            w16(a, len); w16((uint16_t)(a + 2), 0);
            for (uint16_t i = 0; i < len; i++)
                w8(a + 4 + i, r8(src + 4 + off + i));
            spush(a, 0);
        }
#endif
            break;
        case NATIVE_FILE_OPEN: {
            /* fileOpen(byte[] name, int nameLen, int mode) → int status */
            uint16_t mode    = spop_lo(); spop_hi();
            uint16_t nameLen = spop_lo(); spop_hi();
            uint16_t nameRef = spop_lo();
            uint8_t nameBuf[64];
            uint16_t nl = nameLen > 63 ? 63 : nameLen;
            for (uint16_t i = 0; i < nl; i++)
                nameBuf[i] = r8(nameRef + 4 + i);
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
            uint16_t len = spop_lo(); spop_hi();
            uint16_t off = spop_lo(); spop_hi();
            uint16_t ref = spop_lo();
            int32_t total = 0;
            for (uint16_t i = 0; i < len; i++) {
                int32_t ch = pjvm_platform_file_read_byte();
                if (ch < 0) break;
                w8(ref + 4 + off + i, (uint8_t)ch);
                total++;
            }
            pjvm_push32(total);
            break;
        }
        case NATIVE_FILE_WRITE: {
            /* fileWrite(byte[] buf, int off, int len) */
            uint16_t len = spop_lo(); spop_hi();
            uint16_t off = spop_lo(); spop_hi();
            uint16_t ref = spop_lo();
            for (uint16_t i = 0; i < len; i++)
                pjvm_platform_file_write_byte(r8(ref + 4 + off + i));
            break;
        }
        case NATIVE_FILE_CLOSE: {
            /* fileClose(int mode) — 0=both, 1=read, 2=write */
            uint16_t cmode = spop_lo(); spop_hi();
            pjvm_platform_file_close((uint8_t)cmode);
            break;
        }
        case NATIVE_FILE_DELETE: {
            /* fileDelete(byte[] name, int nameLen) → int status */
            uint16_t nameLen = spop_lo(); spop_hi();
            uint16_t nameRef = spop_lo();
            uint8_t nameBuf[64];
            uint16_t nl = nameLen > 63 ? 63 : nameLen;
            for (uint16_t i = 0; i < nl; i++)
                nameBuf[i] = r8(nameRef + 4 + i);
            nameBuf[nl] = 0;
            int32_t result = pjvm_platform_file_delete(nameBuf, (uint8_t)nl);
            pjvm_push32(result);
            break;
        }
        default:
            pjvm_platform_trap(0xFF, g_pjvm->pc);
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
    for (int8_t i = m_ac[mi] - 1; i >= 0; i--) {
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
        rlo = spop_lo(); rhi = spop_hi();
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
}

static void pjvm_throw(uint16_t exc_ref, uint32_t throw_pc) {
    uint8_t ci = (uint8_t)r16(exc_ref);

    for (;;) {
        uint8_t count = m_ec[g_pjvm->cur_mi];
        uint8_t base = m_eo[g_pjvm->cur_mi];
        uint32_t rel_pc = throw_pc - m_co[g_pjvm->cur_mi];

        for (uint8_t i = 0; i < count; i++) {
            uint32_t eoff = et_off + (uint16_t)(base + i) * 7;
            uint16_t e_start = (uint16_t)PROG(eoff) | ((uint16_t)PROG(eoff + 1) << 8);
            uint16_t e_end   = (uint16_t)PROG(eoff + 2) | ((uint16_t)PROG(eoff + 3) << 8);
            uint16_t e_handler = (uint16_t)PROG(eoff + 4) | ((uint16_t)PROG(eoff + 5) << 8);
            uint8_t  e_catch  = PROG(eoff + 6);

            if (rel_pc >= e_start && rel_pc < e_end) {
                uint8_t match = 0;
                if (e_catch == 0xFF) {
                    match = 1;
                } else {
                    uint8_t walk = ci;
                    while (walk != 0xFF) {
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
    uint16_t a = heap_alloc(g_pjvm, (uint16_t)(4 + count * 4));
    w16(a, count); w16((uint16_t)(a + 2), 0);
    if (depth + 1 < dims) {
        for (uint16_t i = 0; i < count; i++) {
            uint16_t inner = pjvm_multi_alloc(sizes, depth + 1, dims);
            w16(a + 4 + i * 4, inner);
            w16((uint16_t)(a + 4 + i * 4 + 2), 0);
        }
    }
    return a;
}

/* --- interpreter loop ------------------------------------------------- */
void pjvm_run(PJVMCtx *j) {
    g_pjvm = j;

    /* Pre-allocate interned string constants into heap */
    {
        uint32_t soff = sc_off;
        for (uint8_t i = 0; i < n_string_constants; i++) {
            uint16_t slen = (uint16_t)PROG(soff) | ((uint16_t)PROG(soff + 1) << 8);
            soff += 2;
            uint16_t a = heap_alloc(j, (uint16_t)(4 + slen));
            w16(a, slen); w16((uint16_t)(a + 2), 0);
            for (uint16_t k = 0; k < slen; k++) w8(a + 4 + k, PROG(soff + k));
            soff += slen;
            str_refs[i] = a;
        }
    }

    /* Pre-initialize static fields from const_data init table (ROM refs) */
    if (cd_off) {
        uint32_t p_cd = cd_off;
        uint16_t n_ca = (uint16_t)PROG(p_cd) | ((uint16_t)PROG(p_cd + 1) << 8);
        p_cd += 2;
        /* Skip past array entries to reach init table */
        for (uint16_t i = 0; i < n_ca; i++) {
            uint16_t ne = (uint16_t)PROG(p_cd) | ((uint16_t)PROG(p_cd + 1) << 8);
            uint8_t et = PROG(p_cd + 2);
            p_cd += 4; /* skip 4-byte header */
            uint16_t dsz = ne;
            if (et == 1 || et == 2) dsz = (uint16_t)(ne * 2);
            else if (et == 3) dsz = (uint16_t)(ne * 4);
            p_cd += dsz;
        }
        /* Read init table: n_init entries of (slot:2, lo:2, hi:2) */
        uint16_t n_init = (uint16_t)PROG(p_cd) | ((uint16_t)PROG(p_cd + 1) << 8);
        p_cd += 2;
        for (uint16_t i = 0; i < n_init; i++) {
            uint16_t slot = (uint16_t)PROG(p_cd) | ((uint16_t)PROG(p_cd + 1) << 8);
            uint16_t lo   = (uint16_t)PROG(p_cd + 2) | ((uint16_t)PROG(p_cd + 3) << 8);
            uint16_t hi   = (uint16_t)PROG(p_cd + 4) | ((uint16_t)PROG(p_cd + 5) << 8);
            j->sf_lo[slot] = lo;
            j->sf_hi[slot] = hi;
            p_cd += 6;
        }
    }

    j->sp_max = 0; j->lt_max = 0; j->fdepth_max = 0;

    /* Run static initializers (<clinit>) */
    for (uint8_t ci = 0; ci < n_classes; ci++) {
        if (cls_ci[ci] == 0xFF) continue;
        uint8_t mi = cls_ci[ci];
        j->cur_mi = mi; j->cur_lb = 0; j->cur_cb = m_cb[mi];
        j->lt = m_ml[mi]; j->pc = m_co[mi];
        j->fdepth = 0; j->sp = 0;
        if (j->lt > j->lt_max) j->lt_max = j->lt;
        pjvm_exec();
    }

    /* Run main */
    j->cur_mi = main_mi; j->cur_lb = 0; j->cur_cb = m_cb[main_mi];
    j->lt = m_ml[main_mi]; j->pc = m_co[main_mi];
    j->fdepth = 0; j->sp = 0;
    if (j->lt > j->lt_max) j->lt_max = j->lt;
    if (m_ac[main_mi] > 0) {
        uint16_t args_ref = pjvm_make_main_args(j);
        j->loc_lo[0] = args_ref;
        j->loc_hi[0] = 0;
    }
    pjvm_exec();
}

uint32_t pjvm_step_limit;
uint8_t  pjvm_trace_enabled;
#define TRACE_BUF_SIZE 32
uint32_t trace_pc[TRACE_BUF_SIZE];
uint8_t  trace_op[TRACE_BUF_SIZE];
uint8_t  trace_mi[TRACE_BUF_SIZE];
uint8_t  trace_sp[TRACE_BUF_SIZE];
uint16_t trace_stk0[TRACE_BUF_SIZE];
uint32_t trace_idx;

static void pjvm_exec(void) {
    uint32_t steps = 0;
    while (g_pjvm->pc != PJVM_PC_HALT) {
        if (pjvm_step_limit && ++steps > pjvm_step_limit) {
            pjvm_platform_trap(0xFE, g_pjvm->pc);
            return;
        }
        uint32_t opc = g_pjvm->pc;
        uint8_t op = BC(g_pjvm->pc++);
        if (pjvm_trace_enabled) {
            uint32_t ti = trace_idx % TRACE_BUF_SIZE;
            trace_pc[ti] = opc;
            trace_op[ti] = op;
            trace_mi[ti] = g_pjvm->cur_mi;
            trace_sp[ti] = g_pjvm->sp;
            trace_stk0[ti] = (g_pjvm->sp > 0) ? g_pjvm->stk_lo[g_pjvm->sp - 1] : 0xFFFF;
            trace_idx++;
        }
        uint16_t alo, ahi, blo, bhi;

        switch (op) {
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
            spush((uint16_t)(int16_t)v, v < 0 ? 0xFFFF : 0);
            break;
        }
        case OP_SIPUSH: {
            int16_t v = bread();
            spush((uint16_t)v, v < 0 ? 0xFFFF : 0);
            break;
        }
        case OP_LDC: {
            uint16_t ci;
            uint8_t raw = bcread();
            if (cp16) {
                uint32_t off = cpr_off + (uint32_t)g_pjvm->cur_cb + (uint32_t)raw * 2;
                ci = (uint16_t)PROG(off) | ((uint16_t)PROG(off + 1) << 8);
            } else {
                ci = PROG(cpr_off + g_pjvm->cur_cb + raw);
            }
            if (ci & cp_str_flag) {
                spush(str_refs[ci & cp_str_mask], 0);
            } else {
                uint32_t base = ic_off + (uint32_t)ci * 4;
                spush((uint16_t)PROG(base) | ((uint16_t)PROG(base + 1) << 8),
                      (uint16_t)PROG(base + 2) | ((uint16_t)PROG(base + 3) << 8));
            }
            break;
        }
        case OP_LDC_W: {
            uint16_t ci = cpread();
            if (ci & cp_str_flag) {
                spush(str_refs[ci & cp_str_mask], 0);
            } else {
                uint32_t base = ic_off + (uint32_t)ci * 4;
                spush((uint16_t)PROG(base) | ((uint16_t)PROG(base + 1) << 8),
                      (uint16_t)PROG(base + 2) | ((uint16_t)PROG(base + 3) << 8));
            }
            break;
        }

        case OP_ILOAD: case OP_ALOAD: lload(bcread()); break;
        case OP_ILOAD_0: case OP_ALOAD_0: lload(0); break;
        case OP_ILOAD_1: case OP_ALOAD_1: lload(1); break;
        case OP_ILOAD_2: case OP_ALOAD_2: lload(2); break;
        case OP_ILOAD_3: case OP_ALOAD_3: lload(3); break;

        case OP_IALOAD: case OP_AALOAD: {
            alo = spop_lo();
            blo = spop_lo(); bhi = spop_hi();
            if (bhi) {
                /* ROM array: decode 32-bit offset, read via PROG() */
                uint32_t off = ((uint32_t)(bhi - 1) << 16) | blo;
                off += 4 + (uint32_t)alo * 4;
                uint16_t vlo = (uint16_t)PROG(off) | ((uint16_t)PROG(off + 1) << 8);
                uint16_t vhi = (uint16_t)PROG(off + 2) | ((uint16_t)PROG(off + 3) << 8);
                spush(vlo, vhi);
            } else {
                uint16_t addr = blo + 4 + alo * 4;
                spush(r16(addr), r16((uint16_t)(addr + 2)));
            }
            break;
        }
        case OP_BALOAD: {
            alo = spop_lo();
            blo = spop_lo(); bhi = spop_hi();
            if (bhi) {
                uint32_t off = ((uint32_t)(bhi - 1) << 16) | blo;
                int8_t bv = (int8_t)PROG(off + 4 + alo);
                spush((uint16_t)(int16_t)bv, bv < 0 ? 0xFFFF : 0);
            } else {
                int8_t bv = (int8_t)r8(blo + 4 + alo);
                spush((uint16_t)(int16_t)bv, bv < 0 ? 0xFFFF : 0);
            }
            break;
        }
        case OP_CALOAD: {
            alo = spop_lo();
            blo = spop_lo(); bhi = spop_hi();
            if (bhi) {
                uint32_t off = ((uint32_t)(bhi - 1) << 16) | blo;
                off += 4 + (uint32_t)alo * 2;
                spush((uint16_t)PROG(off) | ((uint16_t)PROG(off + 1) << 8), 0);
            } else {
                spush(r16(blo + 4 + alo * 2), 0);
            }
            break;
        }
        case OP_SALOAD: {
            alo = spop_lo();
            blo = spop_lo(); bhi = spop_hi();
            if (bhi) {
                uint32_t off = ((uint32_t)(bhi - 1) << 16) | blo;
                off += 4 + (uint32_t)alo * 2;
                uint16_t sv = (uint16_t)PROG(off) | ((uint16_t)PROG(off + 1) << 8);
                spush(sv, (int16_t)sv < 0 ? 0xFFFF : 0);
            } else {
                uint16_t sv = r16(blo + 4 + alo * 2);
                spush(sv, (int16_t)sv < 0 ? 0xFFFF : 0);
            }
            break;
        }

        case OP_ISTORE: case OP_ASTORE: lstore(bcread()); break;
        case OP_ISTORE_0: case OP_ASTORE_0: lstore(0); break;
        case OP_ISTORE_1: case OP_ASTORE_1: lstore(1); break;
        case OP_ISTORE_2: case OP_ASTORE_2: lstore(2); break;
        case OP_ISTORE_3: case OP_ASTORE_3: lstore(3); break;

        case OP_IASTORE: case OP_AASTORE: {
            alo = spop_lo(); ahi = spop_hi();
            blo = spop_lo();
            uint16_t aref = spop_lo(); uint16_t aref_hi = spop_hi();
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
            uint16_t addr = aref + 4 + blo * 4;
            w16(addr, alo); w16((uint16_t)(addr + 2), ahi);
            break;
        }
        case OP_BASTORE: {
            alo = spop_lo(); spop_hi();
            blo = spop_lo();
            uint16_t aref = spop_lo(); uint16_t aref_hi = spop_hi();
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
            w8(aref + 4 + blo, (uint8_t)alo);
            break;
        }
        case OP_CASTORE: case OP_SASTORE: {
            alo = spop_lo(); spop_hi();
            blo = spop_lo();
            uint16_t aref = spop_lo(); uint16_t aref_hi = spop_hi();
            if (aref_hi) { pjvm_platform_trap(op, (uint16_t)g_pjvm->pc); break; }
            w16(aref + 4 + blo * 2, alo);
            break;
        }

        case OP_POP: g_pjvm->sp--; break;
        case OP_POP2: g_pjvm->sp -= 2; break;
        case OP_DUP: {
            uint16_t t = g_pjvm->sp - 1;
            spush(g_pjvm->stk_lo[t], g_pjvm->stk_hi[t]);
            break;
        }
        case OP_DUP_X1: {
            uint16_t s1 = g_pjvm->sp - 1, s2 = g_pjvm->sp - 2;
            uint16_t v1l = g_pjvm->stk_lo[s1], v1h = g_pjvm->stk_hi[s1];
            g_pjvm->stk_lo[s1] = g_pjvm->stk_lo[s2]; g_pjvm->stk_hi[s1] = g_pjvm->stk_hi[s2];
            g_pjvm->stk_lo[s2] = v1l; g_pjvm->stk_hi[s2] = v1h;
            spush(v1l, v1h);
            break;
        }
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
        case OP_SWAP: {
            uint16_t t = g_pjvm->sp - 1, u = g_pjvm->sp - 2;
            alo = g_pjvm->stk_lo[t]; ahi = g_pjvm->stk_hi[t];
            g_pjvm->stk_lo[t] = g_pjvm->stk_lo[u]; g_pjvm->stk_hi[t] = g_pjvm->stk_hi[u];
            g_pjvm->stk_lo[u] = alo; g_pjvm->stk_hi[u] = ahi;
            break;
        }

        case OP_IADD: {
            blo = spop_lo(); bhi = spop_hi();
            alo = spop_lo(); ahi = spop_hi();
            uint16_t rlo = alo + blo;
            spush(rlo, ahi + bhi + (rlo < alo ? 1 : 0));
            break;
        }
        case OP_ISUB: {
            blo = spop_lo(); bhi = spop_hi();
            alo = spop_lo(); ahi = spop_hi();
            uint16_t rlo = alo - blo;
            spush(rlo, ahi - bhi - (alo < blo ? 1 : 0));
            break;
        }
        case OP_INEG: {
            alo = spop_lo(); ahi = spop_hi();
            uint16_t rlo = ~alo + 1;
            spush(rlo, ~ahi + (rlo == 0 ? 1 : 0));
            break;
        }
        case OP_IAND:
            blo = spop_lo(); bhi = spop_hi();
            alo = spop_lo(); ahi = spop_hi();
            spush(alo & blo, ahi & bhi); break;
        case OP_IOR:
            blo = spop_lo(); bhi = spop_hi();
            alo = spop_lo(); ahi = spop_hi();
            spush(alo | blo, ahi | bhi); break;
        case OP_IXOR:
            blo = spop_lo(); bhi = spop_hi();
            alo = spop_lo(); ahi = spop_hi();
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
            g_pjvm->loc_hi[i] += (v < 0 ? 0xFFFF : 0) + carry;
            break;
        }

        case OP_IMUL:
            blo = spop_lo(); bhi = spop_hi();
            alo = spop_lo(); ahi = spop_hi();
            pjvm_push32(pjvm_to32(alo, ahi) * pjvm_to32(blo, bhi)); break;
        case OP_IDIV:
            blo = spop_lo(); bhi = spop_hi();
            alo = spop_lo(); ahi = spop_hi();
            pjvm_push32(pjvm_to32(alo, ahi) / pjvm_to32(blo, bhi)); break;
        case OP_IREM:
            blo = spop_lo(); bhi = spop_hi();
            alo = spop_lo(); ahi = spop_hi();
            pjvm_push32(pjvm_to32(alo, ahi) % pjvm_to32(blo, bhi)); break;
        case OP_ISHL:
            blo = spop_lo();
            alo = spop_lo(); ahi = spop_hi();
            pjvm_push32(pjvm_to32(alo, ahi) << (blo & 0x1F)); break;
        case OP_ISHR:
            blo = spop_lo();
            alo = spop_lo(); ahi = spop_hi();
            pjvm_push32(pjvm_to32(alo, ahi) >> (blo & 0x1F)); break;
        case OP_IUSHR:
            blo = spop_lo();
            alo = spop_lo(); ahi = spop_hi();
            pjvm_push32((int32_t)((uint32_t)pjvm_to32(alo, ahi) >> (blo & 0x1F))); break;

        case OP_I2B: {
            alo = spop_lo();
            int8_t v = (int8_t)alo;
            spush((uint16_t)(int16_t)v, v < 0 ? 0xFFFF : 0); break;
        }
        case OP_I2C:
            alo = spop_lo();
            spush(alo, 0); break;
        case OP_I2S:
            alo = spop_lo();
            spush(alo, (int16_t)alo < 0 ? 0xFFFF : 0); break;

        case OP_IFEQ: {
            int16_t o = bread();
            alo = spop_lo(); ahi = spop_hi();
            if (alo == 0 && ahi == 0) g_pjvm->pc = opc + o;
            break;
        }
        case OP_IFNE: {
            int16_t o = bread();
            alo = spop_lo(); ahi = spop_hi();
            if (alo != 0 || ahi != 0) g_pjvm->pc = opc + o;
            break;
        }
        case OP_IFLT: {
            int16_t o = bread();
            spop_lo(); ahi = spop_hi();
            if (ahi & 0x8000) g_pjvm->pc = opc + o;
            break;
        }
        case OP_IFGE: {
            int16_t o = bread();
            spop_lo(); ahi = spop_hi();
            if (!(ahi & 0x8000)) g_pjvm->pc = opc + o;
            break;
        }
        case OP_IFGT: {
            int16_t o = bread();
            alo = spop_lo(); ahi = spop_hi();
            if (!(ahi & 0x8000) && (alo | ahi)) g_pjvm->pc = opc + o;
            break;
        }
        case OP_IFLE: {
            int16_t o = bread();
            alo = spop_lo(); ahi = spop_hi();
            if ((ahi & 0x8000) || (alo == 0 && ahi == 0)) g_pjvm->pc = opc + o;
            break;
        }
        case OP_IFNULL: {
            int16_t o = bread();
            alo = spop_lo(); ahi = spop_hi();
            if (alo == 0 && ahi == 0) g_pjvm->pc = opc + o;
            break;
        }
        case OP_IFNONNULL: {
            int16_t o = bread();
            alo = spop_lo(); ahi = spop_hi();
            if (alo != 0 || ahi != 0) g_pjvm->pc = opc + o;
            break;
        }

        case OP_IF_ICMPEQ: {
            int16_t o = bread();
            blo = spop_lo(); bhi = spop_hi();
            alo = spop_lo(); ahi = spop_hi();
            if (alo == blo && ahi == bhi) g_pjvm->pc = opc + o; break;
        }
        case OP_IF_ICMPNE: {
            int16_t o = bread();
            blo = spop_lo(); bhi = spop_hi();
            alo = spop_lo(); ahi = spop_hi();
            if (alo != blo || ahi != bhi) g_pjvm->pc = opc + o; break;
        }
        case OP_IF_ICMPLT: {
            int16_t o = bread();
            blo = spop_lo(); bhi = spop_hi();
            alo = spop_lo(); ahi = spop_hi();
            if ((int16_t)ahi < (int16_t)bhi || (ahi == bhi && alo < blo))
                g_pjvm->pc = opc + o; break;
        }
        case OP_IF_ICMPGE: {
            int16_t o = bread();
            blo = spop_lo(); bhi = spop_hi();
            alo = spop_lo(); ahi = spop_hi();
            if ((int16_t)ahi > (int16_t)bhi || (ahi == bhi && alo >= blo))
                g_pjvm->pc = opc + o; break;
        }
        case OP_IF_ICMPGT: {
            int16_t o = bread();
            blo = spop_lo(); bhi = spop_hi();
            alo = spop_lo(); ahi = spop_hi();
            if ((int16_t)ahi > (int16_t)bhi || (ahi == bhi && alo > blo))
                g_pjvm->pc = opc + o; break;
        }
        case OP_IF_ICMPLE: {
            int16_t o = bread();
            blo = spop_lo(); bhi = spop_hi();
            alo = spop_lo(); ahi = spop_hi();
            if ((int16_t)ahi < (int16_t)bhi || (ahi == bhi && alo <= blo))
                g_pjvm->pc = opc + o; break;
        }
        case OP_IF_ACMPEQ: {
            int16_t o = bread();
            blo = spop_lo();
            alo = spop_lo();
            if (alo == blo) g_pjvm->pc = opc + o; break;
        }
        case OP_IF_ACMPNE: {
            int16_t o = bread();
            blo = spop_lo();
            alo = spop_lo();
            if (alo != blo) g_pjvm->pc = opc + o; break;
        }
        case OP_GOTO: {
            int16_t o = bread();
            g_pjvm->pc = opc + o; break;
        }

        case OP_TABLESWITCH: {
            uint32_t base = m_co[g_pjvm->cur_mi];
            g_pjvm->pc = base + (((g_pjvm->pc - base) + 3) & ~3u);
            g_pjvm->pc += 2; int16_t def_off = bread();
            g_pjvm->pc += 2; int16_t low_lo = bread();
            g_pjvm->pc += 2; int16_t high_lo = bread();
            alo = spop_lo(); spop_hi();
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
            alo = spop_lo(); ahi = spop_hi();
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

        case OP_IRETURN: case OP_ARETURN: pjvm_ret(1); break;
        case OP_RETURN: pjvm_ret(0); break;

        case OP_GETSTATIC: {
            uint16_t s = cpread();
            spush(g_pjvm->sf_lo[s], g_pjvm->sf_hi[s]); break;
        }
        case OP_PUTSTATIC: {
            uint16_t s = cpread();
            alo = spop_lo(); ahi = spop_hi();
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
            alo = spop_lo();
            uint16_t addr = alo + 4 + s * 4;
            spush(r16(addr), r16((uint16_t)(addr + 2))); break;
        }
        case OP_PUTFIELD: {
            uint16_t s = cpread();
            alo = spop_lo(); ahi = spop_hi();
            blo = spop_lo();
            uint16_t addr = blo + 4 + s * 4;
            w16(addr, alo); w16((uint16_t)(addr + 2), ahi); break;
        }

        case OP_INVOKESTATIC: case OP_INVOKESPECIAL: {
            uint16_t mi = cpread();
            pjvm_inv(mi); break;
        }
        case OP_INVOKEVIRTUAL: {
            uint16_t bmi = cpread();
            uint8_t vs = m_vs[bmi];
            if (vs == 0xFF) { pjvm_inv(bmi); }
            else {
                uint16_t objref = g_pjvm->stk_lo[g_pjvm->sp - m_ac[bmi]];
                uint8_t ci = (uint8_t)r16(objref);
                pjvm_inv(vt[cls_vb[ci] + vs]);
            }
            break;
        }
        case OP_INVOKEINTERFACE: {
            uint16_t bmi = cpread();
            bcread(); bcread();
            uint8_t vid = m_vmid[bmi];
            uint16_t objref = g_pjvm->stk_lo[g_pjvm->sp - m_ac[bmi]];
            uint8_t ci = (uint8_t)r16(objref);
            uint8_t found = 0xFF;
            for (uint8_t k = 0; k < cls_vs[ci]; k++) {
                if (m_vmid[vt[cls_vb[ci] + k]] == vid) {
                    found = vt[cls_vb[ci] + k]; break;
                }
            }
            if (found != 0xFF) pjvm_inv(found);
            else pjvm_platform_trap(0xB9, g_pjvm->pc);
            break;
        }

        case OP_NEW: {
            uint16_t ci = cpread();
            uint8_t nf = ci < n_classes ? cls_nf[ci] : 0;
            uint16_t a = heap_alloc(g_pjvm, (uint16_t)(4 + nf * 4));
            w16(a, ci); w16((uint16_t)(a + 2), 0);
            spush(a, 0); break;
        }
        case OP_NEWARRAY: {
            uint8_t atype = bcread();
            alo = spop_lo();
            uint8_t esz = 4;
            if (atype == 4 || atype == 8) esz = 1;
            else if (atype == 5 || atype == 9) esz = 2;
            uint16_t a = heap_alloc(g_pjvm, (uint16_t)(4 + alo * esz));
            w16(a, alo); w16((uint16_t)(a + 2), 0);
            spush(a, 0); break;
        }
        case OP_ANEWARRAY: {
            g_pjvm->pc += 2;
            alo = spop_lo();
            uint16_t a = heap_alloc(g_pjvm, (uint16_t)(4 + alo * 4));
            w16(a, alo); w16((uint16_t)(a + 2), 0);
            spush(a, 0); break;
        }
        case OP_ARRAYLENGTH:
            alo = spop_lo(); ahi = spop_hi();
            if (ahi) {
                uint32_t off = ((uint32_t)(ahi - 1) << 16) | alo;
                spush((uint16_t)PROG(off) | ((uint16_t)PROG(off + 1) << 8), 0);
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
            uint16_t tci = cpread();
            alo = g_pjvm->stk_lo[g_pjvm->sp - 1];
            if (alo != 0) {
                uint8_t ci = (uint8_t)r16(alo);
                uint8_t ok = 0;
                while (ci != 0xFF) {
                    if (ci == tci) { ok = 1; break; }
                    ci = cls_pid[ci];
                }
                if (!ok) pjvm_platform_trap(op, opc);
            }
            break;
        }
        case OP_INSTANCEOF: {
            uint16_t tci = cpread();
            alo = spop_lo(); spop_hi();
            if (alo == 0) { spush(0, 0); }
            else {
                uint8_t ci = (uint8_t)r16(alo);
                uint8_t match = 0;
                while (ci != 0xFF) {
                    if (ci == tci) { match = 1; break; }
                    ci = cls_pid[ci];
                }
                spush(match, 0);
            }
            break;
        }

        case OP_ATHROW: {
            uint16_t exc_ref = spop_lo(); spop_hi();
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

#undef NI
