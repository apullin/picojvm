/**
 * InterfaceTest.java — Test invokeinterface dispatch.
 *
 * Expected output (as bytes):
 *   27  — Circle.area()  = 3*3*3 = 27
 *   20  — Box.area()     = 4*5 = 20
 *   67  — Circle.describe() = 'C'
 *   66  — Box.describe()    = 'B'
 *   27  — via Measurable.measure() on Circle
 *   20  — via Measurable.measure() on Box
 */

interface HasArea {
    int area();
}

interface Describable {
    int describe();
}

interface Measurable {
    int measure();
}

class Circle implements HasArea, Describable, Measurable {
    int radius;

    public int area() { return radius * radius * 3; }
    public int describe() { return 67; }  // 'C'
    public int measure() { return area(); }
}

class Box implements HasArea, Describable, Measurable {
    int width;
    int height;

    public int area() { return width * height; }
    public int describe() { return 66; }  // 'B'
    public int measure() { return area(); }
}

public class InterfaceTest {
    static void printArea(HasArea h) {
        Native.putchar(h.area());
    }

    static void printDesc(Describable d) {
        Native.putchar(d.describe());
    }

    static void printMeasure(Measurable m) {
        Native.putchar(m.measure());
    }

    public static void main(String[] args) {
        Circle c = new Circle();
        c.radius = 3;

        Box b = new Box();
        b.width = 4;
        b.height = 5;

        // Dispatch through HasArea interface
        printArea(c);     // 27
        printArea(b);     // 20

        // Dispatch through Describable interface
        printDesc(c);     // 67 = 'C'
        printDesc(b);     // 66 = 'B'

        // Dispatch through Measurable interface
        printMeasure(c);  // 27
        printMeasure(b);  // 20

        Native.halt();
    }
}
