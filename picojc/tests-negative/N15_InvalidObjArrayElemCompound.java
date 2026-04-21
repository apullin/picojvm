public class N15_InvalidObjArrayElemCompound {
    public static void main(String[] args) {
        String[] a = new String[1];
        a[0] = "x";
        a[0] += 1;
    }
}
