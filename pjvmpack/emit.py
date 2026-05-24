"""Low-level binary emission helpers."""

from .constants import (
    PJVM_MAX_REAL_ID,
    PJVM_MAX_REAL_ID_V4,
    PJVM_MAX_U8,
    PJVM_MAX_U16,
)
from .errors import PackError


def require_u8(label, value):
    if value < 0 or value > PJVM_MAX_U8:
        raise PackError(f"{label} {value} exceeds .pjvm u8 limit ({PJVM_MAX_U8})")


def require_u16(label, value):
    if value < 0 or value > PJVM_MAX_U16:
        raise PackError(f"{label} {value} exceeds .pjvm u16 limit ({PJVM_MAX_U16})")


def require_real_id(label, value):
    if value < 0 or value > PJVM_MAX_REAL_ID:
        raise PackError(
            f"{label} {value} exceeds .pjvm id limit "
            f"({PJVM_MAX_REAL_ID}; 0xFF is reserved)"
        )


def require_real_id_v4(label, value):
    if value < 0 or value > PJVM_MAX_REAL_ID_V4:
        raise PackError(
            f"{label} {value} exceeds .pjvm v4 id limit "
            f"({PJVM_MAX_REAL_ID_V4}; 0xFFFF is reserved)"
        )


def write_uleb(value):
    if value < 0:
        raise PackError(f"negative ULEB value: {value}")
    out = bytearray()
    while True:
        b = value & 0x7F
        value >>= 7
        if value:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)
