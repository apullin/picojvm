/*
 * The algorithmic String API running as packed bytecode via the
 * java/lang/String shim — no NATIVE_STR_* beyond the primitive set
 * (length/charAt/equals/hashCode/construction) is used, so this runs
 * identically on builds with PJVM_USE_EXT_STRING_APIS compiled out,
 * including the 8085. Compare StringApiSmoke, which packs without the
 * shim and exercises the native tier.
 */
public class StringShimTest {
    public static void main(String[] args) {
        String s = " Hello picoJVM ";
        String t = s.trim();                              // "Hello picoJVM"
        Native.print(t);
        Native.putchar(10);
        Native.print(t.substring(6));                     // "picoJVM"
        Native.putchar(10);
        Native.print(t.substring(0, 5).toUpperCase());    // "HELLO"
        Native.print(t.replace('l', 'L').substring(0, 5)); // "HeLLo"
        Native.putchar(10);
        Native.putchar(t.indexOf('p') == 6 ? 'Y' : 'N');
        Native.putchar(t.indexOf("co") == 8 ? 'Y' : 'N');
        Native.putchar(t.indexOf("co", 10) == -1 ? 'Y' : 'N');
        Native.putchar(t.lastIndexOf('o') == 9 ? 'Y' : 'N');
        Native.putchar(t.contains("pico") ? 'Y' : 'N');
        Native.putchar(t.startsWith("Hel") ? 'Y' : 'N');
        Native.putchar(t.endsWith("JVM") ? 'Y' : 'N');
        Native.putchar(t.isEmpty() ? 'N' : 'Y');
        Native.putchar("".isEmpty() ? 'Y' : 'N');
        Native.putchar(t.equalsIgnoreCase("hello PICOjvm") ? 'Y' : 'N');
        Native.putchar(t.compareTo("Hello") > 0 ? 'Y' : 'N');
        Native.putchar(t.compareToIgnoreCase("HELLO PICOJVM") == 0 ? 'Y' : 'N');
        Native.putchar(t.regionMatches(true, 6, "PICO", 0, 4) ? 'Y' : 'N');
        Native.putchar(10);
        char[] ca = t.toCharArray();
        Native.putchar(ca[6]);                            // 'p'
        byte[] ba = t.getBytes();
        Native.putchar(ba[5]);                            // ' '
        Native.print(String.valueOf('Q'));                // 'Q'
        Native.putchar(10);
        Native.halt();
    }
}
