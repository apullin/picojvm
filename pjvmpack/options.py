"""User-visible pack options after CLI parsing."""

from dataclasses import dataclass
from typing import List, Optional


@dataclass(frozen=True)
class PackOptions:
    verbose: bool = False
    v2: bool = False
    pin_hints: Optional[List[int]] = None
    compact_cp: bool = True
    pjvm_format: str = "auto"
    pack_method_table: bool = False
