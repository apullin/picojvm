class FieldBox {
    int[] ints;
    byte[] bytes;
    String[] words;
    FieldBox next;
}

public class T68_ArrayField {
    public static void main(String[] args) {
        FieldBox a = new FieldBox();
        FieldBox b = new FieldBox();

        a.ints = new int[3];
        a.bytes = new byte[2];
        a.words = new String[2];
        b.ints = new int[1];
        a.next = b;

        a.ints[0] = 7;
        a.ints[1] = 9;
        a.bytes[1] = 5;
        a.words[0] = "A";
        a.words[1] = "BC";
        b.ints[0] = 8;

        Native.putchar('0' + a.ints[0]);
        Native.putchar('0' + a.ints[1]);
        Native.putchar('0' + a.bytes[1]);
        Native.putchar('0' + a.words[0].length());
        Native.putchar('0' + a.words[1].length());
        Native.putchar('0' + a.next.ints[0]);
        Native.putchar('\n');
        Native.halt();
    }
}
