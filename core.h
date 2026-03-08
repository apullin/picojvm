#ifndef PICOJVM_CORE_H
#define PICOJVM_CORE_H

#include <stdint.h>

#ifndef PJVM_METHOD_CAP
#error "PJVM_METHOD_CAP must be defined before including core.h"
#endif
#ifndef PJVM_CLASS_CAP
#error "PJVM_CLASS_CAP must be defined before including core.h"
#endif
#ifndef PJVM_VTABLE_CAP
#error "PJVM_VTABLE_CAP must be defined before including core.h"
#endif
#ifndef PJVM_STATIC_CAP
#error "PJVM_STATIC_CAP must be defined before including core.h"
#endif
#ifndef PJVM_MAX_STACK
#error "PJVM_MAX_STACK must be defined before including core.h"
#endif
#ifndef PJVM_MAX_LOCALS
#error "PJVM_MAX_LOCALS must be defined before including core.h"
#endif
#ifndef PJVM_MAX_FRAMES
#error "PJVM_MAX_FRAMES must be defined before including core.h"
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
};

/* --- per-execution context -------------------------------------------- */
typedef struct {
    uint16_t pc;
    uint16_t cb;
    uint8_t  mi;
    uint8_t  lb;
    uint8_t  so;
} PJVMFrame;

typedef struct {
    uint16_t stk_lo[PJVM_MAX_STACK], stk_hi[PJVM_MAX_STACK];
    uint16_t loc_lo[PJVM_MAX_LOCALS], loc_hi[PJVM_MAX_LOCALS];
    uint16_t sf_lo[PJVM_STATIC_CAP], sf_hi[PJVM_STATIC_CAP];
    PJVMFrame frames[PJVM_MAX_FRAMES];
    uint16_t pc, cur_cb;
    uint8_t  sp, lt, cur_mi, cur_lb;
    int8_t   fdepth;
    uint16_t heap_ptr;
#ifdef PJVM_TRACK_STATS
    uint8_t  sp_max, lt_max, fdepth_max;
#endif
} PJVMCtx;

/* --- shared read-only data (loaded once) ------------------------------ */
static uint8_t  n_methods, main_mi, n_classes;
static uint8_t  n_static_fields, n_int_constants, n_string_constants;
static uint16_t bytecodes_size;
static uint8_t  m_ml[PJVM_METHOD_CAP], m_ac[PJVM_METHOD_CAP];
static uint8_t  m_fl[PJVM_METHOD_CAP], m_vs[PJVM_METHOD_CAP], m_vmid[PJVM_METHOD_CAP];
static uint8_t  m_ec[PJVM_METHOD_CAP], m_eo[PJVM_METHOD_CAP];
static uint16_t m_co[PJVM_METHOD_CAP], m_cb[PJVM_METHOD_CAP];
static uint8_t  cls_pid[PJVM_CLASS_CAP], cls_nf[PJVM_CLASS_CAP], cls_vb[PJVM_CLASS_CAP], cls_vs[PJVM_CLASS_CAP], cls_ci[PJVM_CLASS_CAP];
static uint8_t  vt[PJVM_VTABLE_CAP];
#ifdef PJVM_ASM_HELPERS
uint8_t *cpr;
uint8_t *ic;
uint8_t *sc;
uint8_t *bc;
uint8_t *et;
#else
static uint8_t *cpr;
static uint8_t *ic;
static uint8_t *sc;
static uint8_t *bc;
static uint8_t *et;
#endif
static uint16_t str_refs[32];

/* --- global context pointer (set once at pjvm_run entry) --------------- */
#ifdef PJVM_ASM_HELPERS
PJVMCtx *g_pjvm;
#else
static PJVMCtx *g_pjvm;
#endif

/* --- platform shims (provided by host or target .c) ------------------- */
static uint16_t heap_alloc(PJVMCtx *j, uint16_t size);
static uint8_t  r8(uint16_t a);
static void     w8(uint16_t a, uint8_t v);
static uint16_t r16(uint16_t a);
static void     w16(uint16_t a, uint16_t v);
static void     pjvm_platform_putchar(uint8_t ch);
static uint8_t  pjvm_platform_peek8(uint16_t a);
static void     pjvm_platform_poke8(uint16_t a, uint8_t v);
static void     pjvm_platform_trap(uint8_t op, uint16_t pc);

