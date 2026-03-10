public class T12_DoWhile {
    public static void main(String[] args) {
        int n = 5;
        int sum = 0;
        do {
            sum = sum + n;
            n--;
        } while (n > 0);
        // sum = 5+4+3+2+1 = 15
        Native.putchar(sum);
        Native.putchar(10);
        Native.halt();
    }
}
