#!/usr/bin/env python3
"""
pjvmpack.py — Convert Java .class files to .pjvm format for picoJVM.

Supports multiple classes with inheritance, vtable dispatch, and object fields.
The .pjvm format pre-resolves all constant pool references so the 8085
interpreter never needs to parse a class file. Bytecode streams are
preserved unmodified (branch offsets intact).

Usage:
    javac Fib.java
    python3 pjvmpack.py Fib.class -o fib.pjvm

    javac Shapes.java
    python3 pjvmpack.py Shape.class Square.class Rect.class Shapes.class -o shapes.pjvm
"""

import struct
import sys
import os

# --- Constant pool tags ---
CP_UTF8 = 1
CP_INTEGER = 3
CP_FLOAT = 4
CP_LONG = 5
CP_DOUBLE = 6
CP_CLASS = 7
CP_STRING = 8
CP_FIELDREF = 9
CP_METHODREF = 10
CP_INTERFACE_METHODREF = 11
CP_NAME_AND_TYPE = 12
CP_METHOD_HANDLE = 15
CP_METHOD_TYPE = 16
CP_INVOKE_DYNAMIC = 18

# --- Native method IDs ---
NATIVE_IDS = {
    "putchar": 0,
    "in": 1,
    "out": 2,
    "peek": 3,
    "poke": 4,
    "halt": 5,
    "print": 11,
    "arraycopy": 13,
    "memcmp": 14,
    "writeBytes": 15,
    "stringFromBytes": 16,
    "fileOpen": 17,
    "fileReadByte": 18,
    "fileWriteByte": 19,
    "fileRead": 20,
    "fileWrite": 21,
    "fileClose": 22,
}
NATIVE_OBJECT_INIT = 6

# java/lang/String methods mapped to native IDs
STRING_NATIVE_IDS = {
    ("length", "()I"): 7,
    ("charAt", "(I)C"): 8,
    ("equals", "(Ljava/lang/Object;)Z"): 9,
    ("toString", "()Ljava/lang/String;"): 10,
    ("hashCode", "()I"): 12,
}


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
        v = self.data[self.pos : self.pos + n]
        self.pos += n
        return v


def count_args(descriptor):
    """Count the number of argument slots from a method descriptor like (II)V."""
    i = 1  # skip '('
    count = 0
    while descriptor[i] != ')':
        c = descriptor[i]
        if c in ('I', 'Z', 'B', 'C', 'S', 'F'):
            count += 1
            i += 1
        elif c in ('J', 'D'):
            count += 2
            i += 1
        elif c == '[':
            while descriptor[i] == '[':
                i += 1
            if descriptor[i] == 'L':
                while descriptor[i] != ';':
                    i += 1
                i += 1
            else:
                i += 1
            count += 1
        elif c == 'L':
            while descriptor[i] != ';':
                i += 1
            i += 1
            count += 1
        else:
            raise ValueError(f"Unknown descriptor char: {c}")
    return count


def parse_class(data):
    r = ClassReader(data)

    magic = r.u4()
    if magic != 0xCAFEBABE:
        raise ValueError(f"Bad magic: 0x{magic:08X}")
    minor = r.u2()
    major = r.u2()

    # --- Constant pool ---
    cp_count = r.u2()
    cp = [None]  # CP is 1-indexed
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
            name_index = r.u2()
            cp.append(("Class", name_index))
        elif tag == CP_STRING:
            string_index = r.u2()
            cp.append(("String", string_index))
        elif tag == CP_FIELDREF:
            class_index = r.u2()
            nat_index = r.u2()
            cp.append(("Fieldref", class_index, nat_index))
        elif tag == CP_METHODREF:
            class_index = r.u2()
            nat_index = r.u2()
            cp.append(("Methodref", class_index, nat_index))
        elif tag == CP_INTERFACE_METHODREF:
            class_index = r.u2()
            nat_index = r.u2()
            cp.append(("InterfaceMethodref", class_index, nat_index))
        elif tag == CP_NAME_AND_TYPE:
            name_index = r.u2()
            desc_index = r.u2()
            cp.append(("NameAndType", name_index, desc_index))
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
            raise ValueError(f"Unknown CP tag {tag} at index {i}")
        i += 1

    access_flags = r.u2()
    this_class = r.u2()
    super_class = r.u2()

    interfaces_count = r.u2()
    for _ in range(interfaces_count):
        r.u2()

    fields_count = r.u2()
    fields = []
    for _ in range(fields_count):
        f_access = r.u2()
        f_name = r.u2()
        f_desc = r.u2()
        f_attr_count = r.u2()
        for _ in range(f_attr_count):
            r.u2()
            attr_len = r.u4()
            r.read(attr_len)
        fields.append((f_access, f_name, f_desc))

    methods_count = r.u2()
    methods = []
    for _ in range(methods_count):
        m_access = r.u2()
        m_name_idx = r.u2()
        m_desc_idx = r.u2()
        m_attr_count = r.u2()
        code = None
        for _ in range(m_attr_count):
            attr_name_idx = r.u2()
            attr_len = r.u4()
            attr_data = r.read(attr_len)
            attr_name = cp[attr_name_idx][1] if cp[attr_name_idx] else ""
            if attr_name == "Code":
                code = attr_data
        methods.append((m_access, m_name_idx, m_desc_idx, code))

    return cp, this_class, super_class, fields, methods


