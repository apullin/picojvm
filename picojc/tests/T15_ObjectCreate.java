class Point {
    int x;
    int y;
}

public class T15_ObjectCreate {
    public static void main(String[] args) {
        Point p = new Point();
        p.x = 10;
        p.y = 20;
        Native.putchar(p.x);
        Native.putchar(p.y);
        Native.putchar(p.x + p.y);
        Native.putchar(10);
        Native.halt();
    }
}
