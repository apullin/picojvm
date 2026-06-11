package java.lang;

/*
 * Minimal Throwable for picoJVM programs, packed as ordinary bytecode.
 *
 * Without this class packed, pjvmpack synthesizes empty exception classes
 * whose constructors are no-ops - `new RuntimeException("msg")` silently
 * discards the message. Packed, the constructor chain stores it and
 * getMessage()/toString() work as plain bytecode (exception objects are
 * ordinary objects, so normal virtual dispatch applies).
 *
 * No stack traces: picoJVM does not record frames at throw time, so
 * toString() is just the message (not the JDK's "ClassName: message").
 */
public class Throwable {
    private final String message;

    public Throwable() {
        message = null;
    }

    public Throwable(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public String toString() {
        return message == null ? "" : message;
    }
}
