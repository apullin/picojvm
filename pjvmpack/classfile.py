"""Minimal Java classfile parser used by pjvmpack."""

import struct

from .constants import (
    CP_CLASS,
    CP_DOUBLE,
    CP_FIELDREF,
    CP_FLOAT,
    CP_INTEGER,
    CP_INTERFACE_METHODREF,
    CP_INVOKE_DYNAMIC,
    CP_LONG,
    CP_METHODREF,
    CP_METHOD_HANDLE,
    CP_METHOD_TYPE,
    CP_NAME_AND_TYPE,
    CP_STRING,
    CP_UTF8,
)
from .errors import PackError
from .model import FieldInfo, MethodInfo, ParsedClass


# Bounds checks intentionally stay minimal; later phases validate format limits.
class ClassReader:
    def __init__(self, data):
        self.data = data
        self.pos = 0

    def u1(self):
        v = self.data[self.pos]
        self.pos += 1
        return v

    def u2(self):
        v = struct.unpack_from(">H", self.data, self.pos)[0]
        self.pos += 2
        return v

    def u4(self):
        v = struct.unpack_from(">I", self.data, self.pos)[0]
        self.pos += 4
        return v

    def read(self, n):
        v = self.data[self.pos:self.pos + n]
        self.pos += n
        return v


def _skip_annotation_value(r):
    """Skip a single annotation element_value (JVM spec section 4.7.16.1)."""
    tag = r.u1()
    if tag in (ord("B"), ord("C"), ord("D"), ord("F"), ord("I"),
               ord("J"), ord("S"), ord("Z"), ord("s"), ord("c")):
        r.u2()
    elif tag == ord("e"):
        r.u2()
        r.u2()
    elif tag == ord("@"):
        r.u2()
        npairs = r.u2()
        for _ in range(npairs):
            r.u2()
            _skip_annotation_value(r)
    elif tag == ord("["):
        nvals = r.u2()
        for _ in range(nvals):
            _skip_annotation_value(r)


def parse_class(data):
    r = ClassReader(data)

    magic = r.u4()
    if magic != 0xCAFEBABE:
        raise PackError(f"Bad magic: 0x{magic:08X}")
    r.u2()
    r.u2()

    cp_count = r.u2()
    cp = [None]
    i = 1
    while i < cp_count:
        tag = r.u1()
        if tag == CP_UTF8:
            length = r.u2()
            text = r.read(length).decode("utf-8", errors="replace")
            cp.append(("Utf8", text))
        elif tag == CP_INTEGER:
            value = struct.unpack_from(">i", r.read(4))[0]
            cp.append(("Integer", value))
        elif tag == CP_FLOAT:
            r.read(4)
            cp.append(("Float",))
        elif tag == CP_LONG:
            r.read(8)
            cp.append(("Long",))
            i += 1
            cp.append(None)
        elif tag == CP_DOUBLE:
            r.read(8)
            cp.append(("Double",))
            i += 1
            cp.append(None)
        elif tag == CP_CLASS:
            cp.append(("Class", r.u2()))
        elif tag == CP_STRING:
            cp.append(("String", r.u2()))
        elif tag == CP_FIELDREF:
            cp.append(("Fieldref", r.u2(), r.u2()))
        elif tag == CP_METHODREF:
            cp.append(("Methodref", r.u2(), r.u2()))
        elif tag == CP_INTERFACE_METHODREF:
            cp.append(("InterfaceMethodref", r.u2(), r.u2()))
        elif tag == CP_NAME_AND_TYPE:
            cp.append(("NameAndType", r.u2(), r.u2()))
        elif tag == CP_METHOD_HANDLE:
            r.read(3)
            cp.append(("MethodHandle",))
        elif tag == CP_METHOD_TYPE:
            r.read(2)
            cp.append(("MethodType",))
        elif tag == CP_INVOKE_DYNAMIC:
            r.read(4)
            cp.append(("InvokeDynamic",))
        else:
            raise PackError(f"Unknown CP tag {tag} at index {i}")
        i += 1

    r.u2()
    this_class = r.u2()
    super_class = r.u2()

    interfaces_count = r.u2()
    for _ in range(interfaces_count):
        r.u2()

    # Field parsing keeps only data needed by the linker plus @Const markers.
    fields = []
    for _ in range(r.u2()):
        f_access = r.u2()
        f_name = r.u2()
        f_desc = r.u2()
        is_const = False
        for _ in range(r.u2()):
            a_name_idx = r.u2()
            attr_len = r.u4()
            attr_data = r.read(attr_len)
            a_name = cp[a_name_idx][1] if cp[a_name_idx] else ""
            if a_name in ("RuntimeVisibleAnnotations",
                          "RuntimeInvisibleAnnotations"):
                ar = ClassReader(attr_data)
                for _ in range(ar.u2()):
                    type_desc = cp[ar.u2()][1]
                    for _ in range(ar.u2()):
                        ar.u2()
                        _skip_annotation_value(ar)
                    if type_desc == "LConst;" or type_desc.endswith("/Const;"):
                        is_const = True
        fields.append(FieldInfo(f_access, f_name, f_desc, is_const))

    # Method parsing preserves the raw Code attribute for the pack pipeline.
    methods = []
    for _ in range(r.u2()):
        m_access = r.u2()
        m_name_idx = r.u2()
        m_desc_idx = r.u2()
        code = None
        for _ in range(r.u2()):
            attr_name_idx = r.u2()
            attr_len = r.u4()
            attr_data = r.read(attr_len)
            attr_name = cp[attr_name_idx][1] if cp[attr_name_idx] else ""
            if attr_name == "Code":
                code = attr_data
        methods.append(MethodInfo(m_access, m_name_idx, m_desc_idx, code))

    return ParsedClass(cp, this_class, super_class, fields, methods)
