class T71Box {
    int f;
}

public class T71_ObjectFieldCompound {
    public static void main(String[] args) {
        T71Box b = new T71Box();
        b.f = 1;
        b.f += 2;
        Native.putchar('0' + b.f);
        Native.putchar('\n');
    }
}
