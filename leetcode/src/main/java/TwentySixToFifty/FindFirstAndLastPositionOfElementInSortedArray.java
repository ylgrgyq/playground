package TwentySixToFifty;

public class FindFirstAndLastPositionOfElementInSortedArray {
    public static int searchStartInRange(int[] nums, int left, int right, int target) {
        if (left < right) {
            int mid = (left + right) / 2;

            if (nums[mid] == target) {
                int start = searchStartInRange(nums, left, mid, target);
                return start < 0 ? mid : start;
            } else {
                assert target > nums[mid];
                return searchStartInRange(nums, mid + 1, right, target);
            }
        }

        return -1;
    }

    public static int searchEndInRange(int[] nums, int left, int right, int target) {
        if (left < right) {
            int mid = (left + right) / 2;

            if (nums[mid] == target) {
                int end = searchEndInRange(nums, mid + 1, right, target);
                return end < 0 ? mid : end;
            } else {
                assert target < nums[mid];
                return searchEndInRange(nums, left, mid, target);
            }
        }
        return -1;
    }


    public static int[] searchInRange(int[] nums, int left, int right, int target) {
        if (left < right) {
            int mid = (left + right) / 2;

            if (nums[mid] == target) {
                int start = searchStartInRange(nums, left, mid, target);
                if (start < 0) {
                    start = mid;
                }
                int end = searchEndInRange(nums, mid + 1, right, target);
                if (end < 0) {
                    end = mid;
                }
                return new int[]{start, end};
            } else if (nums[mid] > target) {
                return searchInRange(nums, left, mid, target);
            } else {
                return searchInRange(nums, mid + 1, right, target);
            }
        }

        return new int[]{-1, -1};
    }

    public static int[] searchRange(int[] nums, int target) {
        return searchInRange(nums, 0, nums.length, target);
    }
}