/* --- noinline helpers for code size ----------------------------------- */

#ifdef PJVM_ASM_HELPERS
/* Provided by jvm85_helpers.S */
extern void     spush(uint16_t lo, uint16_t hi);
extern uint16_t spop_lo(void);
extern uint16_t spop_hi(void);
extern void     lload(uint8_t slot);
extern void     lstore(uint8_t slot);
extern uint8_t  bcread(void);
extern int16_t  bread(void);
extern uint8_t  cpread(void);
#else
NI static void spush(uint16_t lo, uint16_t hi) {
    g_pjvm->stk_lo[g_pjvm->sp] = lo;
    g_pjvm->stk_hi[g_pjvm->sp] = hi;
    g_pjvm->sp++;
#ifdef PJVM_TRACK_STATS
    if (g_pjvm->sp > g_pjvm->sp_max) g_pjvm->sp_max = g_pjvm->sp;
#endif
}

NI static uint16_t spop_lo(void) {
    g_pjvm->sp--;
    return g_pjvm->stk_lo[g_pjvm->sp];
}

NI static uint16_t spop_hi(void) {
    return g_pjvm->stk_hi[g_pjvm->sp];
}

NI static void lload(uint8_t slot) {
    uint8_t i = g_pjvm->cur_lb + slot;
    spush(g_pjvm->loc_lo[i], g_pjvm->loc_hi[i]);
}

NI static void lstore(uint8_t slot) {
    uint8_t i = g_pjvm->cur_lb + slot;
    g_pjvm->sp--;
    g_pjvm->loc_lo[i] = g_pjvm->stk_lo[g_pjvm->sp];
    g_pjvm->loc_hi[i] = g_pjvm->stk_hi[g_pjvm->sp];
}

NI static uint8_t bcread(void) {
    return bc[g_pjvm->pc++];
}

NI static int16_t bread(void) {
    int16_t o = (int16_t)((bc[g_pjvm->pc] << 8) | bc[g_pjvm->pc + 1]);
    g_pjvm->pc += 2;
    return o;
}

NI static uint8_t cpread(void) {
    uint8_t r = cpr[g_pjvm->cur_cb + ((bc[g_pjvm->pc] << 8) | bc[g_pjvm->pc + 1])];
    g_pjvm->pc += 2;
    return r;
}
#endif

static int32_t pjvm_to32(uint16_t lo, uint16_t hi) {
    return (int32_t)((uint32_t)lo | ((uint32_t)hi << 16));
}

NI static void pjvm_push32(int32_t v) {
    spush((uint16_t)v, (uint16_t)((uint32_t)v >> 16));
}

/* --- .j85 loader ------------------------------------------------------ */
static void pjvm_parse(uint8_t *data) {
    n_methods = data[2];
    main_mi = data[3];
    n_static_fields = data[4];
    n_int_constants = data[5];
    n_classes = data[6];
    n_string_constants = data[7];
    bytecodes_size = (uint16_t)data[8] | ((uint16_t)data[9] << 8);

    uint8_t *p = data + 10;
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
        m_co[i] = (uint16_t)p[4] | ((uint16_t)p[5] << 8);
        m_cb[i] = (uint16_t)p[6] | ((uint16_t)p[7] << 8);
        m_vs[i] = p[8]; m_vmid[i] = p[9];
        m_ec[i] = p[10]; m_eo[i] = p[11]; p += 12;
    }

    uint16_t cpc = (uint16_t)p[0] | ((uint16_t)p[1] << 8);
    p += 2;
    cpr = p; p += cpc;
    ic = p; p += (uint16_t)n_int_constants * 4;
    sc = p;
    for (uint8_t i = 0; i < n_string_constants; i++) {
        uint16_t slen = (uint16_t)p[0] | ((uint16_t)p[1] << 8);
        p += 2 + slen;
    }
    bc = p; p += bytecodes_size;
    et = p;
}

