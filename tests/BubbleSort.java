// Bubble sort — exercises arrays, nested loops, and comparisons.

public class BubbleSort {
    static void sort(int[] arr) {
        int n = arr.length;
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - 1 - i; j++) {
                if (arr[j] > arr[j + 1]) {
                    int tmp = arr[j];
                    arr[j] = arr[j + 1];
                    arr[j + 1] = tmp;
                }
            }
        }
    }

    public static void main(String[] args) {
        int[] data = { 5, 3, 8, 1, 9, 2, 7, 4, 6, 0 };
        sort(data);
        for (int i = 0; i < data.length; i++) {
            Native.putchar(data[i]);
        }
        Native.halt();
    }
}
