public class N63_SwitchLocalScope {
    public static void main(String[] args) {
        switch (1) {
            case 1:
                int inside = 1;
                break;
            default:
                break;
        }
        Native.putchar(inside);
    }
}
