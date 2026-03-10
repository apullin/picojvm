class Shape {
    int kind() { return 0; }
}

class Circle extends Shape {
    int kind() { return 1; }
}

class Square extends Shape {
    int kind() { return 2; }
}

public class T19_InstanceOf {
    public static void main(String[] args) {
        Shape s = new Circle();
        if (s instanceof Circle) {
            Native.putchar(1);  // true
        } else {
            Native.putchar(0);
        }
        if (s instanceof Square) {
            Native.putchar(1);
        } else {
            Native.putchar(0);  // false
        }
        if (s instanceof Shape) {
            Native.putchar(1);  // true (upcast)
        } else {
            Native.putchar(0);
        }
        Native.putchar(10);
        Native.halt();
    }
}
