public class N07_InvalidFieldAssignNarrowing {
    byte b;

    public static void main(String[] args) {
        N07_InvalidFieldAssignNarrowing x = new N07_InvalidFieldAssignNarrowing();
        x.b = 300;
        Native.putchar(x.b);
    }
}