def resolve_class_name(cp, class_idx):
    """Resolve a CONSTANT_Class to its UTF-8 name."""
    if class_idx == 0:
        return None
    entry = cp[class_idx]
    if entry[0] != "Class":
        raise ValueError(f"CP#{class_idx} is not a Class")
    return cp[entry[1]][1]


def resolve_method_name(cp, methodref_idx):
    """Resolve a CONSTANT_Methodref or InterfaceMethodref to (class_name, method_name, descriptor)."""
    entry = cp[methodref_idx]
    if entry[0] not in ("Methodref", "InterfaceMethodref"):
        raise ValueError(f"CP#{methodref_idx} is not a Methodref/InterfaceMethodref")
    class_name = resolve_class_name(cp, entry[1])
    nat = cp[entry[2]]
    method_name = cp[nat[1]][1]
    descriptor = cp[nat[2]][1]
    return class_name, method_name, descriptor


# ----- Multi-class packing -----

class ClassInfo:
    """Parsed class with resolved fields and methods."""
    def __init__(self, name, parent_name, cp, fields_raw, methods_raw):
        self.name = name
        self.parent_name = parent_name  # None for java/lang/Object
        self.cp = cp
        self.fields_raw = fields_raw
        self.methods_raw = methods_raw
        self.class_id = -1
        self.parent_class_id = 0xFF
        self.static_fields = []      # field names
        self.own_instance_fields = [] # (name, global_slot_index) pairs
        self.all_instance_fields = [] # names, including inherited
        self.vtable = []             # list of global method indices
        self.method_vtable_slots = {}  # (name, desc) -> vtable_slot
        self.global_methods = {}     # (name, desc) -> global method index
        self.clinit_mi = 0xFF        # global method index of <clinit>, 0xFF if none


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


