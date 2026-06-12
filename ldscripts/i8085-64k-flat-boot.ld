/* 64K flat map with the picoJVM boot overlay: the .pjvmboot section
 * (the .pjvm loader - code that runs exactly once, before the heap
 * exists) is placed AFTER .bss, inside the heap window [_end, heap end).
 * pjvm_heap_init reclaims it: the first allocations overwrite boot code
 * that can never run again. Derived from sysroot's i8085-64k-flat.ld. */
OUTPUT_FORMAT(elf32-i8085)
ENTRY(_start)

MEMORY
{
  VECTORS (rx)  : ORIGIN = 0x0000, LENGTH = 0x0040
  PROG    (rwx) : ORIGIN = 0x0040, LENGTH = 0xFDC0
}

SECTIONS
{
  .vectors : {
    KEEP(*(.vectors))
  } > VECTORS

  .text : {
    *(.text .text.* .init .fini)
  } > PROG

  .rodata : {
    *(.rodata .rodata.*)
  } > PROG

  /* C++ global constructors/destructors */
  .init_array : {
    . = ALIGN(2);
    PROVIDE_HIDDEN(__init_array_start = .);
    KEEP(*(SORT_BY_INIT_PRIORITY(.init_array.*) SORT_BY_INIT_PRIORITY(.ctors.*)))
    KEEP(*(.init_array .ctors))
    PROVIDE_HIDDEN(__init_array_end = .);
  } > PROG

  .fini_array : {
    . = ALIGN(2);
    PROVIDE_HIDDEN(__fini_array_start = .);
    KEEP(*(SORT_BY_INIT_PRIORITY(.fini_array.*) SORT_BY_INIT_PRIORITY(.dtors.*)))
    KEEP(*(.fini_array .dtors))
    PROVIDE_HIDDEN(__fini_array_end = .);
  } > PROG

  . = ALIGN(2);
  _etext = .;

  .data : {
    . = ALIGN(2);
    _sdata = .;
    *(.data .data.*)
    . = ALIGN(2);
    _edata = .;
  } > PROG

  _sidata = LOADADDR(.data);

  .bss (NOLOAD) : {
    . = ALIGN(2);
    _sbss = .;
    *(.bss .bss.*)
    *(COMMON)
    . = ALIGN(2);
    _ebss = .;
  } > PROG

  _end = .;

  /* Boot-only code, sacrificed to the heap after pjvm_parse() returns.
   * Deliberately after _end so the heap window covers it. */
  .pjvmboot : {
    *(.pjvmboot .pjvmboot.*)
  } > PROG
}

PROVIDE(_data_size = _edata - _sdata);
PROVIDE(_bss_size = _ebss - _sbss);

PROVIDE(_estack = 0xFE00);
PROVIDE(_stack = _estack);

PROVIDE(__heap_start = _end);
PROVIDE(__heap_end = _stack - 0x0200);
