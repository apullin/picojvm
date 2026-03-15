public class T61_MainArgs {
    public static void main(String[] args) {
        Native.putchar('0' + args.length);
        Native.putchar(':');
        if (args.length > 0) Native.print(args[0]);
        Native.putchar(',');
        if (args.length > 1) Native.print(args[1]);
        Native.putchar(',');
        if (args.length > 2) Native.print(args[2]);
        Native.putchar('\n');
        Native.halt();
    }
}
