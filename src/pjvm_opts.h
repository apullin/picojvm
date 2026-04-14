/*
 * pjvm_opts.h -- local size/speed options for picoJVM builds.
 *
 * The default policy is:
 * - host / generic builds keep the plain C helpers
 * - 8085 ASM-helper builds may opt specific helpers into assembly
 */
#ifndef PJVM_OPTS_H
#define PJVM_OPTS_H

#define PJVM_HEAP_BUMP      0
#define PJVM_HEAP_FREELIST  1

#ifndef PJVM_HEAP_MODE
#define PJVM_HEAP_MODE PJVM_HEAP_BUMP
#endif

#define PJVM_GC_TRIG_ALLOC_FAIL              0x01
#define PJVM_GC_TRIG_WATERMARK               0x02
#define PJVM_GC_TRIG_RETURN                  0x04
#define PJVM_GC_TRIG_RANDOM_ABOVE_WATERMARK  0x08

#ifndef PJVM_GC_TRIGGERS
#define PJVM_GC_TRIGGERS 0
#endif

#ifndef PJVM_GC_WATERMARK_PCT
#define PJVM_GC_WATERMARK_PCT 75
#endif

#ifndef PJVM_GC_RANDOM_MASK
#define PJVM_GC_RANDOM_MASK 0x0007u
#endif

#if PJVM_GC_TRIGGERS
#define PJVM_GC_ENABLED 1
#else
#define PJVM_GC_ENABLED 0
#endif

/* Reduced-opcode profile for the current selfhost/disk corpus.
 * This intentionally drops standard handlers that the present selfhosted
 * compiler and its generated test corpus do not emit. It is not suitable for
 * arbitrary javac-packed programs.
 *
 * PJVM_PROFILE_PICOJC_ONLY is kept as a compatibility alias for older local
 * builds that used the earlier name. */
#ifndef PJVM_PROFILE_SELFHOST_SET
#ifdef PJVM_PROFILE_PICOJC_ONLY
#define PJVM_PROFILE_SELFHOST_SET PJVM_PROFILE_PICOJC_ONLY
#else
#define PJVM_PROFILE_SELFHOST_SET 0
#endif
#endif

#ifndef PJVM_USE_OP_POP2
#if PJVM_PROFILE_SELFHOST_SET
#define PJVM_USE_OP_POP2      0
#else
#define PJVM_USE_OP_POP2      1
#endif
#endif

#ifndef PJVM_USE_OP_DUP_X1
#if PJVM_PROFILE_SELFHOST_SET
#define PJVM_USE_OP_DUP_X1    0
#else
#define PJVM_USE_OP_DUP_X1    1
#endif
#endif

#ifndef PJVM_USE_OP_SWAP
#if PJVM_PROFILE_SELFHOST_SET
#define PJVM_USE_OP_SWAP      0
#else
#define PJVM_USE_OP_SWAP      1
#endif
#endif

#ifndef PJVM_USE_OP_IFLT
#if PJVM_PROFILE_SELFHOST_SET
#define PJVM_USE_OP_IFLT      0
#else
#define PJVM_USE_OP_IFLT      1
#endif
#endif

#ifndef PJVM_USE_OP_IFGE
#if PJVM_PROFILE_SELFHOST_SET
#define PJVM_USE_OP_IFGE      0
#else
#define PJVM_USE_OP_IFGE      1
#endif
#endif

#ifndef PJVM_USE_OP_IFGT
#if PJVM_PROFILE_SELFHOST_SET
#define PJVM_USE_OP_IFGT      0
#else
#define PJVM_USE_OP_IFGT      1
#endif
#endif

#ifndef PJVM_USE_OP_IFLE
#if PJVM_PROFILE_SELFHOST_SET
#define PJVM_USE_OP_IFLE      0
#else
#define PJVM_USE_OP_IFLE      1
#endif
#endif

#ifndef PJVM_USE_OP_IFNULL
#if PJVM_PROFILE_SELFHOST_SET
#define PJVM_USE_OP_IFNULL    0
#else
#define PJVM_USE_OP_IFNULL    1
#endif
#endif

#ifndef PJVM_USE_OP_IFNONNULL
#if PJVM_PROFILE_SELFHOST_SET
#define PJVM_USE_OP_IFNONNULL 0
#else
#define PJVM_USE_OP_IFNONNULL 1
#endif
#endif

#if defined(PJVM_ASM_HELPERS) && !defined(PJVM_PAGED)
#define PJVM_USE_ASM_CPREAD           1
#define PJVM_USE_ASM_ROM_STRING_DATA  1
#define PJVM_USE_ASM_STRING_LEN       1
#define PJVM_USE_ASM_STRING_BYTE      1
#define PJVM_USE_ASM_ARRAYCOPY        1
#define PJVM_USE_ASM_MEMCMP           1
#define PJVM_USE_ASM_WRITE_BYTES      1
#define PJVM_USE_ASM_STRING_FROM_BYTES 1
#else
#define PJVM_USE_ASM_CPREAD           0
#define PJVM_USE_ASM_ROM_STRING_DATA  0
#define PJVM_USE_ASM_STRING_LEN       0
#define PJVM_USE_ASM_STRING_BYTE      0
#define PJVM_USE_ASM_ARRAYCOPY        0
#define PJVM_USE_ASM_MEMCMP           0
#define PJVM_USE_ASM_WRITE_BYTES      0
#define PJVM_USE_ASM_STRING_FROM_BYTES 0
#endif

#endif /* PJVM_OPTS_H */