def pack_pjvm(class_data_list, verbose=False, v2=False, pin_hints=None):
    """Pack one or more .class files into a single .pjvm binary."""

    # --- Step 1: Parse all classes ---
    classes = {}
    for class_data in class_data_list:
        cp, this_class, super_class, fields, methods = parse_class(class_data)
        name = resolve_class_name(cp, this_class)
        parent_name = resolve_class_name(cp, super_class)
        classes[name] = ClassInfo(name, parent_name, cp, fields, methods)

    if verbose:
        print(f"Parsed {len(classes)} classes: {', '.join(classes.keys())}")

    # --- Step 2: Topological sort (parents first) ---
    class_order = topological_sort(classes)
    for i, name in enumerate(class_order):
        classes[name].class_id = i
        parent_name = classes[name].parent_name
        if parent_name and parent_name in classes:
            classes[name].parent_class_id = classes[parent_name].class_id
        else:
            classes[name].parent_class_id = 0xFF

    if verbose:
        for name in class_order:
            cls = classes[name]
            parent = f"extends {cls.parent_name}" if cls.parent_name else ""
            print(f"  Class #{cls.class_id}: {name} {parent}")

    # --- Step 2b: Synthesize exception classes ---
    # Standard exception hierarchy (parent → child)
    EXCEPTION_HIERARCHY = {
        "java/lang/Throwable": "java/lang/Object",
        "java/lang/Exception": "java/lang/Throwable",
        "java/lang/RuntimeException": "java/lang/Exception",
        "java/lang/ArithmeticException": "java/lang/RuntimeException",
        "java/lang/NullPointerException": "java/lang/RuntimeException",
        "java/lang/ArrayIndexOutOfBoundsException": "java/lang/RuntimeException",
        "java/lang/IndexOutOfBoundsException": "java/lang/RuntimeException",
        "java/lang/ClassCastException": "java/lang/RuntimeException",
        "java/lang/IllegalArgumentException": "java/lang/RuntimeException",
        "java/lang/StackOverflowError": "java/lang/Throwable",
    }

    # Collect all exception class names referenced in exception tables and athrow
    exc_classes_needed = set()
    for name in class_order:
        cls = classes[name]
        for m_access, m_name_idx, m_desc_idx, code_data in cls.methods_raw:
            if code_data is None:
                continue
            cr = ClassReader(code_data)
            cr.u2(); cr.u2()  # skip max_stack, max_locals
            code_length = cr.u4()
            cr.read(code_length)
            ec = cr.u2()
            for _ in range(ec):
                cr.u2(); cr.u2(); cr.u2()  # start, end, handler
                catch_type = cr.u2()
                if catch_type != 0:
                    cname = resolve_class_name(cls.cp, catch_type)
                    if cname and cname not in classes:
                        exc_classes_needed.add(cname)

        # Also check for 'new' of exception classes (for throw new X())
        for cp_idx in range(1, len(cls.cp)):
            entry = cls.cp[cp_idx]
            if entry and entry[0] == "Class":
                cname = cls.cp[entry[1]][1]
                if cname in EXCEPTION_HIERARCHY and cname not in classes:
                    exc_classes_needed.add(cname)

    # Add parent chain classes
    to_add = set(exc_classes_needed)
    while to_add:
        next_add = set()
        for ename in to_add:
            parent = EXCEPTION_HIERARCHY.get(ename)
            if parent and parent != "java/lang/Object" and parent not in classes and parent not in exc_classes_needed:
                exc_classes_needed.add(parent)
                next_add.add(parent)
        to_add = next_add

    # Create synthetic class entries in dependency order
    if exc_classes_needed:
        # Sort so parents come before children
        sorted_exc = []
        added = set(classes.keys())
        remaining = set(exc_classes_needed)
        while remaining:
            progress = False
            for ename in list(remaining):
                parent = EXCEPTION_HIERARCHY.get(ename, "java/lang/Object")
                if parent == "java/lang/Object" or parent in added:
                    sorted_exc.append(ename)
                    added.add(ename)
                    remaining.discard(ename)
                    progress = True
            if not progress:
                # Break cycles by just adding remaining
                sorted_exc.extend(remaining)
                break

        for ename in sorted_exc:
            parent = EXCEPTION_HIERARCHY.get(ename, "java/lang/Object")
            cid = len(class_order)
            cls = ClassInfo(ename, parent, [], [], [])
            cls.class_id = cid
            if parent in classes:
                cls.parent_class_id = classes[parent].class_id
            else:
                cls.parent_class_id = 0xFF
            classes[ename] = cls
            class_order.append(ename)
            if verbose:
                print(f"  Synthetic exception class #{cid}: {ename} "
                      f"(parent_id={cls.parent_class_id})")

    # Re-resolve parent_class_ids now that synthetic classes exist
    for name in class_order:
        cls = classes[name]
        if cls.parent_class_id == 0xFF and cls.parent_name and cls.parent_name in classes:
            cls.parent_class_id = classes[cls.parent_name].class_id

    # --- Step 3: Build field layouts ---
    for name in class_order:
        cls = classes[name]
        parent = classes.get(cls.parent_name) if cls.parent_name else None

        if parent:
            cls.all_instance_fields = list(parent.all_instance_fields)
        else:
            cls.all_instance_fields = []

        for f_access, f_name_idx, f_desc_idx in cls.fields_raw:
            field_name = cls.cp[f_name_idx][1]
            if f_access & 0x0008:  # ACC_STATIC
                cls.static_fields.append(field_name)
            else:
                slot = len(cls.all_instance_fields)
                cls.own_instance_fields.append((field_name, slot))
                cls.all_instance_fields.append(field_name)

        if verbose and cls.all_instance_fields:
            print(f"    Instance fields: {cls.all_instance_fields}")

    # --- Step 4: Build global method table and vtables ---
    vmid_map = {}
    next_vmid = [0]

    def get_vmid(mname, mdesc):
        key = (mname, mdesc)
        if key not in vmid_map:
            vmid_map[key] = next_vmid[0]
            next_vmid[0] += 1
        return vmid_map[key]

    method_table = []

    for name in class_order:
        cls = classes[name]
        parent = classes.get(cls.parent_name) if cls.parent_name else None

        # Inherit parent's vtable
        if parent:
            cls.vtable = list(parent.vtable)
            cls.method_vtable_slots = dict(parent.method_vtable_slots)
        else:
            cls.vtable = []
            cls.method_vtable_slots = {}

        for m_access, m_name_idx, m_desc_idx, code_data in cls.methods_raw:
            m_name = cls.cp[m_name_idx][1]
            m_desc = cls.cp[m_desc_idx][1]

            is_static = bool(m_access & 0x0008)
            is_native = bool(m_access & 0x0100)

            if is_native:
                continue  # declared native (like Native.java) — skip

            if code_data is None:
                continue

            arg_count = count_args(m_desc)
            if not is_static:
                arg_count += 1  # 'this'

            # Parse Code attribute
            cr = ClassReader(code_data)
            max_stack = cr.u2()
            max_locals = cr.u2()
            code_length = cr.u4()
            bytecode = cr.read(code_length)
            exc_count = cr.u2()
            exc_table = []
            for _ in range(exc_count):
                e_start = cr.u2()
                e_end = cr.u2()
                e_handler = cr.u2()
                e_catch_type = cr.u2()  # CP index (0 = catch-all)
                exc_table.append((e_start, e_end, e_handler, e_catch_type))

            # Parse Code sub-attributes (LineNumberTable, etc.)
            line_number_table = []
            code_attr_count = cr.u2()
            for _ in range(code_attr_count):
                ca_name_idx = cr.u2()
                ca_len = cr.u4()
                ca_data = cr.read(ca_len)
                ca_name = cls.cp[ca_name_idx][1] if cls.cp[ca_name_idx] else ""
                if ca_name == "LineNumberTable":
                    lr = ClassReader(ca_data)
                    lnt_len = lr.u2()
                    for _ in range(lnt_len):
                        start_pc = lr.u2()
                        line_num = lr.u2()
                        line_number_table.append((start_pc, line_num))

            global_idx = len(method_table)
            if m_name == "<clinit>":
                cls.clinit_mi = global_idx

            # Determine vtable slot
            vtable_slot = 0xFF  # not virtual (static or constructor)
            if not is_static and m_name != "<init>":
                key = (m_name, m_desc)
                if key in cls.method_vtable_slots:
                    # Override parent method
                    vtable_slot = cls.method_vtable_slots[key]
                    cls.vtable[vtable_slot] = global_idx
                else:
                    # New virtual method
                    vtable_slot = len(cls.vtable)
                    cls.vtable.append(global_idx)
                cls.method_vtable_slots[key] = vtable_slot

            cls.global_methods[(m_name, m_desc)] = global_idx

            method_table.append({
                "name": m_name,
                "descriptor": m_desc,
                "max_locals": max_locals,
                "max_stack": max_stack,
                "arg_count": arg_count,
                "bytecode": bytecode,
                "is_native": False,
                "native_id": 0,
                "class_id": cls.class_id,
                "vtable_slot": vtable_slot,
                "vmid": get_vmid(m_name, m_desc) if vtable_slot != 0xFF else 0xFF,
                "cp_base": 0,  # filled later
                "exc_table": exc_table,
                "line_table": line_number_table,
            })

            if verbose:
                vt_str = f"vt={vtable_slot}" if vtable_slot != 0xFF else "static"
                print(f"  Method #{global_idx}: {name}.{m_name}{m_desc} "
                      f"(locals={max_locals}, stack={max_stack}, args={arg_count}, "
                      f"code={len(bytecode)}B, {vt_str})")

    # --- Step 5: Find main method ---
    main_index = None
    for i, mt in enumerate(method_table):
        if mt["name"] == "main" and mt["descriptor"] == "([Ljava/lang/String;)V":
            main_index = i
            break
    if main_index is None:
        raise ValueError("No main method found")

    # --- Step 6: Add native/external methods ---
    native_cache = {}  # (class, name, desc) -> global method index

    for name in class_order:
        cls = classes[name]
        for cp_idx in range(1, len(cls.cp)):
            entry = cls.cp[cp_idx]
            if entry is None or entry[0] != "Methodref":
                continue
            ref_class, ref_method, ref_desc = resolve_method_name(cls.cp, cp_idx)
            key = (ref_class, ref_method, ref_desc)

            if key in native_cache:
                continue

            if ref_class == "Native":
                if ref_method not in NATIVE_IDS:
                    raise ValueError(f"Unknown native: Native.{ref_method}")
                nm_idx = len(method_table)
                a_count = count_args(ref_desc)
                method_table.append({
                    "name": ref_method,
                    "descriptor": ref_desc,
                    "max_locals": a_count,
                    "max_stack": 0,
                    "arg_count": a_count,
                    "bytecode": b"",
                    "is_native": True,
                    "native_id": NATIVE_IDS[ref_method],
                    "class_id": 0xFF,
                    "vtable_slot": 0xFF,
                    "vmid": 0xFF,
                    "cp_base": 0,
                    "exc_table": [],
                    "line_table": [],
                })
                native_cache[key] = nm_idx
                if verbose:
                    print(f"  Native #{nm_idx}: Native.{ref_method}{ref_desc} "
                          f"(id={NATIVE_IDS[ref_method]})")

            elif ref_method == "<init>" and (
                    ref_class == "java/lang/Object" or
                    ref_class in EXCEPTION_HIERARCHY):
                nm_idx = len(method_table)
                method_table.append({
                    "name": "<init>",
                    "descriptor": "()V",
                    "max_locals": 1,
                    "max_stack": 0,
                    "arg_count": 1,
                    "bytecode": b"",
                    "is_native": True,
                    "native_id": NATIVE_OBJECT_INIT,
                    "class_id": 0xFF,
                    "vtable_slot": 0xFF,
                    "vmid": 0xFF,
                    "cp_base": 0,
                    "exc_table": [],
                    "line_table": [],
                })
                native_cache[key] = nm_idx
                if verbose:
                    print(f"  Native #{nm_idx}: {ref_class}.<init>()V (no-op)")

            elif ref_class == "java/lang/String":
                str_key = (ref_method, ref_desc)
                if str_key in STRING_NATIVE_IDS:
                    nm_idx = len(method_table)
                    a_count = count_args(ref_desc) + 1  # +1 for 'this'
                    method_table.append({
                        "name": ref_method,
                        "descriptor": ref_desc,
                        "max_locals": a_count,
                        "max_stack": 0,
                        "arg_count": a_count,
                        "bytecode": b"",
                        "is_native": True,
                        "native_id": STRING_NATIVE_IDS[str_key],
                        "class_id": 0xFF,
                        "vtable_slot": 0xFF,
                        "vmid": 0xFF,
                        "cp_base": 0,
                        "exc_table": [],
                        "line_table": [],
                    })
                    native_cache[key] = nm_idx
                    if verbose:
                        print(f"  Native #{nm_idx}: String.{ref_method}"
                              f"{ref_desc} (id={STRING_NATIVE_IDS[str_key]})")

    # --- Step 6b: Add interface method stubs ---
    for name in class_order:
        cls = classes[name]
        for cp_idx in range(1, len(cls.cp)):
            entry = cls.cp[cp_idx]
            if entry is None or entry[0] != "InterfaceMethodref":
                continue
            ref_class, ref_method, ref_desc = resolve_method_name(cls.cp, cp_idx)
            key = (ref_class, ref_method, ref_desc)
            if key in native_cache:
                continue
            nm_idx = len(method_table)
            a_count = count_args(ref_desc) + 1  # +1 for 'this'
            method_table.append({
                "name": ref_method,
                "descriptor": ref_desc,
                "max_locals": a_count,
                "max_stack": 0,
                "arg_count": a_count,
                "bytecode": b"",
                "is_native": False,
                "native_id": 0,
                "class_id": 0xFF,
                "vtable_slot": 0xFF,
                "vmid": get_vmid(ref_method, ref_desc),
                "cp_base": 0,
                "exc_table": [],
                "line_table": [],
            })
            native_cache[key] = nm_idx
            if verbose:
                print(f"  Interface stub #{nm_idx}: "
                      f"{ref_class}.{ref_method}{ref_desc} "
                      f"(vmid={method_table[-1]['vmid']})")

    # --- Step 7: Build per-class CP resolution, concatenate with offsets ---
    global_cp_resolve = bytearray()
    global_int_constants = []
    global_string_constants = []  # list of UTF-8 byte strings
    string_constant_dedup = {}   # utf8_text -> index

    # Static field offsets per class (into global static_fields array)
    static_field_base = {}
    total_static_fields = 0
    for name in class_order:
        cls = classes[name]
        static_field_base[name] = total_static_fields
        total_static_fields += len(cls.static_fields)

    cp_bases = {}  # class_name -> cp_base offset

    for name in class_order:
        cls = classes[name]
        cp = cls.cp
        cp_base = len(global_cp_resolve)
        cp_bases[name] = cp_base

        cp_resolve = bytearray(len(cp))
        for i in range(len(cp_resolve)):
            cp_resolve[i] = 0xFF

        # Resolve Methodrefs
        for cp_idx in range(1, len(cp)):
            entry = cp[cp_idx]
            if entry is None or entry[0] != "Methodref":
                continue
            ref_class, ref_method, ref_desc = resolve_method_name(cp, cp_idx)
            nkey = (ref_class, ref_method, ref_desc)

            # Check native cache first
            if nkey in native_cache:
                cp_resolve[cp_idx] = native_cache[nkey]
                continue

            # Search in our classes (walk up hierarchy)
            mkey = (ref_method, ref_desc)
            resolved = False
            walk = ref_class
            while walk:
                if walk in classes:
                    target = classes[walk]
                    if mkey in target.global_methods:
                        cp_resolve[cp_idx] = target.global_methods[mkey]
                        resolved = True
                        break
                    walk = target.parent_name
                else:
                    break

            if not resolved and verbose:
                print(f"  WARNING: unresolved Methodref "
                      f"{ref_class}.{ref_method}{ref_desc}")

        # Resolve InterfaceMethodrefs
        for cp_idx in range(1, len(cp)):
            entry = cp[cp_idx]
            if entry is None or entry[0] != "InterfaceMethodref":
                continue
            ref_class, ref_method, ref_desc = resolve_method_name(cp, cp_idx)
            nkey = (ref_class, ref_method, ref_desc)
            if nkey in native_cache:
                cp_resolve[cp_idx] = native_cache[nkey]

        # Resolve Fieldrefs
        for cp_idx in range(1, len(cp)):
            entry = cp[cp_idx]
            if entry is None or entry[0] != "Fieldref":
                continue
            ref_class_name = resolve_class_name(cp, entry[1])
            nat = cp[entry[2]]
            field_name = cp[nat[1]][1]

            target_cls = classes.get(ref_class_name)
            if not target_cls:
                # Walk up from referencing class
                walk = name
                while walk and walk in classes:
                    target_cls = classes[walk]
                    break
                if not target_cls:
                    continue

            # Check static fields
            found = False
            for slot, sf in enumerate(target_cls.static_fields):
                if sf == field_name:
                    cp_resolve[cp_idx] = static_field_base[ref_class_name] + slot
                    found = True
                    break

            if not found:
                # Check instance fields (search up hierarchy)
                walk = ref_class_name
                while walk and walk in classes:
                    tc = classes[walk]
                    for slot, iname in enumerate(tc.all_instance_fields):
                        if iname == field_name:
                            cp_resolve[cp_idx] = slot
                            found = True
                            break
                    if found:
                        break
                    walk = tc.parent_name

        # Resolve Integer constants
        for cp_idx in range(1, len(cp)):
            entry = cp[cp_idx]
            if entry is None or entry[0] != "Integer":
                continue
            ic_idx = len(global_int_constants)
            global_int_constants.append(entry[1])
            cp_resolve[cp_idx] = ic_idx

        # Resolve String constants → 0x80 | string_index
        for cp_idx in range(1, len(cp)):
            entry = cp[cp_idx]
            if entry is None or entry[0] != "String":
                continue
            utf8_text = cp[entry[1]][1]  # CONSTANT_String -> CONSTANT_Utf8
            if utf8_text in string_constant_dedup:
                sc_idx = string_constant_dedup[utf8_text]
            else:
                sc_idx = len(global_string_constants)
                global_string_constants.append(utf8_text.encode("utf-8"))
                string_constant_dedup[utf8_text] = sc_idx
            cp_resolve[cp_idx] = 0x80 | sc_idx

        # Resolve Class refs → class_id (for 'new' and 'anewarray')
        for cp_idx in range(1, len(cp)):
            entry = cp[cp_idx]
            if entry is None or entry[0] != "Class":
                continue
            ref_name = cp[entry[1]][1]
            if ref_name in classes:
                cp_resolve[cp_idx] = classes[ref_name].class_id

        global_cp_resolve.extend(cp_resolve)

    # Set cp_base on all method entries
    for mt in method_table:
        cid = mt["class_id"]
        if cid != 0xFF:
            for cname in class_order:
                if classes[cname].class_id == cid:
                    mt["cp_base"] = cp_bases[cname]
                    break

    # --- Step 7b: Build global exception table ---
    global_exc_table = []  # list of (start_pc, end_pc, handler_pc, catch_class_id)
    for mt in method_table:
        mt["exc_offset_idx"] = len(global_exc_table)
        mt["exc_count"] = len(mt["exc_table"])
        for (e_start, e_end, e_handler, e_catch_cp) in mt["exc_table"]:
            if e_catch_cp == 0:
                catch_cid = 0xFF  # catch-all (finally)
            else:
                # Resolve CP index to class name, then to class_id
                cid = mt.get("class_id", 0xFF)
                catch_cid = 0xFF
                # Find the class whose CP owns this method
                if cid != 0xFF:
                    for cname in class_order:
                        if classes[cname].class_id == cid:
                            cp = classes[cname].cp
                            catch_name = resolve_class_name(cp, e_catch_cp)
                            if catch_name and catch_name in classes:
                                catch_cid = classes[catch_name].class_id
                            elif verbose:
                                print(f"  WARNING: unresolved catch type "
                                      f"CP#{e_catch_cp} ({catch_name})")
                            break
            global_exc_table.append((e_start, e_end, e_handler, catch_cid))

    if verbose and global_exc_table:
        print(f"  Exception table: {len(global_exc_table)} entries")
        for i, (s, e, h, ct) in enumerate(global_exc_table):
            ct_str = "catch-all" if ct == 0xFF else f"class#{ct}"
            print(f"    [{i}] start={s} end={e} handler={h} {ct_str}")

    # --- Step 8: Concatenate bytecodes ---
    bytecode_section = bytearray()
    for mt in method_table:
        if mt["is_native"]:
            mt["code_offset"] = 0
        else:
            mt["code_offset"] = len(bytecode_section)
            bytecode_section.extend(mt["bytecode"])

    # --- Step 9: Emit .pjvm binary ---
    out = bytearray()

    if v2:
        # v2 Header (14 bytes)
        hdr_size = 14
        mt_entry_size = 14
        region_flags = 0
        if pin_hints:
            region_flags |= 0x02  # bit 1: pin hints present
        out.append(0x85)           # magic
        out.append(0x4B)           # v2
        out.append(len(method_table))
        out.append(main_index)
        out.append(total_static_fields)
        out.append(len(global_int_constants))
        out.append(len(class_order))
        out.append(len(global_string_constants))
        out.extend(struct.pack("<I", len(bytecode_section)))  # 32-bit
        out.append(region_flags)   # [12] region_flags
        out.append(0)              # [13] reserved
    else:
        # v1 Header (10 bytes)
        hdr_size = 10
        mt_entry_size = 12
        out.append(0x85)           # magic
        out.append(0x4A)           # v1
        out.append(len(method_table))
        out.append(main_index)
        out.append(total_static_fields)
        out.append(len(global_int_constants))
        out.append(len(class_order))
        out.append(len(global_string_constants))
        out.extend(struct.pack("<H", len(bytecode_section)))  # 16-bit

    # Class table (variable length)
    for name in class_order:
        cls = classes[name]
        out.append(cls.parent_class_id)
        out.append(len(cls.all_instance_fields))
        out.append(len(cls.vtable))
        out.append(cls.clinit_mi)
        for vt_entry in cls.vtable:
            out.append(vt_entry)

    # Method table
    for mt in method_table:
        flags = 0
        if mt["is_native"]:
            flags = 1 | (mt["native_id"] << 1)
        out.append(mt["max_locals"])
        out.append(mt["max_stack"])
        out.append(mt["arg_count"])
        out.append(flags)
        if v2:
            out.extend(struct.pack("<I", mt["code_offset"]))  # 32-bit
        else:
            out.extend(struct.pack("<H", mt["code_offset"]))  # 16-bit
        out.extend(struct.pack("<H", mt["cp_base"]))
        out.append(mt["vtable_slot"])
        out.append(mt.get("vmid", 0xFF))
        out.append(mt["exc_count"])
        out.append(mt["exc_offset_idx"])

    # CP resolution table
    out.extend(struct.pack("<H", len(global_cp_resolve)))
    out.extend(global_cp_resolve)

    # Integer constants (4 bytes each, little-endian)
    for val in global_int_constants:
        out.extend(struct.pack("<i", val))

    # String constants (len_le16 + utf8_bytes each)
    for s in global_string_constants:
        out.extend(struct.pack("<H", len(s)))
        out.extend(s)

    # Bytecode section
    out.extend(bytecode_section)

    # Exception table section (7 bytes per entry)
    for (e_start, e_end, e_handler, e_catch_cid) in global_exc_table:
        out.extend(struct.pack("<H", e_start))
        out.extend(struct.pack("<H", e_end))
        out.extend(struct.pack("<H", e_handler))
        out.append(e_catch_cid)

    # Pin hints (one byte per method, after exception table, v2 only)
    if v2 and pin_hints:
        pin_set = set(pin_hints)
        for i in range(len(method_table)):
            out.append(1 if i in pin_set else 0)
        if verbose:
            print(f"  Pin hints: methods {sorted(pin_set)}")

    if verbose:
        fmt_str = "v2" if v2 else "v1"
        print(f"\n.pjvm output ({fmt_str}): {len(out)} bytes")
        print(f"  Header: {hdr_size} bytes")
        ct_size = sum(4 + len(classes[n].vtable) for n in class_order)
        print(f"  Class table: {len(class_order)} classes, {ct_size} bytes")
        print(f"  Method table: {len(method_table)} × {mt_entry_size} = "
              f"{len(method_table) * mt_entry_size} bytes")
        print(f"  CP resolution: {len(global_cp_resolve) + 2} bytes")
        print(f"  Int constants: {len(global_int_constants)} × 4 = "
              f"{len(global_int_constants) * 4} bytes")
        sc_size = sum(2 + len(s) for s in global_string_constants)
        print(f"  String constants: {len(global_string_constants)}, "
              f"{sc_size} bytes")
        for i, s in enumerate(global_string_constants):
            print(f"    [{i}] \"{s.decode('utf-8')}\" ({len(s)} bytes)")
        print(f"  Bytecodes: {len(bytecode_section)} bytes")
        print(f"  Exception table: {len(global_exc_table)} × 7 = "
              f"{len(global_exc_table) * 7} bytes")
        print(f"  Entry point: method #{main_index} "
              f"({method_table[main_index]['name']})")
        for name in class_order:
            cls = classes[name]
            if cls.vtable:
                vt_str = ", ".join(
                    f"{method_table[m]['name']}" for m in cls.vtable)
                print(f"  {name} vtable[{len(cls.vtable)}]: {vt_str}")

    return bytes(out), class_order, method_table


