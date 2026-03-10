public class T08_IntArray {
    public static void main(String[] args) {
        int[] arr = new int[5];
        for (int i = 0; i < 5; i++) {
            arr[i] = (i + 1) * 10;
        }
        // Print arr[0]=10, arr[2]=30, arr[4]=50
        Native.putchar(arr[0]);
        Native.putchar(arr[2]);
        Native.putchar(arr[4]);
        // Print length
        Native.putchar(arr.length);
        Native.putchar(10);
        Native.halt();
    }
}
