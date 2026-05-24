"""Resolution helpers for parsed classfile structures."""

from .errors import PackError


def resolve_class_name(cp, class_idx):
    """Resolve a CONSTANT_Class to its UTF-8 internal name."""
    if class_idx == 0:
        return None
    entry = cp[class_idx]
    if entry[0] != "Class":
        raise PackError(f"CP#{class_idx} is not a Class")
    return cp[entry[1]][1]


def resolve_method_name(cp, methodref_idx):
    """Resolve a method reference to ``(class_name, method_name, descriptor)``."""
    entry = cp[methodref_idx]
    if entry[0] not in ("Methodref", "InterfaceMethodref"):
        raise PackError(f"CP#{methodref_idx} is not a Methodref/InterfaceMethodref")
    class_name = resolve_class_name(cp, entry[1])
    nat = cp[entry[2]]
    method_name = cp[nat[1]][1]
    descriptor = cp[nat[2]][1]
    return class_name, method_name, descriptor


def topological_sort(classes):
    """Sort class names so parents come before children."""
    order = []
    visited = set()

    def visit(name):
        if name in visited or name not in classes:
            return
        visited.add(name)
        parent = classes[name].parent_name
        if parent and parent in classes:
            visit(parent)
        order.append(name)

    for name in classes:
        visit(name)
    return order
