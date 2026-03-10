public class T05_WhileLoop {
    public static void main(String[] args) {
        int i = 0;
        while (i < 5) {
            Native.putchar(48 + i);  // '0' through '4'
            i++;
        }
        Native.putchar(10);
        Native.halt();
    }
}
