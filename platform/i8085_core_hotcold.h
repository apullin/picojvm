#ifndef TOOLING_PICOJVM_CORE_H
#define TOOLING_PICOJVM_CORE_H

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

enum {
    OP_NOP = 0x00,
    OP_ACONST_NULL = 0x01,
    OP_ICONST_M1 = 0x02, OP_ICONST_0 = 0x03, OP_ICONST_1 = 0x04,
    OP_ICONST_2 = 0x05, OP_ICONST_3 = 0x06, OP_ICONST_4 = 0x07,
    OP_ICONST_5 = 0x08,
    OP_BIPUSH = 0x10, OP_SIPUSH = 0x11, OP_LDC = 0x12,
    OP_ILOAD = 0x15, OP_ALOAD = 0x19,
    OP_ILOAD_0 = 0x1A, OP_ILOAD_1 = 0x1B, OP_ILOAD_2 = 0x1C, OP_ILOAD_3 = 0x1D,
    OP_ALOAD_0 = 0x2A, OP_ALOAD_1 = 0x2B, OP_ALOAD_2 = 0x2C, OP_ALOAD_3 = 0x2D,
    OP_IALOAD = 0x2E, OP_AALOAD = 0x32,
    OP_ISTORE = 0x36, OP_ASTORE = 0x3A,
    OP_ISTORE_0 = 0x3B, OP_ISTORE_1 = 0x3C, OP_ISTORE_2 = 0x3D, OP_ISTORE_3 = 0x3E,
    OP_IASTORE = 0x4F, OP_AASTORE = 0x53,
    OP_ASTORE_0 = 0x4B, OP_ASTORE_1 = 0x4C, OP_ASTORE_2 = 0x4D, OP_ASTORE_3 = 0x4E,
    OP_POP = 0x57, OP_DUP = 0x59, OP_SWAP = 0x5F,
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
    OP_IRETURN = 0xAC, OP_ARETURN = 0xB0, OP_RETURN = 0xB1,
    OP_GETSTATIC = 0xB2, OP_PUTSTATIC = 0xB3,
    OP_GETFIELD = 0xB4, OP_PUTFIELD = 0xB5,
    OP_INVOKEVIRTUAL = 0xB6, OP_INVOKESPECIAL = 0xB7, OP_INVOKESTATIC = 0xB8,
    OP_NEW = 0xBB, OP_NEWARRAY = 0xBC, OP_ANEWARRAY = 0xBD,
    OP_ARRAYLENGTH = 0xBE,
    OP_IFNULL = 0xC6, OP_IFNONNULL = 0xC7,
};

enum {
    NATIVE_PUTCHAR = 0,
    NATIVE_IN = 1,
    NATIVE_OUT = 2,
    NATIVE_PEEK = 3,
    NATIVE_POKE = 4,
    NATIVE_HALT = 5,
    NATIVE_OBJECT_INIT = 6,
};

typedef struct {
    uint16_t pc;
    uint16_t cb;
    uint8_t mi;
    uint8_t lb;
    uint8_t so;
} PJVMFrame;

typedef struct {
    uint16_t pc, cur_cb;
    uint8_t sp, lt, cur_mi, cur_lb;
    int8_t fdepth;
#ifdef PJVM_TRACK_STATS
    uint8_t sp_max, lt_max, fdepth_max;
#endif
} PJVMHotState;

typedef struct {
    uint16_t stk_lo[PJVM_MAX_STACK], stk_hi[PJVM_MAX_STACK];
    uint16_t loc_lo[PJVM_MAX_LOCALS], loc_hi[PJVM_MAX_LOCALS];
    uint16_t sf_lo[PJVM_STATIC_CAP], sf_hi[PJVM_STATIC_CAP];
    PJVMFrame frames[PJVM_MAX_FRAMES];
    uint16_t heap_ptr;
} PJVMColdState;

typedef struct {
    PJVMHotState hot;
    PJVMColdState cold;
} PJVMCtx;

static uint8_t n_methods, main_mi, n_classes;
static uint8_t n_static_fields, n_int_constants;
static uint16_t bytecodes_size;
static uint8_t m_ml[PJVM_METHOD_CAP], m_ac[PJVM_METHOD_CAP], m_fl[PJVM_METHOD_CAP], m_vs[PJVM_METHOD_CAP];
static uint16_t m_co[PJVM_METHOD_CAP], m_cb[PJVM_METHOD_CAP];
static uint8_t cls_nf[PJVM_CLASS_CAP], cls_vb[PJVM_CLASS_CAP], cls_vs[PJVM_CLASS_CAP];
static uint8_t vt[PJVM_VTABLE_CAP];
static uint8_t *cpr;
static uint8_t *ic;
static uint8_t *bc;

static uint16_t heap_alloc(PJVMCtx *j, uint16_t size);
static uint16_t r16(uint16_t a);
static void w16(uint16_t a, uint16_t v);
static void pjvm_platform_putchar(uint8_t ch);
static uint8_t pjvm_platform_peek8(uint16_t a);
static void pjvm_platform_poke8(uint16_t a, uint8_t v);
static void pjvm_platform_trap(uint8_t op, uint16_t pc);

