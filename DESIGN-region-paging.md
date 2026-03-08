# Design: Method-Granularity Region Paging

## Status: Implemented and validated

## Problem

The current fixed-page cache (Phase 3, 4×128B) proved demand paging works, but
the page geometry is wrong:

- Fixed-size pages don't match the natural caching unit (methods)
- Page size and slot count are compile-time `#define`s, not runtime-configurable
- Page cache state is static globals — can't support multiple JVM instances
- No way for a program or invoker to influence caching strategy

## Design

### Region Model

The caching unit is a **region** — a contiguous range of bytecode, typically
one method. Each region is described by:

```c
typedef struct PJVMRegion {
    uint32_t file_offset;   // offset into .pjvm file
    uint32_t length;        // bytes
    uint8_t *ram_ptr;       // NULL = not resident
    uint8_t  pinned;        // 1 = never evict
    struct PJVMRegion *next;
} PJVMRegion;
```

The method table already provides `m_co[i]` (code offset) and method sizes are
derivable. Each method maps to one region.

**Pinned regions** are loaded once at startup and never evicted. The hot inner
loop, the main dispatch method, frequently-called utilities — pin those.
Everything else is evictable via LRU.

### Per-Instance Pager Context

Page cache state moves from static globals into a per-instance struct:

```c
typedef struct {
    PJVMRegion *regions;     // linked list of all regions
    uint8_t    *pool;        // RAM pool for evictable region data
    uint32_t    pool_size;   // total bytes available for caching
    uint32_t    pool_used;   // bytes currently occupied
    // platform callback
    void (*read)(uint32_t file_offset, uint8_t *buf, uint16_t len, void *ctx);
    void *read_ctx;          // platform-specific handle (FILE*, FCB*, etc.)
} PJVMPager;
```

`PJVMCtx` gets a pointer:

```c
typedef struct {
    // ... existing fields ...
    PJVMPager *pager;  // NULL = non-paged (bytecode fully resident)
} PJVMCtx;
```

When `pager` is NULL, `BC(addr)` resolves to `bc[addr]` as before — zero
overhead for non-paged builds.

### BC() Resolution (Paged Mode)

```c
static uint8_t bc_fetch(PJVMPager *p, uint32_t addr) {
    // Walk regions to find which one contains addr
    // (or use a fast lookup — addr falls in m_co[mi]..m_co[mi]+size)
    // If region is resident (ram_ptr != NULL): return ram_ptr[addr - region->file_offset]
    // If not resident: evict LRU unpinned region, load from disk, return byte
}
```

For the fast path, the interpreter already knows `cur_mi` — so the current
method's region can be cached in a local variable, avoiding the region walk
on every byte fetch.

### Configuration Resolution Order

```
1. Invoker passes explicit PJVMPager config  → use that
2. No config, but .pjvm header has region table → parse and use
3. Neither → fallback: single buffer, min(bytecodes_size, 8KB)
```

All resolved before `pjvm_run()`:

```c
// config can be NULL for "figure it out"
void pjvm_init(PJVMCtx *ctx, uint8_t *program_data, PJVMPager *config);
// pjvm_init: parse header, apply config or fallback,
//            allocate regions, load pinned regions
void pjvm_run(PJVMCtx *ctx);
```

### .pjvm Header Region Table (v2 Extension)

The v2 header byte [12] (`page_shift`) is repurposed:

```
[12]   region_flags:
         bit 0: region table present
         bit 1: pin recommendations present
[13]   n_regions (0 = one region per method, use method table)
```

If `n_regions > 0`, a region table follows the exception table:

```
Per region (6 bytes):
  [0-3]  bytecode_offset (uint32_t LE)
  [4-5]  length (uint16_t LE) — max 64KB per region
  [+1]   flags: bit 0 = pinned recommendation
```

If `n_regions == 0`, the method table itself defines regions (one per method).
This is the common case — no extra data needed.

### Where Config Comes From (Future CLI)

A `javarun` CLI tool or CP/M loader would accept paging config:

```
javarun program.pjvm                    # use header hints or fallback
javarun program.pjvm --cache=32k        # 32KB eviction pool
javarun program.pjvm --pin=0,3,7        # pin methods 0, 3, 7
javarun program.pjvm --cfg=layout.cfg   # read config from file
```

On CP/M, a simpler model: the loader allocates all available TPA above the
interpreter as the eviction pool. No config file needed — just use what's
available.

### Multi-JVM Support

Because pager state is per-instance via `PJVMCtx.pager`, multiple JVM
instances can coexist:

- Each gets its own `PJVMPager` with its own pool and region list
- A scheduler (e.g. FreeRTOS) allocates separate pools per task
- Or a shared pool with per-instance region tracking

### Eviction Policy

**LRU** via a doubly-linked list of resident unpinned regions. On miss:
1. Walk list tail to find victim
2. Free victim's `ram_ptr` slot in pool
3. Load new region from disk
4. Insert at list head

LRU is worth the overhead here because misses are expensive (multi-sector
disk reads).

## Migration Path

1. Replace fixed-page `bc_fetch()` with region-based `bc_fetch()`
2. Move page cache state from static globals into `PJVMPager`
3. Add `pjvm_init()` that builds region list from method table
4. Wire up config resolution (explicit → header → fallback)
5. Add pin support
6. All existing tests must pass — region-per-method with no pins is
   functionally equivalent to the current fixed-page cache

## Non-Goals (For Now)

- Method reordering in pjvmpack.py (layout optimization)
- Shared pools across JVM instances
- Dynamic region splitting/merging
- Paging of non-bytecode data (CP tables, etc. stay resident)
