package java.lang;

/*
 * Minimal String for picoJVM programs, packed as ordinary bytecode.
 *
 * This class is the boundary contract between the two string tiers:
 *
 *  - `native` methods are the primitive tier: they touch the VM's string
 *    layout (ROM vs heap refs, length/byte access, allocation) and are
 *    always provided by the interpreter, on every build, via pjvmpack's
 *    native gap-fill. Constructors stay native too - pjvmpack rewrites
 *    `new String(...)` allocation sequences into the native init family,
 *    and drops the placeholder bodies below at pack time.
 *
 *  - Methods with bodies are the algorithmic tier: plain Java loops over
 *    the primitives. When this class is packed with a program they run as
 *    bytecode and need no VM support at all - in particular they work on
 *    8085 builds, where PJVM_USE_EXT_STRING_APIS has the equivalent C
 *    compiled out. pjvmpack strips the methods a program doesn't use.
 *
 * To compile a program against this contract, pass the shim sources as
 * explicit compilation units alongside it (the build does this for every
 * test compile) - javac's sourcepath cannot shadow boot packages like
 * java.lang, so a bare javac run silently binds the platform String.
 * Compiled against the shim, missing methods fail at compile time instead
 * of trapping on the device. Signatures may be narrower than the JDK's
 * (e.g. contains(String) instead of contains(CharSequence)); pjvmpack
 * carries descriptor aliases so images compiled against the JDK still
 * resolve.
 *
 * Strings are immutable byte strings (8-bit chars). No instance fields:
 * a String reference is a tagged VM value, not an object with a header.
 */
public final class String {

    /* --- primitive tier (VM natives) ----------------------------------- */

    public native int length();
    public native char charAt(int index);
    public native boolean equals(Object other);
    public native int hashCode();
    public native String toString();

    /* Placeholder constructors: call sites are rewritten to native string
     * construction by pjvmpack; these bodies are dropped at pack time. */
    public String() {}
    public String(String s) {}
    public String(byte[] bytes) {}
    public String(byte[] bytes, int off, int len) {}
    public String(char[] chars) {}
    public String(char[] chars, int off, int len) {}

    /* --- algorithmic tier (packed bytecode) ---------------------------- */

    public boolean isEmpty() {
        return length() == 0;
    }

    public String substring(int begin) {
        return substring(begin, length());
    }

    public String substring(int begin, int end) {
        if (begin == 0 && end == length()) return this;
        int n = end - begin;
        byte[] b = new byte[n];
        int i = 0;
        while (i < n) {
            b[i] = (byte) charAt(begin + i);
            i++;
        }
        return new String(b, 0, n);
    }

    public int indexOf(int ch) {
        return indexOf(ch, 0);
    }

    public int indexOf(int ch, int from) {
        int n = length();
        int i = from < 0 ? 0 : from;
        while (i < n) {
            if (charAt(i) == ch) return i;
            i++;
        }
        return -1;
    }

    public int lastIndexOf(int ch) {
        return lastIndexOf(ch, length() - 1);
    }

    public int lastIndexOf(int ch, int from) {
        int n = length();
        int i = from >= n ? n - 1 : from;
        while (i >= 0) {
            if (charAt(i) == ch) return i;
            i--;
        }
        return -1;
    }

    public int indexOf(String needle) {
        return indexOf(needle, 0);
    }

    public int indexOf(String needle, int from) {
        int n = length();
        int m = needle.length();
        int i = from < 0 ? 0 : from;
        if (m == 0) return i <= n ? i : n;
        while (i + m <= n) {
            int j = 0;
            while (j < m && charAt(i + j) == needle.charAt(j)) j++;
            if (j == m) return i;
            i++;
        }
        return -1;
    }

    public boolean contains(String s) {
        return indexOf(s, 0) >= 0;
    }

    public boolean startsWith(String prefix) {
        int m = prefix.length();
        if (m > length()) return false;
        int i = 0;
        while (i < m) {
            if (charAt(i) != prefix.charAt(i)) return false;
            i++;
        }
        return true;
    }

    public boolean endsWith(String suffix) {
        int m = suffix.length();
        int off = length() - m;
        if (off < 0) return false;
        int i = 0;
        while (i < m) {
            if (charAt(off + i) != suffix.charAt(i)) return false;
            i++;
        }
        return true;
    }

    public int compareTo(String other) {
        int a = length();
        int b = other.length();
        int n = a < b ? a : b;
        int i = 0;
        while (i < n) {
            int d = charAt(i) - other.charAt(i);
            if (d != 0) return d;
            i++;
        }
        return a - b;
    }

    public int compareToIgnoreCase(String other) {
        int a = length();
        int b = other.length();
        int n = a < b ? a : b;
        int i = 0;
        while (i < n) {
            int d = lower(charAt(i)) - lower(other.charAt(i));
            if (d != 0) return d;
            i++;
        }
        return a - b;
    }

    public boolean equalsIgnoreCase(String other) {
        if (other == null) return false;
        int n = length();
        if (other.length() != n) return false;
        int i = 0;
        while (i < n) {
            if (lower(charAt(i)) != lower(other.charAt(i))) return false;
            i++;
        }
        return true;
    }

    public boolean regionMatches(boolean ignoreCase, int toff, String other,
                                 int ooff, int len) {
        if (toff < 0 || ooff < 0) return false;
        if (toff + len > length()) return false;
        if (ooff + len > other.length()) return false;
        int i = 0;
        while (i < len) {
            char a = charAt(toff + i);
            char b = other.charAt(ooff + i);
            if (a != b) {
                if (!ignoreCase) return false;
                if (lower(a) != lower(b)) return false;
            }
            i++;
        }
        return true;
    }

    public String replace(char oldCh, char newCh) {
        int n = length();
        byte[] b = new byte[n];
        int i = 0;
        while (i < n) {
            char c = charAt(i);
            b[i] = (byte) (c == oldCh ? newCh : c);
            i++;
        }
        return new String(b, 0, n);
    }

    public String toLowerCase() {
        int n = length();
        byte[] b = new byte[n];
        int i = 0;
        while (i < n) {
            b[i] = (byte) lower(charAt(i));
            i++;
        }
        return new String(b, 0, n);
    }

    public String toUpperCase() {
        int n = length();
        byte[] b = new byte[n];
        int i = 0;
        while (i < n) {
            b[i] = (byte) upper(charAt(i));
            i++;
        }
        return new String(b, 0, n);
    }

    public String trim() {
        int n = length();
        int s = 0;
        while (s < n && charAt(s) <= ' ') s++;
        int e = n;
        while (e > s && charAt(e - 1) <= ' ') e--;
        return substring(s, e);
    }

    public char[] toCharArray() {
        int n = length();
        char[] c = new char[n];
        int i = 0;
        while (i < n) {
            c[i] = charAt(i);
            i++;
        }
        return c;
    }

    public byte[] getBytes() {
        int n = length();
        byte[] b = new byte[n];
        int i = 0;
        while (i < n) {
            b[i] = (byte) charAt(i);
            i++;
        }
        return b;
    }

    public static String valueOf(char c) {
        byte[] b = new byte[1];
        b[0] = (byte) c;
        return new String(b, 0, 1);
    }

    private static char lower(char c) {
        return c >= 'A' && c <= 'Z' ? (char) (c + 32) : c;
    }

    private static char upper(char c) {
        return c >= 'a' && c <= 'z' ? (char) (c - 32) : c;
    }
}
