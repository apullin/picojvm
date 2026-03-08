# Design: Fixed-Page Program Image Pager

## Status: Approved design, pending implementation

## Supersedes

DESIGN-region-paging.md (method-granularity regions). That design paged only
bytecodes via `BC()` and left other sections (`cpr`, `ic`, `sc`, `et`) as
direct pointers — incomplete. Also used variable-sized regions with compaction,
which is more complex than needed.

## Problem

The .pjvm program image contains several read-only sections:

| Section     | Pointer | Typical size | Access pattern              |
|-------------|---------|--------------|-----------------------------|
| bytecodes   | `bc`    | dominant     | every opcode fetch          |
| CP resolve  | `cpr`   | moderate     | every invoke, field access  |
| int consts  | `ic`    | small        | `ldc` integer               |
| str consts  | `sc`    | small-mod    | `ldc` string, native calls  |
| exc table   | `et`    | small        | `athrow`, unwind            |

On a memory-constrained target, none of these can be assumed resident. A
program with a large twiddle table (int constants) or many string literals
would fault on constant loads, not just branches. The pager must cover the
entire read-only image, not just bytecodes.

## Design

### Unified `PROG()` Accessor

All read-only data from the .pjvm file is accessed through a single macro:

```c
#ifdef PJVM_PAGED
#define PROG(off)  prog_fetch(off)
#else
#define PROG(off)  prog_data[(off)]
#endif
```

Section pointers become section offsets (byte position in the .pjvm file):

```c
// Before (direct pointers):
static uint8_t *bc, *cpr, *ic, *sc, *et;
bc[addr]           // bytecode fetch
cpr[cb + idx]      // CP resolution

// After (file offsets):
static uint32_t bc_off, cpr_off, ic_off, sc_off, et_off;
PROG(bc_off + addr)      // bytecode fetch
PROG(cpr_off + cb + idx) // CP resolution
```

Non-paged builds: `PROG()` is a direct array index into the loaded image.
Zero overhead — same as today.

### Fixed-Size Page Cache

The program image is divided into fixed-size pages. Page size is a power of 2,
selected at runtime.

```c
typedef struct {
    uint8_t  *pool;         // flat buffer: n_pages * page_size bytes
    uint16_t  page_size;    // 256, 512, 1024, 4096, etc.
    uint8_t   page_shift;   // log2(page_size) — for bit-shift division
    uint8_t   n_pages;      // number of page slots in pool
    uint16_t  tag[PJVM_MAX_PAGES];   // which chunk is in each slot (0xFFFF = empty)
    uint8_t   age[PJVM_MAX_PAGES];   // LRU counter (incremented on access)
    uint8_t   pinned[PJVM_MAX_PAGES]; // 1 = don't evict
    /* platform read callback */
    void (*read_fn)(uint32_t file_offset, uint8_t *buf, uint16_t len, void *ctx);
    void *read_ctx;
    /* stats (always present, negligible cost) */
    uint32_t  hits;
    uint32_t  misses;
    uint8_t   slot_misses[PJVM_MAX_PAGES]; // per-slot fault count
} PJVMPager;
```

`PJVM_MAX_PAGES` is a compile-time cap (e.g., 16). Actual `n_pages` is
runtime. Metadata per page slot: 5 bytes (tag + age + pinned + slot_misses).

### Lookup (O(1) + small scan)

```c
static uint8_t prog_fetch(uint32_t offset) {
    PJVMPager *p = g_pjvm->pager;
    uint16_t chunk = (uint16_t)(offset >> p->page_shift);
    uint16_t within = offset & (p->page_size - 1);

    // Scan tag array for chunk (n_pages is small, typically 2-8)
    for (uint8_t i = 0; i < p->n_pages; i++) {
        if (p->tag[i] == chunk) {
            p->age[i] = ++p->lru_clock;  // touch
            p->hits++;
            return p->pool[i * p->page_size + within];
        }
    }

    // Miss — evict LRU unpinned slot, load chunk
    p->misses++;
    uint8_t victim = pjvm_find_victim(p);
    p->slot_misses[victim]++;
    pjvm_load_page(p, victim, chunk);
    return p->pool[victim * p->page_size + within];
}
```

Chunk index is a bit shift (no division on 8085). Tag scan is O(n_pages)
where n_pages is 2-8 — a few compare-and-branch instructions.

### Eviction

LRU via the `age[]` counter. On miss, find the unpinned slot with the lowest
age value:

