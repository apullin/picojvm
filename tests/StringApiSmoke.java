public class StringApiSmoke {
    public static void main(String[] args) {
        String s = "TeRminal";
        Native.putchar(s.isEmpty() ? '1' : '0');
        Native.putchar(",".isEmpty() ? '1' : '0');
        Native.putchar("".isEmpty() ? '1' : '0');
        Native.print(s.substring(2));
        Native.putchar(',');
        Native.print(s.substring(1, 4));
        Native.putchar(',');
        Native.putchar(s.indexOf('m'));
        Native.putchar(',');
        Native.putchar(s.indexOf('i', 5));
        Native.putchar(',');
        Native.putchar(s.indexOf("min"));
        Native.putchar(',');
        Native.putchar(s.lastIndexOf('i'));
        Native.putchar(',');
        Native.putchar(s.startsWith("TeR") ? '1' : '0');
        Native.putchar(s.endsWith("nal") ? '1' : '0');
        Native.putchar(s.equalsIgnoreCase("terminal") ? '1' : '0');
        Native.putchar(s.regionMatches(true, 0, "ter", 0, 3) ? '1' : '0');
        Native.putchar(s.contains("min") ? '1' : '0');
        Native.putchar(',');
        Native.print(s.replace('e', 'a'));
        Native.putchar(',');
        Native.print(s.toLowerCase());
        Native.putchar(',');
        char[] chars = s.toCharArray();
        Native.putchar(chars[0]);
        Native.putchar(chars.length);
        Native.putchar(',');
        Native.putchar("abc".compareTo("abd") < 0 ? '1' : '0');
        Native.halt();
    }
}
