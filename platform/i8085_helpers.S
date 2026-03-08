/*
 * i8085_helpers.S — Hand-written 8085 assembly for picoJVM hot helpers.
 *
 * Replaces compiler-generated versions of spush/spop_lo/spop_hi/lload/
 * lstore/bcread/bread/cpread.  Each is a normal C-callable function
 * (args on stack, 16-bit return in BC, 8-bit return in A).
 *
 * Offsets below assume sim-build CAP values:
 *   STACK=64, LOCALS=128, STATIC=32, FRAMES=16
 *
 * PJVMCtx field offsets (i8085, PJVMFrame=9 bytes):
 *   stk_lo   =    0   (64 × 2 = 128 bytes)
 *   stk_hi   =  128   (64 × 2 = 128 bytes)
 *   loc_lo   =  256   (128 × 2 = 256 bytes)
 *   loc_hi   =  512   (128 × 2 = 256 bytes)
 *   frames   =  896   (16 × 9 = 144 bytes)
 *   pc       = 1040   (uint32_t; ASM reads low 16 bits only)
 *   cur_cb   = 1044
 *   sp       = 1046
 *   lt       = 1047
 *   cur_mi   = 1048
 *   cur_lb   = 1049
 *   fdepth   = 1050
 *   heap_ptr = 1051
 */

    .text

/* ---- offsets --------------------------------------------------------- */
.set OFF_STK_HI,  128
.set OFF_LOC_LO,  256
.set OFF_LOC_HI,  512
.set OFF_PC,      1040
.set OFF_CUR_CB,  1044
.set OFF_SP,      1046
.set OFF_CUR_LB,  1049

/* ====================================================================== */
/*  _spush_bcde — internal entry: push BC=lo, DE=hi onto operand stack    */
/*  Clobbers A, HL.  Preserves nothing.                                   */
/* ====================================================================== */
    .type   _spush_bcde, @function
_spush_bcde:
    PUSH D                          ; save hi
    PUSH B                          ; save lo
    LHLD g_pjvm
    LXI D, OFF_SP
    DAD D                           ; HL = &sp
    MOV A, M
    INR M                           ; sp++
    ADD A                           ; A = sp*2
    MOV E, A
    MVI D, 0                        ; DE = sp*2
    LHLD g_pjvm                        ; HL = &stk_lo[0]
    DAD D                           ; HL = &stk_lo[sp]
    POP D                           ; DE = lo (was BC)
    MOV M, E
    INX H
    MOV M, D                        ; stk_lo[sp] = lo
    LXI D, OFF_STK_HI - 1
    DAD D                           ; HL = &stk_hi[sp]
    POP D                           ; DE = hi
    MOV M, E
    INX H
    MOV M, D                        ; stk_hi[sp] = hi
    RET

/* ====================================================================== */
/*  spush(uint16_t lo, uint16_t hi) — C-callable operand stack push       */
/*  Args: lo=[SP+2], hi=[SP+4]                                           */
/* ====================================================================== */
    .globl  spush
    .type   spush, @function
spush:
    LXI H, 2
    DAD SP
    MOV C, M
    INX H
    MOV B, M                        ; BC = lo
    INX H
    MOV E, M
    INX H
    MOV D, M                        ; DE = hi
    JMP _spush_bcde

/* ====================================================================== */
/*  spop_lo(void) → uint16_t in BC                                       */
/*  Decrements sp, returns stk_lo[sp].                                   */
/* ====================================================================== */
    .globl  spop_lo
    .type   spop_lo, @function
spop_lo:
    LHLD g_pjvm
    LXI D, OFF_SP
    DAD D                           ; HL = &sp
    DCR M                           ; sp--
    MOV A, M                        ; A = new sp
    ADD A                           ; A = sp*2
    MOV E, A
    MVI D, 0                        ; DE = sp*2
    LHLD g_pjvm                        ; HL = &stk_lo[0]
    DAD D                           ; HL = &stk_lo[sp]
    MOV C, M
    INX H
    MOV B, M                        ; BC = stk_lo[sp]
    RET

/* ====================================================================== */
/*  spop_hi(void) → uint16_t in BC                                       */
/*  Returns stk_hi[sp] (call after spop_lo; sp already decremented).     */
/* ====================================================================== */
    .globl  spop_hi
    .type   spop_hi, @function
spop_hi:
    LHLD g_pjvm
    LXI D, OFF_SP
    DAD D                           ; HL = &sp
    MOV A, M                        ; A = sp (already decremented)
    ADD A                           ; A = sp*2
    MOV E, A
    MVI D, 0
    LHLD g_pjvm
    LXI B, OFF_STK_HI
    DAD B
    DAD D                           ; HL = &stk_hi[sp]
    MOV C, M
    INX H
    MOV B, M                        ; BC = stk_hi[sp]
    RET

/* ====================================================================== */
/*  bcread(void) → uint8_t in A                                          */
/*  Returns bc[pc++].                                                     */
/* ====================================================================== */
    .globl  bcread
    .type   bcread, @function
bcread:
    LHLD g_pjvm
    LXI D, OFF_PC
    DAD D                           ; HL = &pc
    MOV E, M
    INX H
    MOV D, M                        ; DE = pc
    INX D                           ; DE = pc+1
    MOV M, D
    DCX H
    MOV M, E                        ; store pc+1
    DCX D                           ; DE = old pc
    LHLD (bc)
    DAD D                           ; HL = &bc[old pc]
    MOV A, M                        ; A = byte
    RET

/* ====================================================================== */
/*  bread(void) → int16_t in BC                                          */
/*  Reads big-endian 2-byte branch offset, advances pc by 2.             */
/* ====================================================================== */
    .globl  bread
    .type   bread, @function
