"""v3-specific .pjvm emission helpers."""

from .common import is_no_id
from ..emit import require_real_id


def emit_id(value):
    if is_no_id(value):
        return 0xFF
    require_real_id("v3 id", value)
    return value
