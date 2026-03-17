/*
 * pjvm_opts.h -- local size/speed options for picoJVM builds.
 *
 * The default policy is:
 * - host / generic builds keep the plain C helpers
 * - 8085 ASM-helper builds may opt specific helpers into assembly
 */
#ifndef PJVM_OPTS_H
#define PJVM_OPTS_H

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
