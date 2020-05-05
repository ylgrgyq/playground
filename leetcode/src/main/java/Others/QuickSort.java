package Others;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

public class QuickSort {

    public static void swap(int[] input, int left, int right) {
        if (left == right) {
            return;
        }
        int tmp = input[left];
        input[left] = input[right];
        input[right] = tmp;
    }

    public static void qsort0(int[] input, int left, int right) {
        if (left >= right - 1) {
            return;
        }

        int pivot = ThreadLocalRandom.current().nextInt(left, right);
        swap(input, left, pivot);
        int lo = left + 1;
        int high = right - 1;

        while (true) {
            while (input[lo] <= input[left] && lo < high) {
                lo++;
            }

            while (input[high] > input[left]) {
                high--;
            }

            if (lo >= high) {
                break;
            }

            swap(input, lo, high);
        }

        swap(input, left, high);

        qsort0(input, left, high);
        qsort0(input, high + 1, right);
    }

    public static void qsort(int[] input) {
        if (input == null || input.length == 0) {
            return;
        }

        qsort0(input, 0, input.length);
    }

    public static void main(String[] args) {
//        int[] input = new int[]{5, 9, -1, 3};
        int[] input = new int[]{2, 1, 1, 2};
        qsort(input);

        System.out.println(Arrays.toString(input));
    }
}
