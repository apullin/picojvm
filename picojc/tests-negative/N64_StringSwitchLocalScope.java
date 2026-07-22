public class N64_StringSwitchLocalScope {
    public static void main(String[] args) {
        switch ("a") {
            case "a":
                int inside = 1;
                break;
            default:
                break;
        }
        Native.putchar(inside);
    }
}
