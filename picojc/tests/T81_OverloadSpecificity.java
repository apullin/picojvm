class T81Base {
}

class T81Child extends T81Base {
}

public class T81_OverloadSpecificity {
    static int select(Object value) {
        return 1;
    }

    static int select(T81Base value) {
        return 2;
    }

    public static void main(String[] args) {
        Native.putchar(48 + select(new T81Child()));
        Native.putchar(10);
    }
}
