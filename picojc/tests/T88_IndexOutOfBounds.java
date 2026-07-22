public class T88_IndexOutOfBounds {
    public static void main(String[] args) {
        try {
            throw new IndexOutOfBoundsException();
        } catch (IndexOutOfBoundsException expected) {
            Native.putchar('O');
            Native.putchar('K');
            Native.putchar('\n');
        }
    }
}
