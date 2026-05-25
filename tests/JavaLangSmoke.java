import pj.Native;

public class JavaLangSmoke {
    public static void main(String[] args) {
        Native.print(Integer.toString(Math.min(42, -7)));
        Native.putchar(',');
        Native.print(Integer.toString(Math.max(42, -7)));
        Native.putchar(',');
        Native.print(Integer.toString(Math.abs(-9)));
        Native.putchar(',');
        Native.print(Integer.toHexString(0x1a2b));
        Native.putchar('\n');

        StringBuilder sb = new StringBuilder("abc");
        sb.append(Integer.toString(Integer.valueOf(12).intValue()));
        sb.append(Integer.toString(Byte.valueOf((byte)3).intValue()));
        sb.append(Integer.toString(Short.valueOf((short)4).intValue()));
        sb.append(Character.valueOf('Z').charValue());
        sb.append(Boolean.valueOf(true).toString());
        Native.print(sb.toString());
        Native.putchar(',');
        Native.putchar(sb.length());
        sb.setCharAt(0, 'A');
        sb.setLength(5);
        Native.putchar(',');
        Native.print(sb.toString());
        Native.putchar(',');
        Native.putchar(sb.lastIndexOf("c", sb.length()));
        Native.putchar(',');
        Native.print(sb.substring(1, 4));
        Native.putchar('\n');

        Native.putchar(Character.isWhitespace('\t') ? '1' : '0');
        Native.putchar(Character.isDigit('7') ? '1' : '0');
        Native.putchar(Character.toLowerCase('Q'));
        Native.putchar('\n');

        StringBuilder extra = new StringBuilder("hello");
        Native.putchar(extra.length());
        extra.ensureCapacity(20);
        extra.setLength(3);
        Native.print(extra.toString());
        extra.setCharAt(0, 'H');
        Native.print(extra.toString());
        extra.insert(1, '!');
        Native.print(extra.toString());
        extra.delete(0, 2);
        Native.print(extra.toString());
        extra.replace(0, 1, "ang");
        Native.print(extra.toString());
        Native.putchar(extra.indexOf("ng"));
        Native.putchar(extra.indexOf("zz"));
        Native.putchar(extra.lastIndexOf("l"));
        Native.putchar('\n');

        Native.putchar(Character.isLetter('a') ? '1' : '0');
        Native.putchar(Character.isLetter('5') ? '1' : '0');
        Native.putchar(Character.isLetterOrDigit('5') ? '1' : '0');
        Native.putchar(Character.isUpperCase('A') ? '1' : '0');
        Native.putchar(Character.isUpperCase('a') ? '1' : '0');
        Native.putchar(Character.digit('a', 16) == 10 ? '1' : '0');
        Native.putchar(Character.digit('9', 10) == 9 ? '1' : '0');
        Native.putchar(Character.digit('5', 4) == -1 ? '1' : '0');
        Native.putchar('\n');
        Native.halt();
    }
}