#ifdef PJVM_TRACK_STATS
static inline void pjvm_track_sp(PJVMHotState *hot) {
    if (hot->sp > hot->sp_max) hot->sp_max = hot->sp;
}

static inline void pjvm_track_lt(PJVMHotState *hot) {
    if (hot->lt > hot->lt_max) hot->lt_max = hot->lt;
}

static inline void pjvm_track_fdepth(PJVMHotState *hot) {
    if ((uint8_t)hot->fdepth > hot->fdepth_max) {
        hot->fdepth_max = (uint8_t)hot->fdepth;
    }
}
#else
static inline void pjvm_track_sp(PJVMHotState *hot) { (void)hot; }
static inline void pjvm_track_lt(PJVMHotState *hot) { (void)hot; }
static inline void pjvm_track_fdepth(PJVMHotState *hot) { (void)hot; }
#endif

static inline void pjvm_push(PJVMCtx *j, uint16_t lo, uint16_t hi) {
    PJVMHotState *hot = &j->hot;
    PJVMColdState *cold = &j->cold;

    cold->stk_lo[hot->sp] = lo;
    cold->stk_hi[hot->sp] = hi;
    hot->sp++;
    pjvm_track_sp(hot);
}

static inline void pjvm_pop_a(PJVMCtx *j, uint16_t *lo, uint16_t *hi) {
    PJVMHotState *hot = &j->hot;
    PJVMColdState *cold = &j->cold;

    hot->sp--;
    *lo = cold->stk_lo[hot->sp];
    *hi = cold->stk_hi[hot->sp];
}

static inline void pjvm_pop_b(PJVMCtx *j, uint16_t *lo, uint16_t *hi) {
    PJVMHotState *hot = &j->hot;
    PJVMColdState *cold = &j->cold;

    hot->sp--;
    *lo = cold->stk_lo[hot->sp];
    *hi = cold->stk_hi[hot->sp];
}

static inline int32_t pjvm_to32(uint16_t lo, uint16_t hi) {
    return (int32_t)((uint32_t)lo | ((uint32_t)hi << 16));
}

static inline void pjvm_read_ic(uint8_t idx, uint16_t *lo, uint16_t *hi) {
    uint8_t *p = ic + (uint16_t)idx * 4;
    *lo = (uint16_t)p[0] | ((uint16_t)p[1] << 8);
    *hi = (uint16_t)p[2] | ((uint16_t)p[3] << 8);
}

static void pjvm_parse(uint8_t *data) {
    n_methods = data[2];
    main_mi = data[3];
    n_static_fields = data[4];
    n_int_constants = data[5];
    n_classes = data[6];
    bytecodes_size = (uint16_t)data[8] | ((uint16_t)data[9] << 8);

    {
        uint8_t *p = data + 10;
        uint8_t vo = 0;

        for (uint8_t i = 0; i < n_classes; i++) {
            p++;
            cls_nf[i] = *p++;
            cls_vs[i] = *p++;
            cls_vb[i] = vo;
            for (uint8_t j = 0; j < cls_vs[i]; j++) {
                vt[vo++] = *p++;
            }
        }

        for (uint8_t i = 0; i < n_methods; i++) {
            m_ml[i] = p[0];
            m_ac[i] = p[2];
            m_fl[i] = p[3];
            m_co[i] = (uint16_t)p[4] | ((uint16_t)p[5] << 8);
            m_cb[i] = (uint16_t)p[6] | ((uint16_t)p[7] << 8);
            m_vs[i] = p[8];
            p += 9;
        }

        {
            uint16_t cpc = (uint16_t)p[0] | ((uint16_t)p[1] << 8);
            p += 2;
            cpr = p;
            p += cpc;
        }

        ic = p;
        p += (uint16_t)n_int_constants * 4;
        bc = p;
    }
}

