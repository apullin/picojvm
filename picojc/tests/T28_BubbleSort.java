public class T28_BubbleSort {
    public static void main(String[] args) {
        int[] arr = new int[9];
        arr[0] = 9; arr[1] = 3; arr[2] = 7; arr[3] = 1; arr[4] = 5;
        arr[5] = 8; arr[6] = 2; arr[7] = 6; arr[8] = 4;

        int n = arr.length;
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - i - 1; j++) {
                if (arr[j] > arr[j + 1]) {
                    int temp = arr[j];
                    arr[j] = arr[j + 1];
                    arr[j + 1] = temp;
                }
            }
        }

        for (int i = 0; i < n; i++) {
            Native.putchar(arr[i]);
        }
        Native.halt();
    }
}
