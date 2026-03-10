public class T13_BreakContinue {
    public static void main(String[] args) {
        // Break: sum until reaching 5
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            if (i == 5) break;
            sum = sum + i;
        }
        // sum = 0+1+2+3+4 = 10
        Native.putchar(sum);

        // Continue: sum only even numbers 0..9
        sum = 0;
        for (int i = 0; i < 10; i++) {
            if (i % 2 != 0) continue;
            sum = sum + i;
        }
        // sum = 0+2+4+6+8 = 20
        Native.putchar(sum);
        Native.putchar(10);
        Native.halt();
    }
}
