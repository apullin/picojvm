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
 * PJVMCtx field offsets (i8085, PJVMFrame=11 bytes):
 *   stk_lo   =    0   (64 × 2 = 128 bytes)
 *   stk_hi   =  128   (64 × 2 = 128 bytes)
 *   loc_lo   =  256   (128 × 2 = 256 bytes)
 *   loc_hi   =  512   (128 × 2 = 256 bytes)
 *   sf_lo    =  768   (32 × 2 = 64 bytes)
 *   sf_hi    =  832   (32 × 2 = 64 bytes)
 *   frames   =  896   (16 × 11 = 176 bytes)
 *   pc       = 1072   (uint32_t; ASM reads low 16 bits only)
 *   cur_cb   = 1076   (uint16_t)
 *   sp       = 1078   (uint16_t; ASM reads low byte only, max 64)
 *   lt       = 1080   (uint16_t)
 *   cur_lb   = 1082   (uint16_t; ASM reads low byte only, max 128)
 *   cur_mi   = 1084   (uint8_t)
 *   fdepth   = 1085   (int8_t)
 *   heap_ptr = 1086   (uint16_t)
 */

    .text

/* ---- offsets --------------------------------------------------------- */
.set OFF_STK_HI,  128
.set OFF_LOC_LO,  256
.set OFF_LOC_HI,  512
.set OFF_PC,      1072
.set OFF_CUR_CB,  1076
.set OFF_SP,      1078
.set OFF_CUR_LB,  1082

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

/* ====================================================================== */
/*  Native handler ASM implementations                                    */
/*                                                                        */
/*  Each pops its own JVM operand-stack args via spop_lo (skipping the    */
/*  spop_hi calls since the hi halves are unused for these int args).     */
/*  On the sim platform, r8/w8 are direct memory access — so inner loops  */
/*  use LDAX/MOV M,A instead of calling r8/w8.                            */
/* ====================================================================== */

/* ====================================================================== */
/*  pjvm_native_arraycopy(void)                                           */
/*  JVM args: src(ref), srcOff(int), dst(ref), dstOff(int), len(int)      */
/*  Pops in reverse: len, dstOff, dst, srcOff, src                        */
/* ====================================================================== */
    .globl  pjvm_native_arraycopy
    .type   pjvm_native_arraycopy, @function
pjvm_native_arraycopy:
    CALL spop_lo                    ; BC = len
    PUSH B
    CALL spop_lo                    ; BC = dstOff
    PUSH B
    CALL spop_lo                    ; BC = dst
    PUSH B
    CALL spop_lo                    ; BC = srcOff
    PUSH B
    CALL spop_lo                    ; BC = src

    /* src_addr = src + 4 + srcOff */
    POP D                           ; DE = srcOff
    MOV H, B
    MOV L, C                        ; HL = src
    DAD D
    LXI D, 4
    DAD D                           ; HL = src + srcOff + 4
    XCHG                            ; DE = src_addr

    /* dst_addr = dst + 4 + dstOff */
    POP H                           ; HL = dst
    POP B                           ; BC = dstOff
    DAD B
    LXI B, 4
    DAD B                           ; HL = dst + dstOff + 4
    /* HL = dst_addr, DE = src_addr */

    POP B                           ; BC = len
    MOV A, B
    ORA C
    RZ                              ; len == 0 → done
.Lac_loop:
    LDAX D                          ; A = *src
    MOV M, A                        ; *dst = A
    INX H
    INX D
    DCX B
    MOV A, B
    ORA C
    JNZ .Lac_loop
    RET

/* ====================================================================== */
/*  pjvm_native_memcmp(void)                                              */
/*  JVM args: aref, aOff(int), bref, bOff(int), len(int)                 */
/*  Pops in reverse: len, bOff, bref, aOff, aref                         */
/*  Pushes int32_t result (sign of first difference) via _spush_bcde.     */
/* ====================================================================== */
    .globl  pjvm_native_memcmp
    .type   pjvm_native_memcmp, @function
pjvm_native_memcmp:
    CALL spop_lo                    ; BC = len
    PUSH B
    CALL spop_lo                    ; BC = bOff
    PUSH B
    CALL spop_lo                    ; BC = bref
    PUSH B
    CALL spop_lo                    ; BC = aOff
    PUSH B
    CALL spop_lo                    ; BC = aref

    /* a_addr = aref + aOff + 4 */
    POP D                           ; DE = aOff
    MOV H, B
    MOV L, C                        ; HL = aref
    DAD D
    LXI D, 4
    DAD D                           ; HL = a_addr
    XCHG                            ; DE = a_addr

    /* b_addr = bref + bOff + 4 */
    POP H                           ; HL = bref
    POP B                           ; BC = bOff
    DAD B
    LXI B, 4
    DAD B                           ; HL = b_addr

    POP B                           ; BC = len
    /* DE = a_addr, HL = b_addr, BC = len */
    MOV A, B
    ORA C
    JZ .Lmc_equal                   ; len == 0 → equal

    /* Push len onto HW stack — loop needs BC for temp */
    PUSH B