```c
static uint8_t pjvm_find_victim(PJVMPager *p) {
    uint8_t best = 0xFF, best_age = 0xFF;
    for (uint8_t i = 0; i < p->n_pages; i++) {
        if (p->pinned[i]) continue;
        if (p->tag[i] == 0xFFFF) return i;  // empty slot — use immediately
        if (p->age[i] < best_age) {
            best_age = p->age[i];
            best = i;
        }
    }
    return best;
}
```

### Page Loading

```c
static void pjvm_load_page(PJVMPager *p, uint8_t slot, uint16_t chunk) {
    uint32_t file_offset = (uint32_t)chunk << p->page_shift;
    uint16_t len = p->page_size;
    // Clamp to file size for last page
    if (file_offset + len > p->file_size)
        len = (uint16_t)(p->file_size - file_offset);
    p->read_fn(file_offset, p->pool + slot * p->page_size, len, p->read_ctx);
    p->tag[slot] = chunk;
    p->age[slot] = ++p->lru_clock;
}
```

No compaction. No variable-size allocation. Load a page-sized chunk into a
fixed slot. Done.

### Pinning

Pin a chunk so it's never evicted:

```c
void pjvm_pin_chunk(PJVMPager *p, uint16_t chunk) {
    // Load into first available unpinned empty slot
    uint8_t slot = pjvm_find_victim(p);
    pjvm_load_page(p, slot, chunk);
    p->pinned[slot] = 1;
}
```

Pinned pages are loaded at init time before `pjvm_run()`. Caller decides
which chunks to pin — via CLI flags, header hints, or profiling results.

For function-level pinning: compute `chunk = m_co[mi] >> page_shift` and pin
that chunk. A function that spans two chunks would need both pinned.

### Configuration

All runtime, all per-instance:

```c
// Example: 4 pages of 1KB each
pager.page_size = 1024;
pager.page_shift = 10;
pager.n_pages = 4;
pager.pool = malloc(4 * 1024);

// Example: 2 pages of 4KB each
pager.page_size = 4096;
pager.page_shift = 12;
pager.n_pages = 2;
pager.pool = malloc(2 * 4096);
```

The caller chooses geometry based on available RAM. Different JVM instances
(e.g., FreeRTOS tasks) can have different page sizes and counts. The pager
doesn't care.

### .pjvm Header Hints (v2)

v2 header byte [12] `pager_flags`:

```
bit 0: pin hints present
bit 1-3: recommended page_shift (0 = no recommendation)
```

If pin hints present, one byte per chunk follows the exception table:
`1` = recommend pinning. The invoker can accept or override.

### Stats

Available after `pjvm_run()` returns (normal exit or fault):

- `pager.hits` / `pager.misses` — global counters
- `pager.slot_misses[i]` — which slots are thrashing

The caller inspects these and does whatever it wants (print, log, ignore).
The pager has no I/O dependency.

### Multi-Instance / Multithreading

Per-instance `PJVMPager` in `PJVMCtx`. Each JVM instance owns its own page
pool. A future OS/scheduler could own the pools and assign them to tasks.
Fixed page geometry makes this natural — no fragmentation, no compaction,
no shared mutable state in the pager.

## Access Points to Modify

Every place that currently uses a section pointer directly:

| Current                     | New                                  | Sites |
|-----------------------------|--------------------------------------|-------|
| `bc[addr]` / `BC(a)`       | `PROG(bc_off + a)`                   | ~5    |
| `cpr[cb + idx]`            | `PROG(cpr_off + cb + idx)`           | ~3    |
| `ic[idx * 4 + byte]`       | `PROG(ic_off + idx * 4 + byte)`      | ~2    |
| `sc[offset]`               | `PROG(sc_off + offset)`              | ~5    |
| `et[base + i * 7 + byte]`  | `PROG(et_off + base + i * 7 + byte)` | ~8    |

All sites are in `core.h`. The platform files don't access these directly.

## RAM Budget (8085 target)

Fixed overhead (PJVM_MAX_PAGES=8):
```
PJVMPager struct:  ~50 bytes (pool ptr, geometry, callbacks, stats)
tag[8]:            16 bytes
age[8]:             8 bytes
pinned[8]:          8 bytes
slot_misses[8]:     8 bytes
                   --------
                   ~90 bytes
```

Plus the pool itself (caller-provided):
```
2 × 1KB  =  2KB     (minimal)
4 × 1KB  =  4KB     (comfortable)
2 × 4KB  =  8KB     (generous)
```

Total pager overhead (excluding pool): **~90 bytes**.

## Non-Goals

- Paging of heap or stack data (read-write — different problem)
- Page sizes that aren't powers of 2
- Shared page pools across JVM instances (future OS concern)
- Automatic page size selection (profiling tool provides recommendations)
