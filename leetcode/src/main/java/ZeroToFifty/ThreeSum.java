package ZeroToFifty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ThreeSum {
    /**
     * Time complexity O(n^2)
     * Space complexity O(1)
     */
    public static List<List<Integer>> threeSum(int[] nums) {
        if (nums == null || nums.length < 3) return Collections.emptyList();

        List<List<Integer>> ret = new ArrayList<>();
        Arrays.sort(nums);

        for (int i = 0; i < nums.length - 2; i++) {
            if (i > 0 && nums[i] == nums[i - 1]) {
                continue;
            }

            int remain = -nums[i];

            int j = i + 1;
            int k = nums.length - 1;

            if (nums[j] > remain) {
                continue;
            }

            while (j < k) {
                int sum = nums[j] + nums[k];
                if (sum == remain) {
                    ret.add(Arrays.asList(nums[i], nums[j], nums[k]));
                    ++j;
                    --k;

                    while (j < k && nums[j] == nums[j - 1]) ++j;
                    while (j < k && nums[k] == nums[k + 1]) --k;
                } else if (sum > remain) {
                    --k;
                } else {
                    ++j;
                }
            }

        }
        return ret;
    }
}
