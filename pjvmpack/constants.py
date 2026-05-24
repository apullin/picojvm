"""Constants shared by the pjvmpack pipeline."""

# .pjvm format constants (must be kept in sync with pjvm.h)
PJVM_MAGIC = 0x85
PJVM_VERSION_V3 = 0x4C
PJVM_VERSION_V4 = 0x4D
PJVM_HDR_SIZE_V3 = 16
PJVM_HDR_SIZE_V4 = 24
PJVM_MT_ENTRY = 14
PJVM_MT_ENTRY_V4 = 24
PJVM_ET_ENTRY = 7
PJVM_ET_ENTRY_V4 = 8

# region_flags (header byte 9)
PJVM_RF_PIN_HINTS = 0x01
PJVM_RF_REF_BITMAPS = 0x02
PJVM_RF_CONST_DATA = 0x04
PJVM_RF_PACKED_METHOD_TABLE = 0x08

# CP resolution flags
PJVM_CP_STR_FLAG = 0x8000
PJVM_CP_UNRESOLVED = 0xFFFF

# Internal sentinels. File-format sentinels are applied only while emitting:
# v3 uses 0xFF; v4 uses 0xFFFF.
PJVM_NO_CLASS = -1
PJVM_NO_VTABLE = -1
PJVM_NO_CLINIT = -1

PJVM_MAX_U8 = 0xFF
PJVM_MAX_REAL_ID = 0xFE
PJVM_MAX_U16 = 0xFFFF
PJVM_MAX_REAL_ID_V4 = 0xFFFE
PJVM_MAX_VTABLE_ENTRIES_V3 = 0x100
PJVM_MAX_VTABLE_ENTRIES_V4 = 0x10000

# JVM access flags
ACC_STATIC = 0x0008
ACC_NATIVE = 0x0100

# const_data elem_type codes
PJVM_ELEM_BYTE = 0
PJVM_ELEM_CHAR = 1
PJVM_ELEM_SHORT = 2
PJVM_ELEM_INT = 3
PJVM_ELEM_STRING_REF = 4
PJVM_CONST_NULL_REF = 0xFFFF

# Constant pool tags
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

# Native method IDs
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
    "fileDelete": 23,
    "termInfo": 24,
    "keyRead": 25,
    "ticks": 26,
}
NATIVE_OBJECT_INIT = 6

STRING_NATIVE_IDS = {
    ("length", "()I"): 7,
    ("charAt", "(I)C"): 8,
    ("equals", "(Ljava/lang/Object;)Z"): 9,
    ("toString", "()Ljava/lang/String;"): 10,
    ("hashCode", "()I"): 12,
}
