package java.lang;

import pj.Native;

/*
 * PicoJSE StringBuilder: only the append surface used by Java 8 string
 * concatenation lowering. Strings are byte/ASCII backed in picoJVM, so this
 * builder stores bytes and truncates char values to the low 8 bits.
 */
public final class StringBuilder {
    private byte[] buf;
    private int len;

    public StringBuilder() {
        buf = new byte[16];
        len = 0;
    }

    public StringBuilder(int capacity) {
        if (capacity < 1) capacity = 1;
        buf = new byte[capacity];
        len = 0;
    }

    public StringBuilder(String s) {
        int capacity = 16;
        if (s != null) capacity = s.length() + 16;
        buf = new byte[capacity];
        len = 0;
        append(s);
    }

    private void ensure(int extra) {
        int need = len + extra;
        if (need <= buf.length) return;
        int cap = buf.length;
        while (cap < need) cap = cap * 2;
        byte[] next = new byte[cap];
        Native.arraycopy(buf, 0, next, 0, len);
        buf = next;
    }

    public StringBuilder append(String s) {
        if (s == null) s = "null";
        int n = s.length();
        ensure(n);
        for (int i = 0; i < n; i++) {
            buf[len] = (byte)s.charAt(i);
            len++;
        }
        return this;
    }

    public StringBuilder append(char c) {
        ensure(1);
        buf[len] = (byte)c;
        len++;
        return this;
    }

    public StringBuilder append(boolean v) {
        return append(v ? "true" : "false");
    }

    private void appendPositive(int v) {
        if (v >= 10) appendPositive(v / 10);
        append((char)('0' + (v % 10)));
    }

    public StringBuilder append(int v) {
        if (v == (1 << 31)) return append("-2147483648");
        if (v < 0) {
            append('-');
            v = -v;
        }
        appendPositive(v);
        return this;
    }

    public StringBuilder append(Object obj) {
        if (obj == null) return append("null");
        return append("<object>");
    }

    public int length() {
        return len;
    }

    public char charAt(int index) {
        if (index < 0 || index >= len) throw new IndexOutOfBoundsException();
        return (char)(buf[index] & 0xFF);
    }

    public void setLength(int newLength) {
        if (newLength < 0) throw new IndexOutOfBoundsException();
        ensure(newLength - len);
        while (len < newLength) buf[len++] = 0;
        len = newLength;
    }

    public void setCharAt(int index, char c) {
        if (index < 0 || index >= len) throw new IndexOutOfBoundsException();
        buf[index] = (byte)c;
    }

    public StringBuilder ensureCapacity(int minCapacity) {
        if (minCapacity > buf.length) ensure(minCapacity - len);
        return this;
    }

    public String substring(int start) {
        return substring(start, len);
    }

    public String substring(int start, int end) {
        if (start < 0 || end < start || end > len)
            throw new IndexOutOfBoundsException();
        return Native.stringFromBytes(buf, start, end - start);
    }

    public int indexOf(String s) {
        return indexOf(s, 0);
    }

    public int indexOf(String s, int fromIndex) {
        int n = s.length();
        if (fromIndex < 0) fromIndex = 0;
        if (n == 0) return fromIndex < len ? fromIndex : len;
        int max = len - n;
        for (int i = fromIndex; i <= max; i++) {
            boolean match = true;
            for (int j = 0; j < n; j++) {
                if ((buf[i + j] & 0xFF) != s.charAt(j)) {
                    match = false;
                    break;
                }
            }
            if (match) return i;
        }
        return -1;
    }

    public int lastIndexOf(String s) {
        return lastIndexOf(s, len);
    }

    public int lastIndexOf(String s, int fromIndex) {
        int n = s.length();
        if (n == 0) return fromIndex < len ? fromIndex : len;
        int start = fromIndex;
        if (start > len - n) start = len - n;
        for (int i = start; i >= 0; i--) {
            boolean match = true;
            for (int j = 0; j < n; j++) {
                if ((buf[i + j] & 0xFF) != s.charAt(j)) {
                    match = false;
                    break;
                }
            }
            if (match) return i;
        }
        return -1;
    }

    public StringBuilder delete(int start, int end) {
        if (end > len) end = len;
        if (start < 0 || start > end) throw new IndexOutOfBoundsException();
        int n = end - start;
        if (n > 0) {
            for (int i = end; i < len; i++) buf[i - n] = buf[i];
            len -= n;
        }
        return this;
    }

    public StringBuilder insert(int offset, char c) {
        if (offset < 0 || offset > len) throw new IndexOutOfBoundsException();
        ensure(1);
        for (int i = len - 1; i >= offset; i--) buf[i + 1] = buf[i];
        buf[offset] = (byte)c;
        len++;
        return this;
    }

    public StringBuilder replace(int start, int end, String s) {
        if (end > len) end = len;
        if (start < 0 || start > end) throw new IndexOutOfBoundsException();
        int oldN = end - start;
        int newN = s.length();
        int delta = newN - oldN;
        if (delta > 0) ensure(delta);
        if (delta > 0) {
            for (int i = len - 1; i >= end; i--) buf[i + delta] = buf[i];
            len += delta;
        } else if (delta < 0) {
            for (int i = end; i < len; i++) buf[i + delta] = buf[i];
            len += delta;
        }
        for (int i = 0; i < newN; i++) buf[start + i] = (byte)s.charAt(i);
        return this;
    }

    public String toString() {
        return Native.stringFromBytes(buf, 0, len);
    }
}
