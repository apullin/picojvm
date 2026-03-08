# Exec Plan: Fixed-Page Program Image Pager

## Purpose

Replace the method-granularity region pager with a fixed-size page cache that
covers the entire .pjvm program image. All read-only data (bytecodes, CP
resolution, constants, strings, exception table) is accessed through a unified
`PROG(offset)` macro. Page size and count are runtime-selectable.

**Observable outcome:** `make test` and `make test-paged` pass. Paged mode
uses `PROG()` for all section access. Non-paged `PROG(off) = prog_data[off]`
has zero overhead. Stats printed on exit.

## Context

See `DESIGN-fixed-page-pager.md`. Key points:

- `PROG(offset)` — single macro for all read-only .pjvm data
- Fixed-size pages, power-of-2, runtime-selectable via `page_shift`
- O(1) chunk index via bit shift, small tag scan for slot lookup
- LRU eviction, optional pinning, per-slot miss counters
- No compaction, no variable-size allocation, no fragmentation

---

## Phase 1: Section offsets and `PROG()` macro (non-paged first)

**Goal:** Replace section pointers (`bc`, `cpr`, `ic`, `sc`, `et`) with file
offsets. Introduce `PROG()` macro. Non-paged only — paged mode unchanged.
No behavioral change.

**Edits in `core.h`:**

1. Add `static uint32_t bc_off, cpr_off, ic_off, sc_off, et_off`
2. In `pjvm_parse()`: compute offsets from `(p - data)` instead of storing
   pointers — keep pointers too for now (non-paged still uses them)
3. Add `static uint8_t *prog_data` (pointer to loaded image, set by platform)
4. Define `PROG(off)` as `prog_data[(off)]` for non-paged builds
5. Replace all `bc[x]` with `PROG(bc_off + x)` — update `BC()` macro
6. Replace all `cpr[x]` with `PROG(cpr_off + x)` — update `cpread()`
7. Replace all `ic[x]` with `PROG(ic_off + x)`
8. Replace all `sc[x]` with `PROG(sc_off + x)`
9. Replace all `et[x]` with `PROG(et_off + x)`
10. Remove now-unused pointer declarations (`bc`, `cpr`, `ic`, `sc`, `et`)

**Edits in `platform/host.c`:**

1. Set `prog_data` pointer after loading file (already have `prog_data` local)
2. Expose it to core.h (make it the static that core.h declares, or pass via
   a setter function)

**Verify:** `make clean && make && make test` — all 12 pass, identical output.
Non-paged behavior unchanged; we just changed how bytes are accessed.

---

## Phase 2: New `PJVMPager` struct and `prog_fetch()`

**Goal:** Replace the region-based PJVMPager with fixed-page PJVMPager.
Implement `prog_fetch()`. Wire up `PROG()` to use it in paged mode.

**Edits in `core.h`:**

1. Remove `PJVMRegion` struct, `method_region[]`, LRU linked list,
   `pjvm_compact_pool()`, `pjvm_load_region()`, `pjvm_lru_touch()`,
   `pjvm_init_regions()`, `pjvm_apply_header_pins()`, `m_sz[]`, `m_pin[]`,
   `has_pin_hints`, `pjvm_bc_file_offset`
2. Define new `PJVMPager` struct:
   - `pool`, `page_size`, `page_shift`, `n_pages`, `file_size`
   - `tag[PJVM_MAX_PAGES]`, `age[PJVM_MAX_PAGES]`, `pinned[PJVM_MAX_PAGES]`
   - `lru_clock`, `read_fn`, `read_ctx`
   - `hits`, `misses`, `slot_misses[PJVM_MAX_PAGES]`
3. Add `PJVM_MAX_PAGES` define (default 16, can be overridden)
4. Implement `prog_fetch()`:
   - Chunk index = `offset >> page_shift`
   - Scan `tag[]` for match (hit) or find victim (miss)
   - On miss: `read_fn()` to load page, update tag/age
5. Implement `pjvm_find_victim()`: lowest age among unpinned slots
6. Implement `pjvm_load_page()`: read from file, set tag, touch age
7. Implement `pjvm_pin_chunk()`: load + set pinned flag
8. Update `PROG()` macro: `prog_fetch(off)` in paged mode
9. Add `pjvm_pager_init()`: zero out tags (0xFFFF), ages, stats

