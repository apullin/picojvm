/**
 * ExceptionTest — comprehensive exception handling test.
 * Adapted from picoJVM test suite.
 *
 * Expected output (as bytes): 1, 2, 3, 10, 4
 */

class MyException extends RuntimeException {
}

public class T32_ExceptionTest {
    // Test 1: basic try-catch
    static void testBasicCatch() {
        try {
            throw new RuntimeException();
        } catch (RuntimeException e) {
            Native.putchar(1);
        }
    }

    // Test 2: catch by parent class
    static void testParentCatch() {
        try {
            throw new RuntimeException();
        } catch (Exception e) {
            Native.putchar(2);
        }
    }

    // Test 3: uncaught in inner, caught in outer
    static void thrower() {
        throw new RuntimeException();
    }

    static void testUnwind() {
        try {
            thrower();
        } catch (Exception e) {
            Native.putchar(3);
        }
    }

    // Test 4: finally (catch-all)
    static void testFinally() {
        int x = 0;
        try {
            x = 10;
        } finally {
            Native.putchar(x);
        }
    }

    // Test 5: catch wrong type falls through
    static void testMismatch() {
        try {
            try {
                throw new RuntimeException();
            } catch (MyException e) {
                Native.putchar(99);  // should NOT reach
            }
        } catch (Exception e) {
            Native.putchar(4);
        }
    }

    public static void main(String[] args) {
        testBasicCatch();    // 1
        testParentCatch();   // 2
        testUnwind();        // 3
        testFinally();       // 10
        testMismatch();      // 4
        Native.halt();
    }
}
