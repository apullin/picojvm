package java.lang;

public final class Math {
    private Math() {
    }

    public static int abs(int v) {
        return v < 0 ? -v : v;
    }

    public static int min(int a, int b) {
        return a < b ? a : b;
    }

    public static int max(int a, int b) {
        return a > b ? a : b;
    }
}
