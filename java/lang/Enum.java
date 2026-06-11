package java.lang;

/*
 * Minimal Enum supertype for picoJVM programs, packed as ordinary bytecode.
 *
 * When this class is included in a pjvmpack invocation, javac enum classes
 * link against it and the VM's NATIVE_ENUM_* handlers are never emitted
 * (pjvmpack only gap-fills natives for Enum methods the packed class lacks).
 * The field layout — name ref in slot 0, ordinal in slot 1 — matches both
 * the packer's synthetic Enum class and the native handlers, so images can
 * mix tiers freely.
 */
public abstract class Enum<E extends Enum<E>> {
    private final String name;
    private final int ordinal;

    protected Enum(String name, int ordinal) {
        this.name = name;
        this.ordinal = ordinal;
    }

    public final String name() {
        return name;
    }

    public final int ordinal() {
        return ordinal;
    }

    public String toString() {
        return name;
    }

    /* javac's enum lowering generates per-enum valueOf(String) methods that
     * reference this signature, so it must exist to compile any enum against
     * this shim. There is no reflection on picoJVM; per-enum valueOf is only
     * usable if the program never calls it (it returns null here). */
    public static <T extends Enum<T>> T valueOf(Class<T> enumType, String name) {
        return null;
    }
}