bread:
    LHLD g_pjvm
    LXI D, OFF_PC
    DAD D                           ; HL = &pc
    MOV E, M
    INX H
    MOV D, M                        ; DE = pc
    INX D
    INX D                           ; DE = pc+2
    MOV M, D
    DCX H
    MOV M, E                        ; store pc+2
    DCX D
    DCX D                           ; DE = old pc
    LHLD (bc)
    DAD D                           ; HL = &bc[pc]
    MOV B, M                        ; B = bc[pc] (high byte)
    INX H
    MOV C, M                        ; C = bc[pc+1] (low byte)
    RET

/* ====================================================================== */
/*  cpread(void) → uint8_t in A                                          */
/*  Reads 2-byte CP index, resolves through cpr[cur_cb + index].         */
/* ====================================================================== */
    .globl  cpread
    .type   cpread, @function
cpread:
    /* Read 2-byte big-endian index and advance pc by 2 */
    LHLD g_pjvm
    LXI D, OFF_PC
    DAD D                           ; HL = &pc
    MOV E, M
    INX H
    MOV D, M                        ; DE = pc
    INX D
    INX D                           ; DE = pc+2
    MOV M, D
    DCX H
    MOV M, E                        ; store pc+2
    DCX D
    DCX D                           ; DE = old pc
    LHLD (bc)
    DAD D                           ; HL = &bc[pc]
    MOV D, M                        ; D = high byte
    INX H
    MOV E, M                        ; E = low byte
    /* DE = CP index (big-endian → native 16-bit) */

    /* Add cur_cb */
    LHLD g_pjvm
    PUSH D                          ; save CP index
    LXI D, OFF_CUR_CB
    DAD D                           ; HL = &cur_cb
    MOV E, M
    INX H
    MOV D, M                        ; DE = cur_cb
    POP H                           ; HL = CP index
    DAD D                           ; HL = cur_cb + CP_index
    XCHG                            ; DE = cur_cb + CP_index

    /* cpr[cur_cb + index] */
    LHLD cpr
    DAD D                           ; HL = &cpr[cur_cb + index]
    MOV A, M                        ; A = resolved value
    RET

/* ====================================================================== */
/*  lload(uint8_t slot) — load local onto operand stack                   */
/*  Args: slot=[SP+2] (low byte)                                         */
/* ====================================================================== */
    .globl  lload
    .type   lload, @function
lload:
    LXI H, 2
    DAD SP
    MOV A, M                        ; A = slot

    LHLD g_pjvm
    LXI D, OFF_CUR_LB
    DAD D
    ADD M                           ; A = cur_lb + slot = i
    ADD A                           ; A = i*2
    MOV E, A
    MVI D, 0                        ; DE = i*2

    /* Load loc_lo[i] */
    LHLD g_pjvm
    LXI B, OFF_LOC_LO
    DAD B
    DAD D                           ; HL = &loc_lo[i]
    MOV C, M
    INX H
    MOV B, M                        ; BC = loc_lo[i]

    /* Load loc_hi[i]: &loc_hi = &loc_lo + 256, from HL+1 that's +255 */
    PUSH B                          ; save lo
    DCX H
    INR H                           ; HL = &loc_hi[i]  (add 255 via +256-1)
    MOV E, M
    INX H
    MOV D, M                        ; DE = loc_hi[i]
    POP B                           ; BC = lo

    JMP _spush_bcde

/* ====================================================================== */
/*  lstore(uint8_t slot) — pop operand stack into local                   */
/*  Args: slot=[SP+2] (low byte)                                         */
/* ====================================================================== */
    .globl  lstore
    .type   lstore, @function
lstore:
    /* Compute i = cur_lb + slot */
    LXI H, 2
    DAD SP
    MOV A, M                        ; A = slot

    LHLD g_pjvm
    LXI D, OFF_CUR_LB
    DAD D
    ADD M                           ; A = i
    PUSH PSW                        ; save i

    /* sp-- and get sp*2 */
    LHLD g_pjvm
    LXI D, OFF_SP
    DAD D
    DCR M                           ; sp--
    MOV A, M                        ; A = new sp
    ADD A                           ; sp*2
    MOV E, A
    MVI D, 0                        ; DE = sp*2

    /* Read stk_lo[sp] */
    LHLD g_pjvm
    DAD D                           ; HL = &stk_lo[sp]
    MOV C, M
    INX H
    MOV B, M                        ; BC = stk_lo[sp]

    /* Read stk_hi[sp] */
    PUSH B                          ; save stk_lo value
    LXI B, OFF_STK_HI - 1
    DAD B                           ; HL = &stk_hi[sp]
    MOV E, M
    INX H
    MOV D, M                        ; DE = stk_hi[sp]

    /* Compute &loc_lo[i] and store both halves */
    POP B                           ; BC = stk_lo value
    POP PSW                         ; A = i
    PUSH D                          ; save stk_hi value
    ADD A                           ; A = i*2
    MOV E, A
    MVI D, 0                        ; DE = i*2
    LHLD g_pjvm
    PUSH B                          ; save stk_lo value
    LXI B, OFF_LOC_LO
    DAD B
    DAD D                           ; HL = &loc_lo[i]
    POP B                           ; BC = stk_lo value
    MOV M, C
    INX H
    MOV M, B                        ; loc_lo[i] = stk_lo[sp]

    /* loc_hi[i] = HL - 1 + 256 */
    DCX H
    INR H                           ; HL = &loc_hi[i]
    POP D                           ; DE = stk_hi value
    MOV M, E
    INX H
    MOV M, D                        ; loc_hi[i] = stk_hi[sp]
    RET