static void pjvm_inv(PJVMCtx *j, uint8_t mi) {
    PJVMHotState *hot = &j->hot;
    PJVMColdState *cold = &j->cold;
    uint16_t alo, ahi, blo, bhi;

    if (m_fl[mi] & 1) {
        uint8_t nid = m_fl[mi] >> 1;

        switch (nid) {
        case NATIVE_PUTCHAR:
            pjvm_pop_a(j, &alo, &ahi);
            pjvm_platform_putchar((uint8_t)alo);
            break;
        case NATIVE_IN:
            pjvm_pop_a(j, &alo, &ahi);
            pjvm_push(j, 0, 0);
            break;
        case NATIVE_OUT:
            pjvm_pop_b(j, &blo, &bhi);
            pjvm_pop_a(j, &alo, &ahi);
            (void)alo;
            (void)ahi;
            (void)blo;
            (void)bhi;
            break;
        case NATIVE_PEEK:
            pjvm_pop_a(j, &alo, &ahi);
            pjvm_push(j, pjvm_platform_peek8(alo), 0);
            break;
        case NATIVE_POKE:
            pjvm_pop_b(j, &blo, &bhi);
            pjvm_pop_a(j, &alo, &ahi);
            pjvm_platform_poke8(alo, (uint8_t)blo);
            break;
        case NATIVE_HALT:
            hot->fdepth = 0;
            hot->pc = 0xFFFF;
            break;
        case NATIVE_OBJECT_INIT:
            hot->sp--;
            break;
        default:
            pjvm_platform_trap(0xFF, hot->pc);
            break;
        }
        return;
    }

    {
        PJVMFrame *f = &cold->frames[hot->fdepth];
        f->pc = hot->pc;
        f->mi = hot->cur_mi;
        f->lb = hot->cur_lb;
        f->so = (uint8_t)(hot->sp - m_ac[mi]);
        f->cb = hot->cur_cb;
    }

    hot->fdepth++;
    pjvm_track_fdepth(hot);

    {
        uint8_t nb = hot->lt;
        hot->lt = (uint8_t)(hot->lt + m_ml[mi]);
        pjvm_track_lt(hot);

        for (int i = (int)m_ac[mi] - 1; i >= 0; i--) {
            hot->sp--;
            cold->loc_lo[nb + i] = cold->stk_lo[hot->sp];
            cold->loc_hi[nb + i] = cold->stk_hi[hot->sp];
        }

        hot->cur_mi = mi;
        hot->cur_lb = nb;
    }

    hot->cur_cb = m_cb[mi];
    hot->pc = m_co[mi];
}

static void pjvm_ret(PJVMCtx *j, uint8_t has_val) {
    PJVMHotState *hot = &j->hot;
    PJVMColdState *cold = &j->cold;
    uint16_t rlo = 0, rhi = 0;

    if (has_val) {
        pjvm_pop_a(j, &rlo, &rhi);
    }

    hot->lt = hot->cur_lb;
    hot->fdepth--;

    if (hot->fdepth < 0) {
        hot->pc = 0xFFFF;
        if (has_val) {
            pjvm_push(j, rlo, rhi);
        }
        return;
    }

    {
        PJVMFrame *f = &cold->frames[hot->fdepth];
        hot->pc = f->pc;
        hot->cur_mi = f->mi;
        hot->cur_lb = f->lb;
        hot->cur_cb = f->cb;
        hot->sp = f->so;
    }

    if (has_val) {
        pjvm_push(j, rlo, rhi);
    }
}

#define PJVM_HOT_RU1(CODE, PC) ((CODE)[(PC)++])

#define PJVM_HOT_RU2(CODE, PC, OUT) \
    do { \
        (OUT) = (uint16_t)(((CODE)[(PC)] << 8) | (CODE)[(PC) + 1]); \
        (PC) += 2; \
    } while (0)

#define PJVM_HOT_RS2(CODE, PC, OUT) \
    do { \
        (OUT) = (int16_t)(((CODE)[(PC)] << 8) | (CODE)[(PC) + 1]); \
        (PC) += 2; \
    } while (0)

#define PJVM_HOT_RCP(CP, CUR_CB, IDX) ((CP)[(CUR_CB) + (IDX)])

#ifdef PJVM_TRACK_STATS
#define PJVM_FLUSH_HOT(HOT, PC, CUR_CB, CUR_MI, CUR_LB, SP, LT, SP_MAX) \
    do { \
        (HOT)->pc = (PC); \
        (HOT)->cur_cb = (CUR_CB); \
        (HOT)->cur_mi = (CUR_MI); \
        (HOT)->cur_lb = (CUR_LB); \
        (HOT)->sp = (SP); \
        (HOT)->lt = (LT); \
        (HOT)->sp_max = (SP_MAX); \
    } while (0)

#define PJVM_RELOAD_HOT(HOT, PC, CUR_CB, CUR_MI, CUR_LB, SP, LT, SP_MAX) \
    do { \
        (PC) = (HOT)->pc; \
        (CUR_CB) = (HOT)->cur_cb; \
        (CUR_MI) = (HOT)->cur_mi; \
        (CUR_LB) = (HOT)->cur_lb; \
        (SP) = (HOT)->sp; \
        (LT) = (HOT)->lt; \
        (SP_MAX) = (HOT)->sp_max; \
    } while (0)
#else
#define PJVM_FLUSH_HOT(HOT, PC, CUR_CB, CUR_MI, CUR_LB, SP, LT, SP_MAX) \
    do { \
        (void)(SP_MAX); \
        (HOT)->pc = (PC); \
        (HOT)->cur_cb = (CUR_CB); \
        (HOT)->cur_mi = (CUR_MI); \
        (HOT)->cur_lb = (CUR_LB); \
        (HOT)->sp = (SP); \
        (HOT)->lt = (LT); \
    } while (0)

#define PJVM_RELOAD_HOT(HOT, PC, CUR_CB, CUR_MI, CUR_LB, SP, LT, SP_MAX) \
    do { \
        (void)(SP_MAX); \
        (PC) = (HOT)->pc; \
        (CUR_CB) = (HOT)->cur_cb; \
        (CUR_MI) = (HOT)->cur_mi; \
        (CUR_LB) = (HOT)->cur_lb; \
        (SP) = (HOT)->sp; \
        (LT) = (HOT)->lt; \
    } while (0)
