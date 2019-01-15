package TwentySixToFifty;

public class SearchInRotatedSortedArray {
    private static int binarySearch(int[] nums, int left, int right, int target) {
        while (left < right) {
            int mid = (left + right) / 2;
            if (nums[mid] == target) {
                return mid;
            } else if (nums[mid] > target) {
                right = mid;
            } else {
                left = mid + 1;
            }
        }

        return -1;
    }

    private static int searchInRange(int[] nums, int left, int right, int target) {
        if (left < right) {
            int mid = (left + right) / 2;

            if (nums[mid] == target) {
                return mid;
            } else if (nums[mid] > nums[left]) {
                if (target >= nums[left] && target < nums[mid]) {
                    return binarySearch(nums, left, mid, target);
                } else {
                    return searchInRange(nums, mid +1, right, target);
                }
            } else {
                if (target > nums[mid] && target <= nums[right - 1]) {
                    return binarySearch(nums, mid + 1, right, target);
                } else {
                    return searchInRange(nums, left, mid, target);
                }
            }
        }

        return -1;
    }

    /**
     * Time complexity O(log(N))
     */
    public static int search(int[] nums, int target) {
        if (nums == null || nums.length == 0) return -1;

        return searchInRange(nums, 0, nums.length, target);
    }
}
