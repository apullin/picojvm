# picoJSE

`picoJSE` is the Java-side standard environment for `picoJVM`.

The goal is not to mimic full `java.*` or to shrink the native VM. The goal
is to move application-facing functionality into Java packages that can run on:

- host terminal
- serial terminal backends
- SOL-20 style character displays
- later tile/cell displays on other machines

## Package split

- `pj.Native`
  - package-visible native bridge
  - keeps `pj.*` code out of the default package
- `pj.archive`
  - reusable archive/container helpers
- `pj.io`
  - file and byte-stream helpers on top of the existing file natives
- `pj.util`
  - low-level array, byte, and integer helpers
- `pj.text`
  - formatting, parsing, and small string utilities
- `pj.term`
  - retained cell-surface model and terminal/display presenters
- `pj.ui`
  - later widget toolkit, forms, grids, menus, spreadsheet controls

## Why `pj.term` first

The immediate target is a text/cell environment, not a pixel framebuffer.
That is the right fit for:

- host terminal demos via ANSI/serial control
- SOL-20 screen memory
- spreadsheet/editor/file-manager style applications
- roguelike / ECS-style character demos

The retained surface model is:

- Java owns the logical grid
- a presenter flushes it to the target
- backends can differ radically underneath:
  - ANSI serial terminal
  - direct character VRAM
  - tile hardware

## Current host route

The first implementation uses:

- `pj.Native.termInfo(int)` for columns/rows/caps
- `pj.Native.keyRead()` for nonblocking raw key input
- `pj.Native.ticks()` for monotonic timing
- ANSI escape emission in Java via `putchar` / `print` / `writeBytes`

This keeps the native surface small while still allowing a real terminal UI.

## Serial/TUI route

Serial-terminal TUI is considered a valid target, not a fallback.

The right discipline is:

- retained cell buffer
- diffed flushes where possible
- avoid full-screen repaint loops
- restrict to a narrow terminal capability profile

That gives a practical path to VisiCalc-style applications even on systems
without reprogrammable tile sets.

## Current implementation status

Implemented:

- `pj.Native`
- `pj.archive.Tar`
- `pj.io.Files`
- `pj.io.Binary`
- `pj.util.Bytes`
- `pj.util.Ints`
- `pj.text.Format`
- `pj.text.Parse`
- `pj.text.Strings`
- `pj.term.Terminal`
- `pj.term.CellSurface`
- `pj.term.AnsiTerminal`
- `pj.term.Draw`
- host-only package tests:
  - `TermSmoke`
  - `FilesSmoke`
  - `PicoJseStdSmoke`
  - `TarSmoke`
- manual host demo:
  - `TermDemo`
- self-hosted `picojc` multi-file package validation:
  - `make test-disk-picojse`
  - compiles the real `pj.*` sources plus `TermSmoke` / `FilesSmoke` /
    `PicoJseStdSmoke` / `TarSmoke`
  - runs the resulting programs under `picoJVM`

Still missing:

- a real SOL-20 presenter
- higher-level `pj.ui` widgets
- richer terminal capabilities / attributes
- stream-style `pj.io` readers/writers
- collection types beyond raw arrays
- compression formats beyond plain tar/ustar

## Near-term next steps

1. Add a SOL-20 presenter backend using the same `CellSurface`.
2. Add stream-style `pj.io` helpers and enough binary/text support for archive
   tools (`pjtar`, later `pjzip` / `pjunzip`).
3. Add a simple grid widget in `pj.ui`.
4. Build a spreadsheet-shell prototype on top of `pj.term` + `pj.io`.
5. Add a cleaner app/library build flow so `picojc` package builds do not rely
   on hand-written `sources.lst` assembly.
