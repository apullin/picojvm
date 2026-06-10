public class N33_CaseIdent {
    static final int K = 3;
    public static void main(String[] args) {
        switch (args.length) {
            case K: Native.putchar('Y'); break;
        }
        Native.halt();
    }
}
