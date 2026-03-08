# Exec Plan: Method-Granularity Region Paging

## Purpose

Replace the fixed-page bytecode cache (4×128B, clock eviction) with method-
granularity region paging. Each method's bytecode is a cacheable region with
LRU eviction and optional pinning. Pager state moves from static globals into
a per-instance `PJVMPager` struct for multi-JVM support.

**Observable outcome:** `make test` and `make test-paged` pass. Paged mode
prints region-level hit/miss stats. Non-paged `BC(a) = bc[a]` has zero
overhead.

## Context

See `DESIGN-region-paging.md` for the full architecture. Key points:

- Region = one method's bytecode (`m_co[i]`, derived size)
- `PJVMPager` struct per JVM instance, pointed to by `PJVMCtx.pager`
- Config resolution: explicit → header hints → fallback (single buffer)
- Pinned regions loaded at startup, never evicted
- LRU eviction with compact-on-evict pool allocator
- `method_region[]` table for O(1) region lookup by method index

---

## Phase 1: Add `m_sz[]` and `PJVMPager`/`PJVMRegion` types

**Goal:** Define types, add method size array, derive sizes in `pjvm_parse()`.
No behavioral change.

**Edits in `core.h`:**

1. Add `m_sz[PJVM_METHOD_CAP]` (uint16_t) alongside `m_co[]`
2. Add `PJVMRegion` and `PJVMPager` structs inside `#ifdef PJVM_PAGED`
3. Add `PJVMPager *pager` to `PJVMCtx` inside `#ifdef PJVM_PAGED`
4. Derive `m_sz[]` in `pjvm_parse()` after method table loop:
   sort non-native methods by `m_co`, compute deltas

**Verify:** `make clean && make && make test && make picojvm-paged && make test-paged`
(all 12 pass, new types unused, old bc_fetch still active)

---

## Phase 2: Region-based `bc_fetch()` and `pjvm_init()`

**Goal:** Replace fixed-page bc_fetch with region-based. Move pager state
from static globals into `PJVMPager`. Add `pjvm_init()`.

**Edits in `core.h`:**

1. Remove old static page cache globals (`pjvm_page_data`, `pjvm_page_tag`,
   `pjvm_page_ref`, `pjvm_page_hand`, `pjvm_hot_slot`, `pjvm_page_hits/misses`)
2. Remove old `bc_fetch()` and `pjvm_platform_read_page` forward decl
3. Add `pjvm_init()` — builds region list from method table + `m_sz[]`
4. Add `method_region[PJVM_METHOD_CAP]` to `PJVMPager` for O(1) lookup
5. Add new `bc_fetch()`:
   - Fast path: `cached_region` (stored in pager) covers addr → return byte
   - Slow path: `method_region[cur_mi]` lookup
   - Miss: evict LRU unpinned, compact pool, load from disk
6. Add LRU helpers: `pjvm_lru_touch()`, `pjvm_evict_lru()`
7. Add `pjvm_compact_pool()` — slide resident regions down, update `ram_ptr`s
8. Add `pjvm_load_region()` — evict until room, compact, bump-allocate, read

**Edits in `platform/host.c`:**

1. Replace `pjvm_platform_read_page()` with callback-compatible `pjvm_host_read()`
   that takes `void *ctx` (the FILE*)
2. In `main()`: allocate `PJVMPager`, `PJVMRegion` array, pool buffer (8KB default)
3. Call `pjvm_init()`, set `ctx.pager`
4. Update stats printing to use `pager.hits`/`pager.misses`

**Verify:** `make test` (non-paged) and `make test-paged` (region-paged) pass

---

## Phase 3: Pin support and CLI options

**Goal:** Pinned regions loaded at init, never evicted. Host CLI gets
`--cache=N` and `--pin=0,3,7` options.

**Edits in `core.h`:**