.Lmc_loop:
    LDAX D                          ; A = *a_ptr
    CMP M                           ; compare with *b_ptr
    JNZ .Lmc_diff
    INX D
    INX H
    /* Decrement counter */
    POP B
    DCX B
    MOV A, B
    ORA C
    PUSH B
    JNZ .Lmc_loop
    POP B                           ; clean counter
.Lmc_equal:
    /* result = 0 → spush(0, 0) */
    LXI B, 0
    LXI D, 0
    JMP _spush_bcde
.Lmc_diff:
    /* A = av (CMP doesn't modify A on 8085), M = bv */
    POP B                           ; clean counter
    SUB M                           ; A = av - bv (signed 8-bit)
    MOV C, A
    MVI B, 0
    JNC .Lmc_pos                    ; if av >= bv, no sign extend
    MVI B, 0xFF                     ; sign extend negative
.Lmc_pos:
    /* BC = (int16_t)(av - bv), DE = sign extend */
    MOV D, B
    MOV E, B
    JMP _spush_bcde

/* ====================================================================== */
/*  pjvm_native_write_bytes(void)                                         */
/*  JVM args: ref, off(int), len(int)                                     */
/*  Pops in reverse: len, off, ref                                        */
/* ====================================================================== */
    .globl  pjvm_native_write_bytes
    .type   pjvm_native_write_bytes, @function
pjvm_native_write_bytes:
    CALL spop_lo                    ; BC = len
    PUSH B
    CALL spop_lo                    ; BC = off
    PUSH B
    CALL spop_lo                    ; BC = ref

    /* addr = ref + off + 4 */
    POP D                           ; DE = off
    MOV H, B
    MOV L, C                        ; HL = ref
    DAD D
    LXI D, 4
    DAD D                           ; HL = addr
    POP B                           ; BC = len

    MOV A, B
    ORA C
    RZ                              ; len == 0 → done
.Lwb_loop:
    MOV A, M                        ; A = *ptr
    INX H                           ; advance ptr
    PUSH H                          ; save ptr
    PUSH B                          ; save count
    /* pjvm_platform_putchar(A) — arg at [SP+2] */
    MVI H, 0
    MOV L, A
    PUSH H                          ; push arg
    CALL pjvm_platform_putchar
    POP H                           ; clean arg
    POP B                           ; restore count
    POP H                           ; restore ptr
    DCX B
    MOV A, B
    ORA C
    JNZ .Lwb_loop
    RET

/* ====================================================================== */
/*  pjvm_native_string_from_bytes(void)                                   */
/*  JVM args: src(ref), off(int), len(int)                                */
/*  Pops in reverse: len, off, src                                        */
/*  Allocates String object (4-byte header + data), pushes ref.           */
/* ====================================================================== */
    .globl  pjvm_native_string_from_bytes
    .type   pjvm_native_string_from_bytes, @function
pjvm_native_string_from_bytes:
    CALL spop_lo                    ; BC = len
    PUSH B
    CALL spop_lo                    ; BC = off
    PUSH B
    CALL spop_lo                    ; BC = src

    /* src_addr = src + off + 4 */
    POP D                           ; DE = off
    MOV H, B
    MOV L, C                        ; HL = src
    DAD D
    LXI D, 4
    DAD D                           ; HL = src_addr
    POP B                           ; BC = len

    PUSH B                          ; save len
    PUSH H                          ; save src_addr

    /* heap_alloc(g_pjvm, len + 4) */
    MOV H, B
    MOV L, C
    LXI D, 4
    DAD D                           ; HL = len + 4
    PUSH H                          ; arg: size
    LHLD g_pjvm
    PUSH H                          ; arg: j
    CALL heap_alloc                 ; BC = a (allocated address)
    POP H                           ; clean j
    POP H                           ; clean size

    POP D                           ; DE = src_addr
    POP H                           ; HL = len

    /* Write header: len at a[0..1], 0 at a[2..3] */
    PUSH B                          ; save a
    PUSH D                          ; save src_addr
    PUSH H                          ; save len (need it for header + loop)

    MOV H, B
    MOV L, C                        ; HL = a
    POP D                           ; DE = len
    PUSH D                          ; re-save len
    MOV M, E                        ; a[0] = len_lo
    INX H
    MOV M, D                        ; a[1] = len_hi
    INX H
    MVI M, 0                        ; a[2] = 0
    INX H
    MVI M, 0                        ; a[3] = 0
    INX H                           ; HL = a + 4 = dst

    /* Copy loop: DE=src_addr → HL=dst, BC=len */
    POP B                           ; BC = len
    POP D                           ; DE = src_addr
    MOV A, B
    ORA C
    JZ .Lsfb_done
.Lsfb_loop:
    LDAX D                          ; A = *src
    MOV M, A                        ; *dst = A
    INX H
    INX D
    DCX B
    MOV A, B
    ORA C
    JNZ .Lsfb_loop
.Lsfb_done:
    /* spush(a, 0) */
    POP B                           ; BC = a
    LXI D, 0                        ; DE = 0 (hi)
    JMP _spush_bcde
