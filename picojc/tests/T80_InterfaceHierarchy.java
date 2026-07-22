interface T80Root {
    int value();
}

interface T80Left extends T80Root {
}

interface T80Right extends T80Root {
}

class T80Base {
    public int value() {
        return 7;
    }
}

class T80Implementation extends T80Base implements T80Left, T80Right {
}

public class T80_InterfaceHierarchy {
    public static void main(String[] args) {
        T80Left left = new T80Implementation();
        T80Right right = new T80Implementation();
        T80Root root = new T80Implementation();
        Native.putchar(48 + left.value());
        Native.putchar(48 + right.value());
        Native.putchar(48 + root.value());
        Native.putchar(10);
    }
}
