"""Bytecode scanning and constant-pool operand rewriting."""

import struct

from .errors import PackError

OP_NOP = 0x00
OP_ACONST_NULL = 0x01
OP_ICONST_M1 = 0x02
OP_ICONST_0 = 0x03
OP_ICONST_5 = 0x08
OP_BIPUSH = 0x10
OP_SIPUSH = 0x11
OP_LDC = 0x12
OP_LDC_W = 0x13
OP_LDC2_W = 0x14
OP_ILOAD = 0x15
OP_ALOAD = 0x19
OP_ILOAD_0 = 0x1A
OP_ILOAD_3 = 0x1D
OP_ALOAD_0 = 0x2A
OP_ALOAD_3 = 0x2D
OP_ISTORE = 0x36
OP_ASTORE = 0x3A
OP_IASTORE = 0x4F
OP_AASTORE = 0x53
OP_BASTORE = 0x54
OP_CASTORE = 0x55
OP_SASTORE = 0x56
OP_DUP = 0x59
OP_IINC = 0x84
OP_I2B = 0x91
OP_I2C = 0x92
OP_I2S = 0x93
OP_IFEQ = 0x99
OP_IF_ACMPNE = 0xA6
OP_GOTO = 0xA7
OP_ARETURN = 0xB0
OP_RETURN = 0xB1
OP_GETSTATIC = 0xB2
OP_PUTSTATIC = 0xB3
OP_GETFIELD = 0xB4
OP_PUTFIELD = 0xB5
OP_INVOKEVIRTUAL = 0xB6
OP_INVOKESPECIAL = 0xB7
OP_INVOKESTATIC = 0xB8
OP_INVOKEINTERFACE = 0xB9
OP_INVOKEDYNAMIC = 0xBA
OP_NEW = 0xBB
OP_NEWARRAY = 0xBC
OP_ANEWARRAY = 0xBD
OP_CHECKCAST = 0xC0
OP_INSTANCEOF = 0xC1
OP_MULTIANEWARRAY = 0xC5
OP_IFNONNULL = 0xC7

CPREF_U1_OPS = {OP_LDC}
CPREF_U2_OPS = {
    OP_LDC_W, OP_LDC2_W, OP_GETSTATIC, OP_PUTSTATIC, OP_GETFIELD,
    OP_PUTFIELD, OP_INVOKEVIRTUAL, OP_INVOKESPECIAL, OP_INVOKESTATIC,
    OP_INVOKEINTERFACE, OP_INVOKEDYNAMIC, OP_NEW, OP_ANEWARRAY,
    OP_CHECKCAST, OP_INSTANCEOF, OP_MULTIANEWARRAY,
}

FIXED_OPCODE_LENGTHS = [1] * 256
for _op in range(0x10, 0x12):
    FIXED_OPCODE_LENGTHS[_op] = _op - 0x0E
FIXED_OPCODE_LENGTHS[0x12] = 2
FIXED_OPCODE_LENGTHS[0x13] = 3
FIXED_OPCODE_LENGTHS[0x14] = 3
for _op in range(0x15, 0x1A):
    FIXED_OPCODE_LENGTHS[_op] = 2
for _op in range(0x36, 0x3B):
    FIXED_OPCODE_LENGTHS[_op] = 2
FIXED_OPCODE_LENGTHS[0x84] = 3
for _op in range(0x99, 0xA9):
    FIXED_OPCODE_LENGTHS[_op] = 3
FIXED_OPCODE_LENGTHS[0xA9] = 2
for _op in range(0xB2, 0xB9):
    FIXED_OPCODE_LENGTHS[_op] = 3
FIXED_OPCODE_LENGTHS[0xB9] = 5
FIXED_OPCODE_LENGTHS[0xBA] = 5
FIXED_OPCODE_LENGTHS[0xBB] = 3
FIXED_OPCODE_LENGTHS[0xBC] = 2
FIXED_OPCODE_LENGTHS[0xBD] = 3
FIXED_OPCODE_LENGTHS[0xC0] = 3
FIXED_OPCODE_LENGTHS[0xC1] = 3
FIXED_OPCODE_LENGTHS[0xC5] = 4
FIXED_OPCODE_LENGTHS[0xC6] = 3
FIXED_OPCODE_LENGTHS[0xC7] = 3
FIXED_OPCODE_LENGTHS[0xC8] = 5
FIXED_OPCODE_LENGTHS[0xC9] = 5


def _read_s4_be(buf, off):
    return struct.unpack_from(">i", buf, off)[0]


def bytecode_cp_operands(bytecode):
    """Yield ``(operand_offset, width, opcode, cp_index)`` for CP operands."""
    i = 0
    n = len(bytecode)
    while i < n:
        op = bytecode[i]
        if op in CPREF_U1_OPS:
            if i + 1 >= n:
                raise PackError(f"Truncated bytecode at opcode 0x{op:02X}")
            yield i + 1, 1, op, bytecode[i + 1]
            i += 2
        elif op in CPREF_U2_OPS:
            if i + 2 >= n:
                raise PackError(f"Truncated bytecode at opcode 0x{op:02X}")
            cp_idx = (bytecode[i + 1] << 8) | bytecode[i + 2]
            yield i + 1, 2, op, cp_idx
            i += FIXED_OPCODE_LENGTHS[op]
        elif op == 0xAA:
            j = i + 1
            while j & 3:
                j += 1
            if j + 12 > n:
                raise PackError("Truncated tableswitch")
            low = _read_s4_be(bytecode, j + 4)
            high = _read_s4_be(bytecode, j + 8)
            count = high - low + 1
            if count < 0:
                raise PackError("Invalid tableswitch range")
            i = j + 12 + count * 4
            if i > n:
                raise PackError("Truncated tableswitch entries")
        elif op == 0xAB:
            j = i + 1
            while j & 3:
                j += 1
            if j + 8 > n:
                raise PackError("Truncated lookupswitch")
            npairs = _read_s4_be(bytecode, j + 4)
            if npairs < 0:
                raise PackError("Invalid lookupswitch npairs")
            i = j + 8 + npairs * 8
            if i > n:
                raise PackError("Truncated lookupswitch pairs")
        elif op == 0xC4:
            if i + 1 >= n:
                raise PackError("Truncated wide opcode")
            i += 6 if bytecode[i + 1] == 0x84 else 4
            if i > n:
                raise PackError("Truncated wide operands")
        else:
            i += FIXED_OPCODE_LENGTHS[op]


def rewrite_bytecode_cp_indices(bytecode, cp_index_map):
    """Return bytecode with all CP operands rewritten through ``cp_index_map``."""
    out = bytearray(bytecode)
    for off, width, _op, old_idx in bytecode_cp_operands(bytecode):
        if old_idx not in cp_index_map:
            raise PackError(f"Missing compact CP mapping for index {old_idx}")
        new_idx = cp_index_map[old_idx]
        if width == 1:
            if new_idx > 0xFF:
                raise PackError(f"Compacted ldc index {new_idx} exceeds u8")
            out[off] = new_idx
        else:
            if new_idx > 0xFFFF:
                raise PackError(f"Compacted CP index {new_idx} exceeds u16")
            out[off] = (new_idx >> 8) & 0xFF
            out[off + 1] = new_idx & 0xFF
    return bytes(out)


def add_ordered_cp_use(uses, cp_idx, is_ldc):
    if cp_idx not in uses["all_set"]:
        uses["all_set"].add(cp_idx)
        uses["all"].append(cp_idx)
    if is_ldc and cp_idx not in uses["ldc_set"]:
        uses["ldc_set"].add(cp_idx)
        uses["ldc"].append(cp_idx)
