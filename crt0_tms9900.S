/* crt0_tms9900.S -- TMS9900 startup for picoJVM
 *
 * Sets workspace and stack, calls main(), then halts.
 * Workspace at 0xF000, stack descends from 0xFFFC.
 */
	.text
	.globl	_start
_start:
	LWPI	0xF000
	LI	R10, 0xFFFC

	BL	@main
.Lhalt:
	JMP	.Lhalt
