public class N23_InvalidInstanceofScalar {
    public static void main(String[] args) {
        if (1 instanceof Object) {
            Native.putchar('Y');
        }
    }
}
