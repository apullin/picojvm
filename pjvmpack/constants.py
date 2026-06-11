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
PJVM_REF_ROM_STRING = 0x8000

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
ACC_FINAL = 0x0010
ACC_STATIC = 0x0008
ACC_NATIVE = 0x0100

# const_data elem_type codes
PJVM_ELEM_BYTE = 0
PJVM_ELEM_CHAR = 1
PJVM_ELEM_SHORT = 2
PJVM_ELEM_INT = 3
PJVM_ELEM_STRING_REF = 4
PJVM_ELEM_OBJECT_REF = 5
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
    ("isEmpty", "()Z"): 33,
    ("substring", "(I)Ljava/lang/String;"): 34,
    ("substring", "(II)Ljava/lang/String;"): 35,
    ("indexOf", "(I)I"): 36,
    ("indexOf", "(II)I"): 37,
    ("indexOf", "(Ljava/lang/String;)I"): 38,
    ("lastIndexOf", "(I)I"): 39,
    ("lastIndexOf", "(II)I"): 40,
    ("startsWith", "(Ljava/lang/String;)Z"): 41,
    ("endsWith", "(Ljava/lang/String;)Z"): 42,
    ("equalsIgnoreCase", "(Ljava/lang/String;)Z"): 43,
    ("regionMatches", "(ZILjava/lang/String;II)Z"): 44,
    ("replace", "(CC)Ljava/lang/String;"): 45,
    ("toLowerCase", "()Ljava/lang/String;"): 46,
    ("toCharArray", "()[C"): 47,
    ("contains", "(Ljava/lang/CharSequence;)Z"): 48,
    # Alias: code compiled against the picoJVM String shim (whose contains
    # takes String) but packed without it still resolves to the native.
    ("contains", "(Ljava/lang/String;)Z"): 48,
    ("compareTo", "(Ljava/lang/String;)I"): 49,
    ("indexOf", "(Ljava/lang/String;I)I"): 50,
    ("compareToIgnoreCase", "(Ljava/lang/String;)I"): 51,
    ("trim", "()Ljava/lang/String;"): 52,
    ("toUpperCase", "()Ljava/lang/String;"): 53,
    ("getBytes", "()[B"): 54,
    ("toLowerCase", "(Ljava/util/Locale;)Ljava/lang/String;"): 58,
    ("<init>", "([C)V"): 61,
    ("<init>", "([CII)V"): 62,
    ("<init>", "([B)V"): 63,
    ("<init>", "([BII)V"): 64,
    ("<init>", "([BLjava/lang/String;)V"): 65,
    ("<init>", "([BLjava/nio/charset/Charset;)V"): 65,
    ("<init>", "([BIILjava/lang/String;)V"): 66,
    ("<init>", "([BIILjava/nio/charset/Charset;)V"): 67,
    ("<init>", "(Ljava/lang/String;)V"): 68,
    ("<init>", "()V"): 69,
}

STRING_STATIC_NATIVE_IDS = {
    ("valueOf", "(C)Ljava/lang/String;"): 55,
}

SYSTEM_NATIVE_IDS = {
    ("arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V"): 70,
    ("identityHashCode", "(Ljava/lang/Object;)I"): 71,
}

OBJECT_NATIVE_IDS = {
    ("getClass", "()Ljava/lang/Class;"): 72,
}

CLASS_NATIVE_IDS = {
    ("getResourceAsStream", "(Ljava/lang/String;)Ljava/io/InputStream;"): 73,
    ("getName", "()Ljava/lang/String;"): 74,
    ("getSimpleName", "()Ljava/lang/String;"): 75,
    ("isInstance", "(Ljava/lang/Object;)Z"): 76,
    ("getClassLoader", "()Ljava/lang/ClassLoader;"): 77,
    ("getDeclaredField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;"): 78,
}

ENUM_NATIVE_IDS = {
    ("<init>", "(Ljava/lang/String;I)V"): 27,
    ("name", "()Ljava/lang/String;"): 28,
    ("ordinal", "()I"): 29,
    ("toString", "()Ljava/lang/String;"): 30,
    ("valueOf", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;"): 31,
}

ARRAY_NATIVE_IDS = {
    ("clone", "()Ljava/lang/Object;"): 32,
}