**Edits in `platform/host.c`:**

1. Remove old region-based setup (`PJVMRegion` array, `pjvm_init_regions`,
   `pjvm_apply_header_pins`, etc.)
2. New pager setup in `main()`:
   - Parse `--page-size=N` (default 1024) and `--pages=N` (default 4)
   - Also keep `--cache=N` as shorthand (cache_size / page_size = n_pages)
   - Allocate pool, set geometry, init pager
3. Parse `--pin=chunk0,chunk1,...` for chunk pinning
4. Keep `pjvm_host_read()` callback (unchanged)
5. Print stats on exit: hits, misses, per-slot misses

**Edits in `Makefile`:**

1. No changes needed (PJVM_PAGED flag already used)

**Verify:** `make test` (non-paged) and `make test-paged` pass.

---

## Phase 3: Pin support and CLI polish

**Goal:** Pinning works for chunks. CLI supports page geometry selection.
Test with various configurations.

**Tests:**

1. `./picojvm-paged test.pjvm` — default geometry, all tests pass
2. `./picojvm-paged test.pjvm --page-size=256 --pages=8` — small pages
3. `./picojvm-paged test.pjvm --page-size=4096 --pages=2` — large pages
4. `./picojvm-paged test.pjvm --page-size=256 --pages=4 --pin=0` — pin chunk 0
5. All 12 tests pass across all configurations
6. Verify stats output shows hits/misses/per-slot

---

## Phase 4: Header hints (v2)

**Goal:** v2 header can recommend page_shift and pin hints.

**Edits in `pjvmpack.py`:**

1. Update v2 header byte [12] as `pager_flags`:
   - bit 0: pin hints present
   - bits 1-3: recommended page_shift (0 = no recommendation)
2. `--page-shift=N` flag sets recommended page_shift in header
3. `--pin-chunks=0,3` flag emits per-chunk pin bytes after exception table

**Edits in `core.h`:**

1. Parse pager_flags from v2 header
2. Apply recommended page_shift if caller didn't set one
3. Apply pin hints if caller didn't set explicit pins

**Verify:** v1 still works, v2 with hints works, CLI overrides header.

---

## Phase 5: Cleanup

**Goal:** Remove dead region-paging code, update docs.

1. Delete DESIGN-region-paging.md and EXECPLAN-region-paging.md
   (superseded by fixed-page docs)
2. Remove any leftover region-paging artifacts (`m_sz[]` derivation, etc.)
3. Verify non-paged build has no pager overhead
4. Final test matrix: v1/v2 x paged/non-paged x pin/no-pin x page sizes

---

## Progress

- [x] Phase 1: Add section offsets (`bc_off`, `cpr_off`, etc.)
- [x] Phase 1: Implement `PROG()` macro for non-paged builds
- [x] Phase 1: Replace all direct section pointer access with `PROG()`
- [x] Phase 1: Remove unused section pointers
- [x] Phase 1: Verify all 12 tests pass (non-paged, identical output)
- [x] Phase 2: Remove region-based pager code
- [x] Phase 2: Implement new fixed-page `PJVMPager` struct
- [x] Phase 2: Implement `prog_fetch()`, `pjvm_find_victim()`, `pjvm_load_page()`
- [x] Phase 2: Wire up host.c with new pager init
- [x] Phase 2: Verify all 12 tests pass in paged mode
- [x] Phase 3: Pin support and CLI geometry options
- [x] Phase 3: Test across page size / count configurations
- [ ] Phase 4: v2 header hints for page_shift and pin recommendations
- [x] Phase 5: Remove dead code, delete old design docs, final validation

## Decision Log

- Fixed-size pages, not variable regions — no compaction, no fragmentation
- `PROG()` covers ALL read-only sections, not just bytecodes
- Page size is runtime power-of-2 — bit shift for chunk index, no division
- Tag scan is O(n_pages) where n_pages is 2-16 — acceptable for 8085
- Stats always present (negligible cost at fault time only)
- Per-slot miss counter enables profiling-driven pin selection
