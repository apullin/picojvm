"""Common helpers shared by format emitters."""

from ..constants import PJVM_NO_CLASS, PJVM_NO_CLINIT, PJVM_NO_VTABLE


def is_no_id(value):
    return value in (PJVM_NO_CLASS, PJVM_NO_VTABLE, PJVM_NO_CLINIT)
