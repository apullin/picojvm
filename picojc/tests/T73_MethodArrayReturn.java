class T73_Item {
    int val;
    T73_Item(int v) {
        val = v;
    }
}

public class T73_MethodArrayReturn {
    static byte[] bytes() {
        byte[] buf = new byte[2];
        buf[0] = 65;
        buf[1] = 66;
        return buf;
    }

    static T73_Item[] items() {
        T73_Item[] vals = new T73_Item[1];
        vals[0] = new T73_Item(67);
        return vals;
    }

    public static void main(String[] args) {
        byte[] buf = bytes();
        T73_Item[] vals = items();
        Native.putchar(buf[0]);
        Native.putchar(buf[1]);
        Native.putchar(vals[0].val);
        Native.putchar(10);
    }
}
