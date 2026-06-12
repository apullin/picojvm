/* Linker script for TMS9900 picoJVM.
 *
 * Memory layout:
 *   0x0000 - _end   : code + rodata + data + bss
 *   _end   - 0xEEFF : heap
 *   0xEF00 - 0xEFFF : output buffer
 *   0xF000 - 0xF01F : workspace
 *   0xF020 - 0xFFFC : stack
 */
ENTRY(_start)
SECTIONS {
  . = 0x0000;
  .text : { *(.text*) }
  .rodata : { *(.rodata*) }
  .data : { *(.data*) }
  .bss : { *(.bss*) *(COMMON) }
  _end = .;

  PROVIDE(__heap_start = _end);
  PROVIDE(__heap_end = 0xEEFF);
}