def emit_map(class_order, method_table):
    """Emit a packed binary .pjvmmap file for exception trace decoding.

    Format:
      u8  n_classes
      u8  n_methods
      Class entries (n_classes × variable):
        u8  name_len
        u8[name_len]  class name (short form, e.g. "MyException")
      Method entries (n_methods × variable):
        u8  class_id
        u8  name_len
        u8[name_len]  method name
        u16 code_offset (LE)
        u8  n_lines
        [u16 bc_offset, u16 line_number] × n_lines (LE)
    """
    out = bytearray()
    out.append(len(class_order))
    out.append(len(method_table))

    for name in class_order:
        # Use short class name (after last /)
        short = name.rsplit("/", 1)[-1]
        encoded = short.encode("utf-8")
        out.append(len(encoded))
        out.extend(encoded)

    for mt in method_table:
        cid = mt["class_id"] if mt["class_id"] != 0xFF else 0xFF
        out.append(cid)
        encoded = mt["name"].encode("utf-8")
        out.append(len(encoded))
        out.extend(encoded)
        out.extend(struct.pack("<H", mt.get("code_offset", 0)))
        lines = mt.get("line_table", [])
        out.append(len(lines))
        for (pc, line) in lines:
            out.extend(struct.pack("<H", pc))
            out.extend(struct.pack("<H", line))

    return bytes(out)


