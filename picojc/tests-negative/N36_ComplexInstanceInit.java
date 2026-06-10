public class N36_ComplexInstanceInit {
    int v = 5 + 3;
    public static void main(String[] args) {
        N36_ComplexInstanceInit m = new N36_ComplexInstanceInit();
        Native.putchar('0' + m.v);
        Native.halt();
    }
}