1. Add `pjvm_pin_method()` — sets `pinned=1`, loads immediately into pool
2. In `pjvm_init()`: load pinned regions first (occupy start of pool)
3. In `pjvm_evict_lru()`: skip pinned regions
4. In `pjvm_compact_pool()`: pinned regions at pool start, never moved

**Edits in `platform/host.c`:**

1. Parse `--cache=N` to set pool size
2. Parse `--pin=0,3,7` to pin specific methods
3. Use `malloc()` for pool (dynamic size)
4. Fallback: `min(bytecodes_size, 8192)` if no `--cache`

**Verify:**
- `make test-paged` passes
- `./picojvm-paged tests/Fib.pjvm --pin=1` — method 1 pinned, 0 misses for it
- `./picojvm-paged tests/BubbleSort.pjvm --cache=256` — correct with heavy eviction

---

## Phase 4: Header hints and config resolution

**Goal:** v2 header carries pin recommendations. Three-tier config resolution.

**Edits in `pjvmpack.py`:**

1. Repurpose v2 header byte [12] as `region_flags` (bit 1 = pin hints present)
2. Add `--pin-hints=0,1` flag — emits per-method pin byte after exception table
3. Update verbose output to show pin hints

**Edits in `core.h`:**

1. Parse `region_flags` from v2 header byte [12]
2. Parse pin hint bytes after exception table if flag set
3. Config resolution in `pjvm_init()`:
   - If caller pre-set `pinned=1` → keep (explicit config)
   - Else if header has pin hints → apply them
   - Else → no pins (fallback)

**Verify:**
- v1 format still works (no region_flags)
- v2 with `--pin-hints=0,1` → methods 0,1 pinned automatically
- CLI `--pin` overrides header hints
- All 12 tests pass in all modes

---

## Phase 5: Cleanup and validation

**Goal:** Remove dead code, verify all configurations, update docs.

1. Verify `grep -c 'pjvm_page_data\|pjvm_page_tag\|pjvm_page_ref' core.h` = 0
2. Verify non-paged build has no PJVMPager overhead (check struct size)
3. Run all 12 tests: non-paged, paged, paged+pins, paged+tiny-cache
4. Update DESIGN-region-paging.md with outcomes
5. Update EXECPLAN progress checkboxes

---

## Progress

- [x] Phase 1: Add m_sz[] and PJVMPager/PJVMRegion types
- [x] Phase 1: Derive method sizes in pjvm_parse()
- [x] Phase 1: Verify all tests pass (no behavioral change)
- [x] Phase 2: Remove old fixed-page globals and bc_fetch()
- [x] Phase 2: Add pjvm_init() and region-based bc_fetch()
- [x] Phase 2: Add LRU eviction and pool compaction
- [x] Phase 2: Wire up host.c with callback and PJVMPager allocation
- [x] Phase 2: Verify all 12 tests pass in paged mode
- [x] Phase 3: Add pjvm_pin_method() and pin support
- [x] Phase 3: Add --cache and --pin CLI options to host
- [x] Phase 3: Verify pins work with tiny cache sizes
- [x] Phase 4: Add --pin-hints to pjvmpack.py
- [x] Phase 4: Parse region_flags and pin hints in core.h
- [x] Phase 4: Implement config resolution (explicit → header → fallback)
- [x] Phase 4: Verify v1/v2 × paged/non-paged × pins all work
- [x] Phase 5: Cleanup and final validation

## Decision Log

- Method-granularity regions, not fixed pages — methods are the natural unit
- Compact-on-evict pool: zero fragmentation, O(n) but n is small (<30 methods)
- method_region[] table for O(1) lookup (interpreter knows cur_mi)
- Pinned regions occupy start of pool, never moved by compaction
- Keep pjvm_bc_file_offset as static (property of parsed file, not pager)
- Pool compaction must process regions in pool-position order (sort by ram_ptr),
  not array order — regions are loaded on-demand in access order which differs
  from array order; sliding in array order can overwrite live region data
