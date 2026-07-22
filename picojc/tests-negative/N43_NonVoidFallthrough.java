public class N43_NonVoidFallthrough {
    static int value(boolean ready) {
        if (ready) {
            return 1;
        }
    }

    public static void main(String[] args) {
        value(true);
    }
}
