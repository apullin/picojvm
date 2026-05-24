"""JVM descriptor helpers."""

from .errors import PackError


def count_args(descriptor):
    """Count argument slots from a method descriptor like ``(II)V``."""
    i = 1
    count = 0
    while descriptor[i] != ")":
        c = descriptor[i]
        if c in ("I", "Z", "B", "C", "S", "F"):
            count += 1
            i += 1
        elif c in ("J", "D"):
            count += 2
            i += 1
        elif c == "[":
            while descriptor[i] == "[":
                i += 1
            if descriptor[i] == "L":
                while descriptor[i] != ";":
                    i += 1
                i += 1
            else:
                i += 1
            count += 1
        elif c == "L":
            while descriptor[i] != ";":
                i += 1
            i += 1
            count += 1
        else:
            raise PackError(f"Unknown descriptor char: {c}")
    return count


def is_ref_descriptor(descriptor):
    return descriptor.startswith("L") or descriptor.startswith("[")
