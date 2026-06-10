// Literal instance-field initializers run at the top of every constructor,
// including auto-generated ones chained from subclasses.
class T77Base {
    int a = 3;
}
public class T77_InstanceFieldInit extends T77Base {
    int v = 7;
    boolean f = true;
    T77_InstanceFieldInit(int unused) {
        Native.putchar('0' + v);
    }
    public static void main(String[] args) {
        T77_InstanceFieldInit m = new T77_InstanceFieldInit(0);
        Native.putchar('0' + m.a);
        Native.putchar(m.f ? 'T' : 'F');
        Native.putchar(10);
        Native.halt();
    }
}
