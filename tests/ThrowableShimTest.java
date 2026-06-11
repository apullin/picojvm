/*
 * Exception messages via the packed Throwable/Exception/RuntimeException
 * shims: the constructor chain stores the message as ordinary bytecode and
 * getMessage()/toString() return it. Without the shims (ExceptionTest's
 * configuration) the synthesized no-op constructors discard messages.
 */
class CustomFault extends Exception {
    CustomFault(String message) {
        super(message);
    }
}

public class ThrowableShimTest {
    static void boom() {
        throw new RuntimeException("boom");
    }

    public static void main(String[] args) {
        try {
            boom();
        } catch (RuntimeException e) {
            Native.print(e.getMessage());
        }
        Native.putchar(10);
        try {
            throw new CustomFault("custom");
        } catch (Exception e) {
            Native.print(e.getMessage());
            Native.print(e.toString());
        }
        Native.putchar(10);
        try {
            throw new RuntimeException();
        } catch (Exception e) {
            Native.putchar(e.getMessage() == null ? 'N' : 'X');
        }
        Native.putchar(10);
        Native.halt();
    }
}
