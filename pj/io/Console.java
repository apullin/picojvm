package pj.io;

/*
 * Convenience facade for the common host-style console case.
 *
 * Apps that care about exact routing should keep explicit TextWriter handles.
 * Apps that just want println-style output can use Console directly.
 */
public class Console {
    private static TextWriter out = TextWriter.stdout();
    private static TextWriter err = TextWriter.stdout();

    public static TextWriter out() {
        return out;
    }

    public static TextWriter err() {
        return err;
    }

    public static void bind(TextWriter writer) {
        if (writer != null) out = writer;
    }

    public static void bindStdout() {
        out = TextWriter.stdout();
    }

    public static void bindPort(int port) {
        out = TextWriter.port(port);
    }

    public static void bindErr(TextWriter writer) {
        if (writer != null) err = writer;
    }

    public static void bindErrStdout() {
        err = TextWriter.stdout();
    }

    public static void bindErrPort(int port) {
        err = TextWriter.port(port);
    }

    public static void print(String s) {
        out.print(s);
    }

    public static void printChar(int c) {
        out.printChar(c);
    }

    public static void println() {
        out.println();
    }

    public static void println(String s) {
        out.println(s);
    }

    public static void printInt(int v) {
        out.printInt(v);
    }

    public static void printlnInt(int v) {
        out.printlnInt(v);
    }

    public static void printHex(int v, int digits) {
        out.printHex(v, digits);
    }

    public static void eprint(String s) {
        err.print(s);
    }

    public static void eprintChar(int c) {
        err.printChar(c);
    }

    public static void eprintln() {
        err.println();
    }

    public static void eprintln(String s) {
        err.println(s);
    }

    public static void eprintInt(int v) {
        err.printInt(v);
    }
}
