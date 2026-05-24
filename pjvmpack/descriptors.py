"""JVM descriptor helpers."""

from .errors import PackError


def count_args(descriptor):
    """Count argument slots from a method descriptor like ``(II)V``."""
    count = 0
    for arg_desc in argument_descriptors(descriptor):
        count += 2 if arg_desc in ("J", "D") else 1
    return count


def argument_descriptors(descriptor):
    """Return argument descriptors from a method descriptor."""
    i = 1
    args = []
    while descriptor[i] != ")":
        start = i
        c = descriptor[i]
        if c in ("I", "Z", "B", "C", "S", "F", "J", "D"):
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
        elif c == "L":
            while descriptor[i] != ";":
                i += 1
            i += 1
        else:
            raise PackError(f"Unknown descriptor char: {c}")
        args.append(descriptor[start:i])
    return args


def is_ref_descriptor(descriptor):
    return descriptor.startswith("L") or descriptor.startswith("[")
