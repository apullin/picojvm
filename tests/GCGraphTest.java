class GCNode {
    GCNode next;
    int value;
    byte[] payload;
}

public class GCGraphTest {
    static int churn(int rounds) {
        GCNode head = new GCNode();
        GCNode tail = head;
        int sum = 0;

        head.value = 1;

        for (int i = 0; i < rounds; i++) {
            GCNode a = new GCNode();
            GCNode b = new GCNode();

            a.value = i;
            b.value = i ^ 0x55;
            a.payload = new byte[12];
            b.payload = new byte[8];
            a.payload[0] = (byte)i;
            b.payload[0] = (byte)(i ^ 0x33);

            a.next = b;
            b.next = a;

            if ((i & 7) == 0) {
                GCNode live = new GCNode();
                live.value = sum + i;
                live.payload = new byte[16];
                tail.next = live;
                tail = live;
                sum += live.payload.length;
            }

            sum += (a.payload[0] & 0xFF);
            sum ^= b.value;
            sum += head.value;
        }

        GCNode cur = head;
        while (cur != null) {
            sum += cur.value;
            cur = cur.next;
        }

        return sum;
    }

    public static void main(String[] args) {
        int v = churn(24);
        Native.putchar((v >>> 24) & 0xFF);
        Native.putchar((v >>> 16) & 0xFF);
        Native.putchar((v >>> 8) & 0xFF);
        Native.putchar(v & 0xFF);
        Native.halt();
    }
}
