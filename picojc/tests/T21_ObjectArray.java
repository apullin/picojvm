class Item {
    int val;
    Item(int v) {
        val = v;
    }
}

public class T21_ObjectArray {
    public static void main(String[] args) {
        Item[] items = new Item[3];
        items[0] = new Item(10);
        items[1] = new Item(20);
        items[2] = new Item(30);
        int sum = 0;
        for (int i = 0; i < items.length; i++) {
            sum = sum + items[i].val;
        }
        // sum = 60
        Native.putchar(sum);
        Native.putchar(10);
        Native.halt();
    }
}
