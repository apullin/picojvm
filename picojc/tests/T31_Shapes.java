// Shapes — exercises inheritance, vtable dispatch, and polymorphism.
// Adapted from picoJVM test suite.

class Shape {
    int x;
    int y;

    int area() {
        return 0;
    }

    int describe() {
        Native.putchar(63);  // '?'
        return 0;
    }
}

class Square extends Shape {
    int side;

    int area() {
        return side * side;
    }

    int describe() {
        Native.putchar(83);  // 'S'
        return area();
    }
}

class Rect extends Shape {
    int w;
    int h;

    int area() {
        return w * h;
    }

    int describe() {
        Native.putchar(82);  // 'R'
        return area();
    }
}

public class T31_Shapes {
    public static void main(String[] args) {
        Shape[] shapes = new Shape[3];

        Shape s0 = new Shape();
        shapes[0] = s0;

        Square s1 = new Square();
        s1.side = 5;
        shapes[1] = s1;

        Rect s2 = new Rect();
        s2.w = 3;
        s2.h = 7;
        shapes[2] = s2;

        // Polymorphic dispatch — each calls the right describe/area
        for (int i = 0; i < 3; i++) {
            int a = shapes[i].describe();
            Native.putchar(a);
        }

        Native.halt();
    }
}