#endif

#define PJVM_TOS_FLUSH(STK_LO, STK_HI, SP, TOS_VALID, TOS_SYNCED, TOS_LO, TOS_HI) \
    do { \
        if ((TOS_VALID) && (SP) > 0 && !(TOS_SYNCED)) { \
            (STK_LO)[(uint8_t)((SP) - 1)] = (TOS_LO); \
            (STK_HI)[(uint8_t)((SP) - 1)] = (TOS_HI); \
            (TOS_SYNCED) = 1; \
        } \
    } while (0)

#define PJVM_TOS_RELOAD(STK_LO, STK_HI, SP, TOS_VALID, TOS_SYNCED, TOS_LO, TOS_HI) \
    do { \
        if ((SP) > 0) { \
            (TOS_LO) = (STK_LO)[(uint8_t)((SP) - 1)]; \
            (TOS_HI) = (STK_HI)[(uint8_t)((SP) - 1)]; \
            (TOS_VALID) = 1; \
            (TOS_SYNCED) = 1; \
        } else { \
            (TOS_VALID) = 0; \
            (TOS_SYNCED) = 1; \
        } \
    } while (0)

#define PJVM_TOS_PUSH(STK_LO, STK_HI, SP, SP_MAX, TOS_VALID, TOS_SYNCED, TOS_LO, TOS_HI, LO, HI) \
    do { \
        PJVM_TOS_FLUSH((STK_LO), (STK_HI), (SP), (TOS_VALID), (TOS_SYNCED), (TOS_LO), (TOS_HI)); \
        (TOS_LO) = (LO); \
        (TOS_HI) = (HI); \
        (TOS_VALID) = 1; \
        (TOS_SYNCED) = 0; \
        (SP)++; \
        if ((SP) > (SP_MAX)) (SP_MAX) = (SP); \
    } while (0)

#define PJVM_TOS_POP(STK_LO, STK_HI, SP, TOS_VALID, TOS_SYNCED, TOS_LO, TOS_HI, OUT_LO, OUT_HI) \
    do { \
        (OUT_LO) = (TOS_LO); \
        (OUT_HI) = (TOS_HI); \
        (SP)--; \
        if ((SP) > 0) { \
            (TOS_LO) = (STK_LO)[(uint8_t)((SP) - 1)]; \
            (TOS_HI) = (STK_HI)[(uint8_t)((SP) - 1)]; \
            (TOS_VALID) = 1; \
            (TOS_SYNCED) = 1; \
        } else { \
            (TOS_VALID) = 0; \
            (TOS_SYNCED) = 1; \
        } \
    } while (0)

#define PJVM_TOS_ABS_LO(STK_LO, SP, TOS_VALID, TOS_LO, IDX) \
    (((TOS_VALID) && ((IDX) == (uint8_t)((SP) - 1))) ? (TOS_LO) : (STK_LO)[(IDX)])

