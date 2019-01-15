package ZeroToTwentyFive;

import java.util.Arrays;

public class ThreeSumClosest {
    public static int threeSumClosest(int[] nums, int target) {
        if (nums == null || nums.length < 3) return -1;

        int closest = Integer.MAX_VALUE;
        int ret = Integer.MAX_VALUE;
        Arrays.sort(nums);

        for (int i = 0; i < nums.length - 2; i++) {
            int remain = target - nums[i];

            int j = i + 1;
            int k = nums.length - 1;

            while (j < k) {
                int sum = nums[k] + nums[j];
                int diff = Math.abs(remain - sum);
                if (diff < closest) {
                    closest = diff;
                    ret = nums[i] + sum;
                }

                if (sum > remain) {
                    --k;
                } else if (sum < remain) {
                    ++j;
                } else {
                    return target;
                }
            }
        }

        return ret;
    }
}
