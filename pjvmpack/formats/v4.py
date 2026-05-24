"""v4-specific .pjvm emission helpers."""

from .common import is_no_id
from ..constants import PJVM_NO_VTABLE
from ..emit import require_real_id_v4, write_uleb
from ..errors import PackError


def emit_id(value):
    if is_no_id(value):
        return 0xFFFF
    require_real_id_v4("v4 id", value)
    return value


def emit_packed_id(value):
    if is_no_id(value):
        return 0
    require_real_id_v4("packed v4 id", value)
    return value + 1


def encode_method_table_packed(method_table):
    """Encode the compact v4 method-table representation."""
    blob = bytearray()
    prev_code_offset = 0
    prev_cp_base = None
    prev_exc_offset = None

    for mt in method_table:
        flags = 0
        if mt["is_native"]:
            flags = 1 | (mt["native_id"] << 1)

        blob.extend(write_uleb(mt["max_locals"]))
        blob.extend(write_uleb(mt["max_stack"]))
        blob.extend(write_uleb(mt["arg_count"]))
        blob.extend(write_uleb(flags))
        blob.extend(write_uleb(emit_packed_id(mt["vtable_slot"])))
        blob.extend(write_uleb(emit_packed_id(mt.get("vmid", PJVM_NO_VTABLE))))

        if mt["is_native"]:
            continue

        code_delta = mt["code_offset"] - prev_code_offset
        if code_delta < 0:
            raise PackError("non-native method code offsets are not monotonic")
        blob.extend(write_uleb(code_delta))
        prev_code_offset = mt["code_offset"]

        cp_base = mt["cp_base"]
        if prev_cp_base is not None and cp_base == prev_cp_base:
            blob.extend(write_uleb(0))
        else:
            blob.extend(write_uleb(cp_base + 1))
            prev_cp_base = cp_base

        blob.extend(write_uleb(mt["exc_count"]))
        exc_offset = mt["exc_offset_idx"]
        if prev_exc_offset is not None and exc_offset == prev_exc_offset:
            blob.extend(write_uleb(0))
        else:
            blob.extend(write_uleb(exc_offset + 1))
            prev_exc_offset = exc_offset

    return bytes(blob)