def main():
    import argparse
    parser = argparse.ArgumentParser(
        description="Convert .class files to .pjvm for picoJVM")
    parser.add_argument("classfiles", nargs="+", help="Input .class files")
    parser.add_argument("-o", "--output", help="Output .pjvm file")
    parser.add_argument("-v", "--verbose", action="store_true",
                        help="Show details")
    parser.add_argument("--hex", action="store_true",
                        help="Print hex dump of output")
    parser.add_argument("--c-header", help="Emit C header with embedded array")
    parser.add_argument("--emit-map", action="store_true",
                        help="Emit .pjvmmap sidecar for exception trace decoding")
    parser.add_argument("--v2", action="store_true",
                        help="Emit v2 format (32-bit code offsets, 32-bit bytecodes_size)")
    parser.add_argument("--pin-hints",
                        help="Comma-separated method indices to recommend pinning (v2 only)")
    args = parser.parse_args()

    class_data_list = []
    for path in args.classfiles:
        with open(path, "rb") as f:
            class_data_list.append(f.read())

    pin_hints = None
    if args.pin_hints:
        pin_hints = [int(x) for x in args.pin_hints.split(",")]
        if not args.v2:
            print("WARNING: --pin-hints requires --v2, ignoring", file=sys.stderr)
            pin_hints = None

    pjvm, class_order, method_table = pack_pjvm(class_data_list,
                                                verbose=args.verbose,
                                                v2=args.v2,
                                                pin_hints=pin_hints)

    out_path = args.output
    if not out_path:
        base = args.classfiles[0].replace(".class", "")
        out_path = base + ".pjvm"
    with open(out_path, "wb") as f:
        f.write(pjvm)
    print(f"Wrote {out_path} ({len(pjvm)} bytes)")

    if args.hex:
        print("\nHex dump:")
        for i in range(0, len(pjvm), 16):
            hex_part = " ".join(f"{b:02X}" for b in pjvm[i : i + 16])
            ascii_part = "".join(
                chr(b) if 32 <= b < 127 else "." for b in pjvm[i : i + 16])
            print(f"  {i:04X}: {hex_part:<48s} {ascii_part}")

    if args.c_header:
        with open(args.c_header, "w") as f:
            f.write(f"// Auto-generated by pjvmpack.py\n")
            f.write(f"static const unsigned char pjvm_program[] = {{\n")
            for i in range(0, len(pjvm), 16):
                line = ", ".join(f"0x{b:02X}" for b in pjvm[i : i + 16])
                f.write(f"    {line},\n")
            f.write(f"}};\n")
            f.write(f"static const unsigned int pjvm_program_size = {len(pjvm)};\n")
        print(f"Wrote {args.c_header}")

    if args.emit_map:
        map_data = emit_map(class_order, method_table)
        map_path = out_path.replace(".pjvm", ".pjvmmap")
        with open(map_path, "wb") as f:
            f.write(map_data)
        print(f"Wrote {map_path} ({len(map_data)} bytes)")


if __name__ == "__main__":
    main()
