# Deploying picoJVM on real hardware

This page is the ROM-budget math: what the VM costs, what each knob buys,
and how to fit the common 8085 memory maps. All numbers are measured at
-Oz with the I8085 MachineOutliner, June 2026.

## The flagship configuration

Small caps, v3 loader, free-list heap + GC (`TRIGGERS=3`), full hardening,
shims-compatible native tier, asm helpers, boot overlay:

| | non-LTO | `TARGET_LTO=1` |
|---|---:|---:|
| `.text` + `.rodata` | 31,007 | 28,769 |
| `.pjvmboot` (loader; ROM bytes, RAM-reclaimed after parse) | 3,065 | 2,698 |
| **ROM total** | **34,072** | **31,467** |
| heap window on flat 64K (to 0xE000) | 24,010 | 26,256 |

The boot overlay means the loader costs ROM but not resident RAM: after
`pjvm_parse` its address range becomes Java heap.

## Core-text reference points (`make size-report`)

| config | core text |
|---|---:|
| small-v3 (no GC) | 24,170 |
| small-v3 + GC | 31,926 |
| big (v3+v4 dual) | 29,137 |
| big v4-only | 27,277 |
| big v4-only + GC | 35,133 |

## The size ladder

Each step is suite-gated and drops no language feature; apply in order of
comfort (non-LTO core-text deltas shown):

1. `TARGET_LTO=1` — ~−2.6K ROM, free heap (see above)
2. `PJVM_MT_IN_IMAGE=1` — −410 text and ~−1K bss (metadata stays in ROM)
3. `PJVM_USE_ENUM_NATIVES=0` — −332, valid when images pack the Enum shim
4. `PJVM_GC_TRIGGERS=1` — −568, collect-on-full-only GC (arguably the
   period-correct policy)
5. `PJVM_USE_FILE_NATIVES=0` / `PJVM_USE_TERM_NATIVES=0` — ~−1.1K
   combined, for diskless/headless deployments

## ROM part targets

- **36K-class allocation (1.5K BIOS/IRQ headroom): met today.** The
  flagship LTO build at ~31.5K fits with several KB to spare, no ladder
  steps needed.
- **32K part (27C256) with 2K BIOS — VM budget 30,720: one ladder step
  away.** Flagship LTO is ~31.3K excluding the embedded test program;
  `MT_IN_IMAGE` (and if needed step 3) closes the rest.

The 8085's RST/IRQ vectors (0x0000–0x003F) are already reserved by the
linker scripts — text starts at 0x40 — so BIOS headroom estimates need not
re-fund vector space.

## Big RAM, big programs

- **Program space is 32-bit by design.** Programs larger than 64K run via
  `PJVM_PAGED` (LRU chunk cache, disk-backed). A banked or extended
  program ROM/RAM is just a faster `pjvm_prog_read` backend — a platform
  change, invisible to the VM.
- **Heap refs are 16-bit by design.** The Java heap lives inside the flat
  64K window; extending it would change ref width through the whole VM and
  collector. Spend extra physical memory on the pager and storage side,
  not the heap side.

## Trap codes worth knowing in the field

`0xF8` division by zero, `0xF9` stack overflow (frames/locals/operands),
`0xFA` unsupported feature in image, `0xFB` bad method, `0xFC` capacity
cap exceeded, `0xFD` bad image version, `0xFF` call into a compiled-out
native group. Platforms additionally trap `0xFE` on heap exhaustion with
the failed allocation size as the operand. The trap hook receives
`(code, operand)` — on the sim the record lands at 0xE080.
