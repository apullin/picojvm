class T85Root {
}

class T85Child extends T85Root {
}

class T85Pick {
    int value;

    T85Pick(Object input) {
        value = 1;
    }

    T85Pick(T85Root input) {
        value = 2;
    }
}

public class T85_ConstructorSpecificity {
    public static void main(String[] args) {
        T85Pick pick = new T85Pick(new T85Child());
        Native.putchar(48 + pick.value);
        Native.putchar(10);
    }
}
