"""Dataclasses used by the pack pipeline."""

from dataclasses import dataclass, field
from typing import List, Optional

from .constants import PJVM_NO_CLASS, PJVM_NO_CLINIT


# Parsed classfile records retain tuple iteration while the packer is migrated.
@dataclass(frozen=True)
class FieldInfo:
    access: int
    name_index: int
    descriptor_index: int
    is_const: bool

    def __iter__(self):
        yield self.access
        yield self.name_index
        yield self.descriptor_index
        yield self.is_const


@dataclass(frozen=True)
class MethodInfo:
    access: int
    name_index: int
    descriptor_index: int
    code: Optional[bytes]

    def __iter__(self):
        yield self.access
        yield self.name_index
        yield self.descriptor_index
        yield self.code


@dataclass(frozen=True)
class ParsedClass:
    cp: list
    this_class: int
    super_class: int
    fields: List[FieldInfo]
    methods: List[MethodInfo]

    def __iter__(self):
        yield self.cp
        yield self.this_class
        yield self.super_class
        yield self.fields
        yield self.methods


# Mutable pack-time layout. Later passes fill these fields in dependency order.
@dataclass
class ClassInfo:
    """Parsed class with resolved layout and method metadata."""

    name: str
    parent_name: Optional[str]
    cp: list
    fields_raw: list
    methods_raw: list
    class_id: int = -1
    parent_class_id: int = PJVM_NO_CLASS
    static_fields: list = field(default_factory=list)
    const_fields: set = field(default_factory=set)
    own_instance_fields: list = field(default_factory=list)
    all_instance_fields: list = field(default_factory=list)
    all_instance_field_is_ref: list = field(default_factory=list)
    vtable: list = field(default_factory=list)
    method_vtable_slots: dict = field(default_factory=dict)
    global_methods: dict = field(default_factory=dict)
    clinit_mi: int = PJVM_NO_CLINIT
