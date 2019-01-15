package TwentySixToFifty;

public class NextPermutation {
    private static void swap(int[] nums, int left, int right) {
        int temp = nums[left];
        nums[left] = nums[right];
        nums[right] = temp;
    }

    private static void reverse(int[] nums, int from, int to) {
        while (from < to) {
            swap(nums, from, to);
            ++from;
            --to;
        }
    }

    public static void nextPermutation(int[] nums) {
        int j = -1;
        for (int i = nums.length - 2; i >= 0; --i) {
            if (nums[i] < nums[i + 1]) {
                j = i;
                break;
            }
        }

        if (j != -1) {
            for (int i = nums.length - 1; i > j; --i) {
                if (nums[i] > nums[j]) {
                    swap(nums, i, j);
                    break;
                }
            }
        }

        reverse(nums, j + 1, nums.length - 1);
    }



}