static void pjvm_run(PJVMCtx *j) {
    PJVMHotState *hot = &j->hot;
    PJVMColdState *cold = &j->cold;
    uint16_t *stk_lo = cold->stk_lo;
    uint16_t *stk_hi = cold->stk_hi;
    uint16_t *loc_lo = cold->loc_lo;
    uint16_t *loc_hi = cold->loc_hi;
    uint16_t *sf_lo = cold->sf_lo;
    uint16_t *sf_hi = cold->sf_hi;
    uint8_t *code = bc;
    uint8_t *cp_map = cpr;
    uint16_t pc, cur_cb;
    uint8_t sp, lt, cur_mi, cur_lb, sp_max;
    uint16_t alo = 0, ahi = 0, blo = 0, bhi = 0;
    uint16_t tos_lo = 0, tos_hi = 0;
    uint8_t tos_valid = 0, tos_synced = 1;

    hot->cur_mi = main_mi;
    hot->cur_lb = 0;
    hot->cur_cb = m_cb[main_mi];
    hot->lt = m_ml[main_mi];
    hot->pc = m_co[main_mi];
    hot->fdepth = 0;
    hot->sp = 0;
#ifdef PJVM_TRACK_STATS
    hot->sp_max = 0;
    hot->lt_max = hot->lt;
    hot->fdepth_max = 0;
#endif
    pjvm_track_lt(hot);

    if (m_ac[main_mi] > 0) {
        loc_lo[0] = 0;
        loc_hi[0] = 0;
    }

    PJVM_RELOAD_HOT(hot, pc, cur_cb, cur_mi, cur_lb, sp, lt, sp_max);
    PJVM_TOS_RELOAD(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi);

    while (pc != 0xFFFF) {
        uint16_t opc = pc;
        uint8_t op = PJVM_HOT_RU1(code, pc);

        switch (op) {
        case OP_NOP:
            break;
        case OP_ACONST_NULL:
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced, tos_lo, tos_hi, 0, 0);
            break;
        case OP_ICONST_M1:
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced, tos_lo, tos_hi, 0xFFFF, 0xFFFF);
            break;
        case OP_ICONST_0:
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced, tos_lo, tos_hi, 0, 0);
            break;
        case OP_ICONST_1:
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced, tos_lo, tos_hi, 1, 0);
            break;
        case OP_ICONST_2:
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced, tos_lo, tos_hi, 2, 0);
            break;
        case OP_ICONST_3:
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced, tos_lo, tos_hi, 3, 0);
            break;
        case OP_ICONST_4:
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced, tos_lo, tos_hi, 4, 0);
            break;
        case OP_ICONST_5:
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced, tos_lo, tos_hi, 5, 0);
            break;

        case OP_BIPUSH: {
            int8_t v = (int8_t)PJVM_HOT_RU1(code, pc);
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced,
                         tos_lo, tos_hi, (uint16_t)(int16_t)v,
                         v < 0 ? 0xFFFF : 0);
            break;
        }
        case OP_SIPUSH: {
            uint16_t imm;
            int16_t v;
            PJVM_HOT_RU2(code, pc, imm);
            v = (int16_t)imm;
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced,
                         tos_lo, tos_hi, (uint16_t)v, v < 0 ? 0xFFFF : 0);
            break;
        }
        case OP_LDC: {
            uint8_t ci = PJVM_HOT_RCP(cp_map, cur_cb, PJVM_HOT_RU1(code, pc));
            pjvm_read_ic(ci, &alo, &ahi);
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced,
                         tos_lo, tos_hi, alo, ahi);
            break;
        }

        case OP_ILOAD: case OP_ALOAD: {
            uint8_t i = cur_lb + PJVM_HOT_RU1(code, pc);
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced,
                         tos_lo, tos_hi, loc_lo[i], loc_hi[i]);
            break;
        }
        case OP_ILOAD_0: case OP_ALOAD_0:
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced,
                         tos_lo, tos_hi, loc_lo[cur_lb + 0], loc_hi[cur_lb + 0]);
            break;
        case OP_ILOAD_1: case OP_ALOAD_1:
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced,
                         tos_lo, tos_hi, loc_lo[cur_lb + 1], loc_hi[cur_lb + 1]);
            break;
        case OP_ILOAD_2: case OP_ALOAD_2:
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced,
                         tos_lo, tos_hi, loc_lo[cur_lb + 2], loc_hi[cur_lb + 2]);
            break;
        case OP_ILOAD_3: case OP_ALOAD_3:
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced,
                         tos_lo, tos_hi, loc_lo[cur_lb + 3], loc_hi[cur_lb + 3]);
            break;

        case OP_IALOAD: case OP_AALOAD: {
            uint16_t addr;
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, blo, bhi);
            addr = (uint16_t)(blo + 4 + alo * 4);
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced,
                         tos_lo, tos_hi, r16(addr), r16((uint16_t)(addr + 2)));
            break;
        }

        case OP_ISTORE: case OP_ASTORE: {
            uint8_t i;
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            i = cur_lb + PJVM_HOT_RU1(code, pc);
            loc_lo[i] = alo;
            loc_hi[i] = ahi;
            break;
        }
        case OP_ISTORE_0: case OP_ASTORE_0:
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            loc_lo[cur_lb + 0] = alo;
            loc_hi[cur_lb + 0] = ahi;
            break;
        case OP_ISTORE_1: case OP_ASTORE_1:
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            loc_lo[cur_lb + 1] = alo;
            loc_hi[cur_lb + 1] = ahi;
            break;
        case OP_ISTORE_2: case OP_ASTORE_2:
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            loc_lo[cur_lb + 2] = alo;
            loc_hi[cur_lb + 2] = ahi;
            break;
        case OP_ISTORE_3: case OP_ASTORE_3:
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            loc_lo[cur_lb + 3] = alo;
            loc_hi[cur_lb + 3] = ahi;
            break;

        case OP_IASTORE: case OP_AASTORE: {
            uint16_t addr, arr, arrhi;
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, blo, bhi);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, arr, arrhi);
            (void)arrhi;
            addr = (uint16_t)(arr + 4 + blo * 4);
            w16(addr, alo);
            w16((uint16_t)(addr + 2), ahi);
            break;
        }

        case OP_POP:
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            (void)alo;
            (void)ahi;
            break;
        case OP_DUP:
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced,
                         tos_lo, tos_hi, tos_lo, tos_hi);
            break;
        case OP_SWAP: {
            uint8_t u = (uint8_t)(sp - 2);
            uint16_t second_lo = stk_lo[u];
            uint16_t second_hi = stk_hi[u];
            stk_lo[u] = tos_lo;
            stk_hi[u] = tos_hi;
            tos_lo = second_lo;
            tos_hi = second_hi;
            tos_valid = 1;
            tos_synced = 0;
            break;
        }

        case OP_IADD: {
            uint16_t rlo;
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, blo, bhi);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            rlo = (uint16_t)(alo + blo);
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced,
                         tos_lo, tos_hi, rlo,
                         (uint16_t)(ahi + bhi + (rlo < alo ? 1 : 0)));
            break;
        }
        case OP_ISUB: {
            uint16_t rlo;
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, blo, bhi);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            rlo = (uint16_t)(alo - blo);
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced,
                         tos_lo, tos_hi, rlo,
                         (uint16_t)(ahi - bhi - (alo < blo ? 1 : 0)));
            break;
        }
        case OP_INEG: {
            uint16_t rlo;
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            rlo = (uint16_t)(~alo + 1);
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced,
                         tos_lo, tos_hi, rlo,
                         (uint16_t)(~ahi + (rlo == 0 ? 1 : 0)));
            break;
        }
        case OP_IAND:
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, blo, bhi);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced,
                         tos_lo, tos_hi, alo & blo, ahi & bhi);
            break;
        case OP_IOR:
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, blo, bhi);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced,
                         tos_lo, tos_hi, alo | blo, ahi | bhi);
            break;
        case OP_IXOR:
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, blo, bhi);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced,
                         tos_lo, tos_hi, alo ^ blo, ahi ^ bhi);
            break;

        case OP_IINC: {
            uint8_t idx = PJVM_HOT_RU1(code, pc);
            int8_t v = (int8_t)PJVM_HOT_RU1(code, pc);
            uint8_t i = cur_lb + idx;
            uint16_t old = loc_lo[i];
            uint16_t inc = (uint16_t)(int16_t)v;
            uint16_t nlo = (uint16_t)(old + inc);
            uint16_t carry = (v >= 0) ? (nlo < old ? 1 : 0)
                                      : (nlo > old ? (uint16_t)-1 : 0);
            loc_lo[i] = nlo;
            loc_hi[i] = (uint16_t)(loc_hi[i] + (v < 0 ? 0xFFFF : 0) + carry);
            break;
        }

        case OP_IMUL: {
            int32_t r;
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, blo, bhi);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            r = pjvm_to32(alo, ahi) * pjvm_to32(blo, bhi);
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced,
                         tos_lo, tos_hi, (uint16_t)r,
                         (uint16_t)((uint32_t)r >> 16));
            break;
        }
        case OP_IDIV: {
            int32_t r;
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, blo, bhi);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            r = pjvm_to32(alo, ahi) / pjvm_to32(blo, bhi);
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced,
                         tos_lo, tos_hi, (uint16_t)r,
                         (uint16_t)((uint32_t)r >> 16));
            break;
        }
        case OP_IREM: {
            int32_t r;
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, blo, bhi);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            r = pjvm_to32(alo, ahi) % pjvm_to32(blo, bhi);
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced,
                         tos_lo, tos_hi, (uint16_t)r,
                         (uint16_t)((uint32_t)r >> 16));
            break;
        }
        case OP_ISHL: {
            int32_t r;
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, blo, bhi);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            r = pjvm_to32(alo, ahi) << (blo & 0x1F);
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced,
                         tos_lo, tos_hi, (uint16_t)r,
                         (uint16_t)((uint32_t)r >> 16));
            break;
        }
        case OP_ISHR: {
            int32_t r;
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, blo, bhi);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            r = pjvm_to32(alo, ahi) >> (blo & 0x1F);
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced,
                         tos_lo, tos_hi, (uint16_t)r,
                         (uint16_t)((uint32_t)r >> 16));
            break;
        }
        case OP_IUSHR: {
            uint32_t r;
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, blo, bhi);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            r = (uint32_t)pjvm_to32(alo, ahi) >> (blo & 0x1F);
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced,
                         tos_lo, tos_hi, (uint16_t)r, (uint16_t)(r >> 16));
            break;
        }

        case OP_I2B: {
            int8_t v;
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            v = (int8_t)alo;
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced,
                         tos_lo, tos_hi, (uint16_t)(int16_t)v, v < 0 ? 0xFFFF : 0);
            break;
        }
        case OP_I2C:
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced,
                         tos_lo, tos_hi, alo, 0);
            break;
        case OP_I2S:
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced,
                         tos_lo, tos_hi, alo, (int16_t)alo < 0 ? 0xFFFF : 0);
            break;

        case OP_IFEQ: {
            int16_t o;
            PJVM_HOT_RS2(code, pc, o);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            if (alo == 0 && ahi == 0) pc = (uint16_t)(opc + o);
            break;
        }
        case OP_IFNE: {
            int16_t o;
            PJVM_HOT_RS2(code, pc, o);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            if (alo != 0 || ahi != 0) pc = (uint16_t)(opc + o);
            break;
        }
        case OP_IFLT: {
            int16_t o;
            PJVM_HOT_RS2(code, pc, o);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            if (ahi & 0x8000) pc = (uint16_t)(opc + o);
            break;
        }
        case OP_IFGE: {
            int16_t o;
            PJVM_HOT_RS2(code, pc, o);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            if (!(ahi & 0x8000)) pc = (uint16_t)(opc + o);
            break;
        }
        case OP_IFGT: {
            int16_t o;
            PJVM_HOT_RS2(code, pc, o);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            if (!(ahi & 0x8000) && (alo | ahi)) pc = (uint16_t)(opc + o);
            break;
        }
        case OP_IFLE: {
            int16_t o;
            PJVM_HOT_RS2(code, pc, o);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            if ((ahi & 0x8000) || (alo == 0 && ahi == 0)) pc = (uint16_t)(opc + o);
            break;
        }
        case OP_IFNULL: {
            int16_t o;
            PJVM_HOT_RS2(code, pc, o);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            if (alo == 0 && ahi == 0) pc = (uint16_t)(opc + o);
            break;
        }
        case OP_IFNONNULL: {
            int16_t o;
            PJVM_HOT_RS2(code, pc, o);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            if (alo != 0 || ahi != 0) pc = (uint16_t)(opc + o);
            break;
        }

        case OP_IF_ICMPEQ: {
            int16_t o;
            PJVM_HOT_RS2(code, pc, o);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, blo, bhi);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            if (alo == blo && ahi == bhi) pc = (uint16_t)(opc + o);
            break;
        }
        case OP_IF_ICMPNE: {
            int16_t o;
            PJVM_HOT_RS2(code, pc, o);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, blo, bhi);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            if (alo != blo || ahi != bhi) pc = (uint16_t)(opc + o);
            break;
        }
        case OP_IF_ICMPLT: {
            int16_t o;
            PJVM_HOT_RS2(code, pc, o);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, blo, bhi);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            if ((int16_t)ahi < (int16_t)bhi ||
                (ahi == bhi && alo < blo)) {
                pc = (uint16_t)(opc + o);
            }
            break;
        }
        case OP_IF_ICMPGE: {
            int16_t o;
            PJVM_HOT_RS2(code, pc, o);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, blo, bhi);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            if ((int16_t)ahi > (int16_t)bhi ||
                (ahi == bhi && alo >= blo)) {
                pc = (uint16_t)(opc + o);
            }
            break;
        }
        case OP_IF_ICMPGT: {
            int16_t o;
            PJVM_HOT_RS2(code, pc, o);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, blo, bhi);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            if ((int16_t)ahi > (int16_t)bhi ||
                (ahi == bhi && alo > blo)) {
                pc = (uint16_t)(opc + o);
            }
            break;
        }
        case OP_IF_ICMPLE: {
            int16_t o;
            PJVM_HOT_RS2(code, pc, o);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, blo, bhi);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            if ((int16_t)ahi < (int16_t)bhi ||
                (ahi == bhi && alo <= blo)) {
                pc = (uint16_t)(opc + o);
            }
            break;
        }
        case OP_IF_ACMPEQ: {
            int16_t o;
            PJVM_HOT_RS2(code, pc, o);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, blo, bhi);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            if (alo == blo) pc = (uint16_t)(opc + o);
            break;
        }
        case OP_IF_ACMPNE: {
            int16_t o;
            PJVM_HOT_RS2(code, pc, o);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, blo, bhi);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            if (alo != blo) pc = (uint16_t)(opc + o);
            break;
        }
        case OP_GOTO: {
            int16_t o;
            PJVM_HOT_RS2(code, pc, o);
            pc = (uint16_t)(opc + o);
            break;
        }

        case OP_IRETURN: case OP_ARETURN:
            PJVM_TOS_FLUSH(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi);
            PJVM_FLUSH_HOT(hot, pc, cur_cb, cur_mi, cur_lb, sp, lt, sp_max);
            pjvm_ret(j, 1);
            PJVM_RELOAD_HOT(hot, pc, cur_cb, cur_mi, cur_lb, sp, lt, sp_max);
            PJVM_TOS_RELOAD(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi);
            break;
        case OP_RETURN:
            PJVM_TOS_FLUSH(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi);
            PJVM_FLUSH_HOT(hot, pc, cur_cb, cur_mi, cur_lb, sp, lt, sp_max);
            pjvm_ret(j, 0);
            PJVM_RELOAD_HOT(hot, pc, cur_cb, cur_mi, cur_lb, sp, lt, sp_max);
            PJVM_TOS_RELOAD(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi);
            break;

        case OP_GETSTATIC: {
            uint16_t i;
            uint8_t s;
            PJVM_HOT_RU2(code, pc, i);
            s = PJVM_HOT_RCP(cp_map, cur_cb, i);
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced,
                         tos_lo, tos_hi, sf_lo[s], sf_hi[s]);
            break;
        }
        case OP_PUTSTATIC: {
            uint16_t i;
            uint8_t s;
            PJVM_HOT_RU2(code, pc, i);
            s = PJVM_HOT_RCP(cp_map, cur_cb, i);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            sf_lo[s] = alo;
            sf_hi[s] = ahi;
            break;
        }

        case OP_GETFIELD: {
            uint16_t i, addr;
            uint8_t s;
            PJVM_HOT_RU2(code, pc, i);
            s = PJVM_HOT_RCP(cp_map, cur_cb, i);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            addr = (uint16_t)(alo + 4 + s * 4);
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced,
                         tos_lo, tos_hi, r16(addr), r16((uint16_t)(addr + 2)));
            break;
        }
        case OP_PUTFIELD: {
            uint16_t i, addr;
            uint8_t s;
            PJVM_HOT_RU2(code, pc, i);
            s = PJVM_HOT_RCP(cp_map, cur_cb, i);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, blo, bhi);
            addr = (uint16_t)(blo + 4 + s * 4);
            w16(addr, alo);
            w16((uint16_t)(addr + 2), ahi);
            break;
        }

        case OP_INVOKESTATIC: case OP_INVOKESPECIAL: {
            uint16_t i;
            PJVM_HOT_RU2(code, pc, i);
            PJVM_TOS_FLUSH(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi);
            PJVM_FLUSH_HOT(hot, pc, cur_cb, cur_mi, cur_lb, sp, lt, sp_max);
            pjvm_inv(j, PJVM_HOT_RCP(cp_map, cur_cb, i));
            PJVM_RELOAD_HOT(hot, pc, cur_cb, cur_mi, cur_lb, sp, lt, sp_max);
            PJVM_TOS_RELOAD(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi);
            break;
        }
        case OP_INVOKEVIRTUAL: {
            uint16_t i;
            uint8_t bmi, vs, ci, arg_idx;
            uint16_t objref;
            PJVM_HOT_RU2(code, pc, i);
            bmi = PJVM_HOT_RCP(cp_map, cur_cb, i);
            vs = m_vs[bmi];
            if (vs == 0xFF) {
                PJVM_TOS_FLUSH(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi);
                PJVM_FLUSH_HOT(hot, pc, cur_cb, cur_mi, cur_lb, sp, lt, sp_max);
                pjvm_inv(j, bmi);
                PJVM_RELOAD_HOT(hot, pc, cur_cb, cur_mi, cur_lb, sp, lt, sp_max);
                PJVM_TOS_RELOAD(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi);
            } else {
                arg_idx = (uint8_t)(sp - m_ac[bmi]);
                objref = PJVM_TOS_ABS_LO(stk_lo, sp, tos_valid, tos_lo, arg_idx);
                ci = (uint8_t)r16(objref);
                PJVM_TOS_FLUSH(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi);
                PJVM_FLUSH_HOT(hot, pc, cur_cb, cur_mi, cur_lb, sp, lt, sp_max);
                pjvm_inv(j, vt[cls_vb[ci] + vs]);
                PJVM_RELOAD_HOT(hot, pc, cur_cb, cur_mi, cur_lb, sp, lt, sp_max);
                PJVM_TOS_RELOAD(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi);
            }
            break;
        }

        case OP_NEW: {
            uint16_t i, a;
            uint8_t ci, nf;
            PJVM_HOT_RU2(code, pc, i);
            ci = PJVM_HOT_RCP(cp_map, cur_cb, i);
            nf = ci < n_classes ? cls_nf[ci] : 0;
            a = heap_alloc(j, (uint16_t)(4 + nf * 4));
            w16(a, ci);
            w16((uint16_t)(a + 2), 0);
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced,
                         tos_lo, tos_hi, a, 0);
            break;
        }
        case OP_NEWARRAY: {
            uint8_t t = PJVM_HOT_RU1(code, pc);
            uint16_t a;
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            a = heap_alloc(j, (uint16_t)(4 + alo * 4));
            w16(a, alo);
            w16((uint16_t)(a + 2), 0);
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced,
                         tos_lo, tos_hi, a, 0);
            (void)t;
            break;
        }
        case OP_ANEWARRAY: {
            uint16_t i, a;
            PJVM_HOT_RU2(code, pc, i);
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            a = heap_alloc(j, (uint16_t)(4 + alo * 4));
            w16(a, alo);
            w16((uint16_t)(a + 2), 0);
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced,
                         tos_lo, tos_hi, a, 0);
            (void)i;
            break;
        }
        case OP_ARRAYLENGTH:
            PJVM_TOS_POP(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi, alo, ahi);
            PJVM_TOS_PUSH(stk_lo, stk_hi, sp, sp_max, tos_valid, tos_synced,
                         tos_lo, tos_hi, r16(alo), r16((uint16_t)(alo + 2)));
            break;

        default:
            PJVM_TOS_FLUSH(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi);
            PJVM_FLUSH_HOT(hot, pc, cur_cb, cur_mi, cur_lb, sp, lt, sp_max);
            pjvm_platform_trap(op, opc);
            return;
        }
    }

    PJVM_TOS_FLUSH(stk_lo, stk_hi, sp, tos_valid, tos_synced, tos_lo, tos_hi);
    PJVM_FLUSH_HOT(hot, pc, cur_cb, cur_mi, cur_lb, sp, lt, sp_max);
}

#endif