/* --- invoke / return -------------------------------------------------- */
static void pjvm_inv(uint8_t mi) {
    uint16_t alo, blo;

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
        case NATIVE_OUT:
            g_pjvm->sp -= 2;
            break;
        case NATIVE_PEEK:
            alo = spop_lo();
            spush(pjvm_platform_peek8(alo), 0);
            break;
        case NATIVE_POKE:
            blo = spop_lo();
            alo = spop_lo();
            pjvm_platform_poke8(alo, (uint8_t)blo);
            break;
        case NATIVE_HALT:
            g_pjvm->fdepth = 0; g_pjvm->pc = 0xFFFF;
            break;
        case NATIVE_OBJECT_INIT:
            g_pjvm->sp--;
            break;
        case NATIVE_STR_LENGTH:
            alo = spop_lo(); /* this = byte[] ref */
            spush(r16(alo), r16((uint16_t)(alo + 2)));
            break;
        case NATIVE_STR_CHARAT:
            blo = spop_lo(); spop_hi(); /* index */
            alo = spop_lo(); /* this = byte[] ref */
            spush(r8(alo + 4 + blo), 0);
            break;
        case NATIVE_STR_EQUALS: {
            blo = spop_lo(); spop_hi(); /* other */
            alo = spop_lo(); /* this */
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
            /* identity — this stays on stack */
            break;
        case NATIVE_PRINT: {
            alo = spop_lo(); /* string ref = byte[] */
            uint16_t slen = r16(alo);
            for (uint16_t i = 0; i < slen; i++)
                pjvm_platform_putchar(r8(alo + 4 + i));
            break;
        }
        case NATIVE_STR_HASHCODE: {
            alo = spop_lo(); /* this = string ref */
            uint16_t slen = r16(alo);
            uint32_t h = 0;
            for (uint16_t i = 0; i < slen; i++)
                h = h * 31 + (uint32_t)r8(alo + 4 + i);
            pjvm_push32((int32_t)h);
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
    f->so = (uint8_t)(g_pjvm->sp - m_ac[mi]); f->cb = g_pjvm->cur_cb;
    g_pjvm->fdepth++;
#ifdef PJVM_TRACK_STATS
    if (g_pjvm->fdepth > g_pjvm->fdepth_max) g_pjvm->fdepth_max = (uint8_t)g_pjvm->fdepth;
#endif

    uint8_t nb = g_pjvm->lt;
    g_pjvm->lt += m_ml[mi];
#ifdef PJVM_TRACK_STATS
    if (g_pjvm->lt > g_pjvm->lt_max) g_pjvm->lt_max = g_pjvm->lt;
#endif
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
        g_pjvm->pc = 0xFFFF;
        if (has_val) spush(rlo, rhi);
        return;
    }
    PJVMFrame *f = &g_pjvm->frames[g_pjvm->fdepth];
    g_pjvm->pc = f->pc; g_pjvm->cur_mi = f->mi; g_pjvm->cur_lb = f->lb;
    g_pjvm->cur_cb = f->cb; g_pjvm->sp = f->so;
    if (has_val) spush(rlo, rhi);
}

static void pjvm_throw(uint16_t exc_ref, uint16_t throw_pc) {
    uint8_t ci = (uint8_t)r16(exc_ref);  /* class_id of thrown exception */

    for (;;) {
        uint8_t count = m_ec[g_pjvm->cur_mi];
        uint8_t base = m_eo[g_pjvm->cur_mi];
        uint16_t rel_pc = throw_pc - m_co[g_pjvm->cur_mi];

        for (uint8_t i = 0; i < count; i++) {
            uint8_t *e = et + (uint16_t)(base + i) * 7;
            uint16_t e_start = (uint16_t)e[0] | ((uint16_t)e[1] << 8);
            uint16_t e_end   = (uint16_t)e[2] | ((uint16_t)e[3] << 8);
            uint16_t e_handler = (uint16_t)e[4] | ((uint16_t)e[5] << 8);
            uint8_t  e_catch  = e[6];

            if (rel_pc >= e_start && rel_pc < e_end) {
                /* Check catch_type match */
                uint8_t match = 0;
                if (e_catch == 0xFF) {
                    match = 1;  /* catch-all / finally */
                } else {
                    uint8_t walk = ci;
                    while (walk != 0xFF) {
                        if (walk == e_catch) { match = 1; break; }
                        walk = cls_pid[walk];
                    }
                }
                if (match) {
                    /* Found handler — reset stack, push exception, jump */
                    g_pjvm->sp = g_pjvm->fdepth > 0
                        ? g_pjvm->frames[g_pjvm->fdepth - 1].so
                        : 0;
                    spush(exc_ref, 0);
                    g_pjvm->pc = m_co[g_pjvm->cur_mi] + e_handler;
                    return;
                }
            }
        }

        /* No handler in this method — unwind frame */
        g_pjvm->lt = g_pjvm->cur_lb;
        g_pjvm->fdepth--;
        if (g_pjvm->fdepth < 0) {
            /* Uncaught exception */
            pjvm_platform_trap(0xBF, throw_pc);
            g_pjvm->pc = 0xFFFF;
            return;
        }
        PJVMFrame *f = &g_pjvm->frames[g_pjvm->fdepth];
        throw_pc = f->pc - 1;  /* back into the invoke instruction */
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

static void pjvm_exec(void);

/* --- interpreter loop ------------------------------------------------- */
static void pjvm_run(PJVMCtx *j) {
    g_pjvm = j;

    /* Pre-allocate interned string constants into heap */
    {
        uint8_t *sp = sc;
        for (uint8_t i = 0; i < n_string_constants; i++) {
            uint16_t slen = (uint16_t)sp[0] | ((uint16_t)sp[1] << 8);
            sp += 2;
            uint16_t a = heap_alloc(j, (uint16_t)(4 + slen));
            w16(a, slen); w16((uint16_t)(a + 2), 0);
            for (uint16_t k = 0; k < slen; k++) w8(a + 4 + k, sp[k]);
            sp += slen;
            str_refs[i] = a;
        }
    }

#ifdef PJVM_TRACK_STATS
    j->sp_max = 0; j->lt_max = 0; j->fdepth_max = 0;
#endif

    /* Run static initializers (<clinit>) */
    for (uint8_t ci = 0; ci < n_classes; ci++) {
        if (cls_ci[ci] == 0xFF) continue;
        uint8_t mi = cls_ci[ci];
        j->cur_mi = mi; j->cur_lb = 0; j->cur_cb = m_cb[mi];
        j->lt = m_ml[mi]; j->pc = m_co[mi];
        j->fdepth = 0; j->sp = 0;
#ifdef PJVM_TRACK_STATS
        if (j->lt > j->lt_max) j->lt_max = j->lt;
#endif
        pjvm_exec();
    }

    /* Run main */
    j->cur_mi = main_mi; j->cur_lb = 0; j->cur_cb = m_cb[main_mi];
    j->lt = m_ml[main_mi]; j->pc = m_co[main_mi];
    j->fdepth = 0; j->sp = 0;
#ifdef PJVM_TRACK_STATS
    if (j->lt > j->lt_max) j->lt_max = j->lt;
#endif
    if (m_ac[main_mi] > 0) { j->loc_lo[0] = 0; j->loc_hi[0] = 0; }
    pjvm_exec();
}

static void pjvm_exec(void) {
    while (g_pjvm->pc != 0xFFFF) {
        uint16_t opc = g_pjvm->pc;
        uint8_t op = bc[g_pjvm->pc++];
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
            uint8_t ci = cpr[g_pjvm->cur_cb + bcread()];
            if (ci & 0x80) {
                spush(str_refs[ci & 0x7F], 0);
            } else {
                uint8_t *p = ic + (uint16_t)ci * 4;
                spush((uint16_t)p[0] | ((uint16_t)p[1] << 8),
                      (uint16_t)p[2] | ((uint16_t)p[3] << 8));
            }
            break;
        }
        case OP_LDC_W: {
            uint8_t ci = cpread();
            if (ci & 0x80) {
                spush(str_refs[ci & 0x7F], 0);
            } else {
                uint8_t *p = ic + (uint16_t)ci * 4;
                spush((uint16_t)p[0] | ((uint16_t)p[1] << 8),
                      (uint16_t)p[2] | ((uint16_t)p[3] << 8));
            }
            break;
        }

        case OP_ILOAD: case OP_ALOAD: lload(bcread()); break;
        case OP_ILOAD_0: case OP_ALOAD_0: lload(0); break;
        case OP_ILOAD_1: case OP_ALOAD_1: lload(1); break;
        case OP_ILOAD_2: case OP_ALOAD_2: lload(2); break;
        case OP_ILOAD_3: case OP_ALOAD_3: lload(3); break;

        case OP_IALOAD: case OP_AALOAD: {
            alo = spop_lo(); /* index */
            blo = spop_lo(); /* arrayref */
            uint16_t addr = blo + 4 + alo * 4;
            spush(r16(addr), r16((uint16_t)(addr + 2)));
            break;
        }
        case OP_BALOAD: {
            alo = spop_lo(); blo = spop_lo();
            int8_t bv = (int8_t)r8(blo + 4 + alo);
            spush((uint16_t)(int16_t)bv, bv < 0 ? 0xFFFF : 0);
            break;
        }
        case OP_CALOAD: {
            alo = spop_lo(); blo = spop_lo();
            spush(r16(blo + 4 + alo * 2), 0);
            break;
        }
        case OP_SALOAD: {
            alo = spop_lo(); blo = spop_lo();
            uint16_t sv = r16(blo + 4 + alo * 2);
            spush(sv, (int16_t)sv < 0 ? 0xFFFF : 0);
            break;
        }

        case OP_ISTORE: case OP_ASTORE: lstore(bcread()); break;
        case OP_ISTORE_0: case OP_ASTORE_0: lstore(0); break;
        case OP_ISTORE_1: case OP_ASTORE_1: lstore(1); break;
        case OP_ISTORE_2: case OP_ASTORE_2: lstore(2); break;
        case OP_ISTORE_3: case OP_ASTORE_3: lstore(3); break;

        case OP_IASTORE: case OP_AASTORE: {
            alo = spop_lo(); ahi = spop_hi(); /* value */
            blo = spop_lo(); /* index */
            uint16_t aref = spop_lo(); /* arrayref */
            uint16_t addr = aref + 4 + blo * 4;
            w16(addr, alo); w16((uint16_t)(addr + 2), ahi);
            break;
        }
        case OP_BASTORE: {
            alo = spop_lo(); spop_hi();
            blo = spop_lo();
            uint16_t aref = spop_lo();
            w8(aref + 4 + blo, (uint8_t)alo);
            break;
        }
        case OP_CASTORE: case OP_SASTORE: {
            alo = spop_lo(); spop_hi();
            blo = spop_lo();
            uint16_t aref = spop_lo();
            w16(aref + 4 + blo * 2, alo);
            break;
        }

        case OP_POP: g_pjvm->sp--; break;
        case OP_POP2: g_pjvm->sp -= 2; break;
        case OP_DUP: {
            uint8_t t = g_pjvm->sp - 1;
            spush(g_pjvm->stk_lo[t], g_pjvm->stk_hi[t]);
            break;
        }
        case OP_DUP_X1: {
            /* ..., v2, v1 → ..., v1, v2, v1 */
            uint8_t s1 = g_pjvm->sp - 1, s2 = g_pjvm->sp - 2;
            uint16_t v1l = g_pjvm->stk_lo[s1], v1h = g_pjvm->stk_hi[s1];
            g_pjvm->stk_lo[s1] = g_pjvm->stk_lo[s2]; g_pjvm->stk_hi[s1] = g_pjvm->stk_hi[s2];
            g_pjvm->stk_lo[s2] = v1l; g_pjvm->stk_hi[s2] = v1h;
            spush(v1l, v1h);
            break;
        }
        case OP_DUP_X2: {
            /* ..., v3, v2, v1 → ..., v1, v3, v2, v1 */
            uint8_t s1 = g_pjvm->sp - 1, s2 = g_pjvm->sp - 2, s3 = g_pjvm->sp - 3;
            uint16_t v1l = g_pjvm->stk_lo[s1], v1h = g_pjvm->stk_hi[s1];
            g_pjvm->stk_lo[s1] = g_pjvm->stk_lo[s2]; g_pjvm->stk_hi[s1] = g_pjvm->stk_hi[s2];
            g_pjvm->stk_lo[s2] = g_pjvm->stk_lo[s3]; g_pjvm->stk_hi[s2] = g_pjvm->stk_hi[s3];
            g_pjvm->stk_lo[s3] = v1l; g_pjvm->stk_hi[s3] = v1h;
            spush(v1l, v1h);
            break;
        }
        case OP_DUP2: {
            /* ..., v2, v1 → ..., v2, v1, v2, v1 */
            uint8_t s1 = g_pjvm->sp - 1, s2 = g_pjvm->sp - 2;
            spush(g_pjvm->stk_lo[s2], g_pjvm->stk_hi[s2]);
            spush(g_pjvm->stk_lo[s1], g_pjvm->stk_hi[s1]);
            break;
        }
        case OP_SWAP: {
            uint8_t t = g_pjvm->sp - 1, u = g_pjvm->sp - 2;
            alo = g_pjvm->stk_lo[t]; ahi = g_pjvm->stk_hi[t];
            g_pjvm->stk_lo[t] = g_pjvm->stk_lo[u]; g_pjvm->stk_hi[t] = g_pjvm->stk_hi[u];
            g_pjvm->stk_lo[u] = alo; g_pjvm->stk_hi[u] = ahi;
            break;
        }

        /* --- 16-bit split arithmetic ---------------------------------- */
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
            uint8_t i = g_pjvm->cur_lb + idx;
            uint16_t old = g_pjvm->loc_lo[i];
            uint16_t inc = (uint16_t)(int16_t)v;
            uint16_t nlo = old + inc;
            uint16_t carry = (v >= 0) ? (nlo < old ? 1 : 0)
                                      : (nlo > old ? (uint16_t)-1 : 0);
            g_pjvm->loc_lo[i] = nlo;
            g_pjvm->loc_hi[i] += (v < 0 ? 0xFFFF : 0) + carry;
            break;
        }

        /* --- heavy ops: reconstruct int32_t --------------------------- */
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

        /* --- conversions ---------------------------------------------- */
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

        /* --- branches: compare against zero --------------------------- */
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

        /* --- branches: compare two values ----------------------------- */
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

        /* --- tableswitch / lookupswitch ------------------------------- */
        case OP_TABLESWITCH: {
            /* Align pc to 4-byte boundary relative to method start */
            uint16_t base = m_co[g_pjvm->cur_mi];
            g_pjvm->pc = base + (((g_pjvm->pc - base) + 3) & ~3u);
            /* Read default, low, high (big-endian i32, use low 16 bits for offsets) */
            g_pjvm->pc += 2; int16_t def_off = bread(); /* default offset (lo16) */
            g_pjvm->pc += 2; int16_t low_lo = bread();  /* low (lo16) */
            g_pjvm->pc += 2; int16_t high_lo = bread(); /* high (lo16) */
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
            uint16_t base = m_co[g_pjvm->cur_mi];
            g_pjvm->pc = base + (((g_pjvm->pc - base) + 3) & ~3u);
            g_pjvm->pc += 2; int16_t def_off = bread();
            g_pjvm->pc += 2; int16_t npairs = bread();
            alo = spop_lo(); ahi = spop_hi();
            /* Compare as bytes — avoids 32-bit PACK + subtract (~30B, spill-heavy).
               Four 8-bit CMPs are ~16B, no register pressure, and short-circuit. */
            uint8_t v0 = (uint8_t)(ahi >> 8), v1 = (uint8_t)ahi,
                    v2 = (uint8_t)(alo >> 8), v3 = (uint8_t)alo;
            uint8_t found = 0;
            for (int16_t i = 0; i < npairs; i++) {
                uint8_t m0 = bc[g_pjvm->pc], m1 = bc[g_pjvm->pc+1],
                        m2 = bc[g_pjvm->pc+2], m3 = bc[g_pjvm->pc+3];
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

        /* --- invoke/return -------------------------------------------- */
        case OP_IRETURN: case OP_ARETURN: pjvm_ret(1); break;
        case OP_RETURN: pjvm_ret(0); break;

        /* --- static fields -------------------------------------------- */
        case OP_GETSTATIC: {
            uint8_t s = cpread();
            spush(g_pjvm->sf_lo[s], g_pjvm->sf_hi[s]); break;
        }
        case OP_PUTSTATIC: {
            uint8_t s = cpread();
            alo = spop_lo(); ahi = spop_hi();
            g_pjvm->sf_lo[s] = alo; g_pjvm->sf_hi[s] = ahi; break;
        }

        /* --- object fields (heap) ------------------------------------- */
        case OP_GETFIELD: {
            uint8_t s = cpread();
            alo = spop_lo(); /* objectref */
            uint16_t addr = alo + 4 + s * 4;
            spush(r16(addr), r16((uint16_t)(addr + 2))); break;
        }
        case OP_PUTFIELD: {
            uint8_t s = cpread();
            alo = spop_lo(); ahi = spop_hi(); /* value */
            blo = spop_lo(); /* objectref */
            uint16_t addr = blo + 4 + s * 4;
            w16(addr, alo); w16((uint16_t)(addr + 2), ahi); break;
        }

        /* --- invoke --------------------------------------------------- */
        case OP_INVOKESTATIC: case OP_INVOKESPECIAL: {
            uint8_t mi = cpread();
            pjvm_inv(mi); break;
        }
        case OP_INVOKEVIRTUAL: {
            uint8_t bmi = cpread();
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
            uint8_t bmi = cpread();
            bcread(); bcread(); /* skip count + zero */
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

        /* --- allocation ----------------------------------------------- */
        case OP_NEW: {
            uint8_t ci = cpread();
            uint8_t nf = ci < n_classes ? cls_nf[ci] : 0;
            uint16_t a = heap_alloc(g_pjvm, (uint16_t)(4 + nf * 4));
            w16(a, ci); w16((uint16_t)(a + 2), 0);
            spush(a, 0); break;
        }
        case OP_NEWARRAY: {
            uint8_t atype = bcread();
            alo = spop_lo();
            uint8_t esz = 4;
            if (atype == 4 || atype == 8) esz = 1;       /* boolean/byte */
            else if (atype == 5 || atype == 9) esz = 2;   /* char/short */
            uint16_t a = heap_alloc(g_pjvm, (uint16_t)(4 + alo * esz));
            w16(a, alo); w16((uint16_t)(a + 2), 0);
            spush(a, 0); break;
        }
        case OP_ANEWARRAY: {
            g_pjvm->pc += 2; /* skip class index */
            alo = spop_lo();
            uint16_t a = heap_alloc(g_pjvm, (uint16_t)(4 + alo * 4));
            w16(a, alo); w16((uint16_t)(a + 2), 0);
            spush(a, 0); break;
        }
        case OP_ARRAYLENGTH:
            alo = spop_lo();
            spush(r16(alo), r16((uint16_t)(alo + 2))); break;

        case OP_MULTIANEWARRAY: {
            g_pjvm->pc += 2; /* skip CP class index */
            uint8_t ndims = bcread();
            uint16_t sizes[4];
            for (uint8_t d = ndims; d > 0; d--)
                sizes[d-1] = spop_lo();
            spush(pjvm_multi_alloc(sizes, 0, ndims), 0);
            break;
        }

        case OP_CHECKCAST: {
            uint8_t tci = cpread();
            alo = g_pjvm->stk_lo[g_pjvm->sp - 1]; /* peek objectref */
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
            uint8_t tci = cpread();
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
                pjvm_platform_trap(0xBF, opc); /* NullPointerException */
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
#endif
