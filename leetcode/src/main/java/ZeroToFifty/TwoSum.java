package ZeroToFifty;

import java.util.Arrays;
import java.util.HashMap;

public class TwoSum {
    /**
     * Time complexity O(n^2)
     * Space complexity O(1)
     */
    public int[] twoSum(int[] nums, int target) {
        for (int i = 0; i < nums.length; i++) {
            for (int j = i + 1; j < nums.length; j++) {
                if (nums[i] + nums[j] == target){
                    return new int[]{i, j};
                }
            }
        }

        return new int[]{};
    }

    /**
     * Two-pass Hash Table
     *
     * Time complexity O(n)
     * Space complexity O(n)
     */
    public int[] twoSum2(int[] nums, int target) {
        HashMap<Integer, Integer> numberIndex = new HashMap<>();
        for (int i = 0; i < nums.length; i++) {
            numberIndex.put(nums[i], i);
        }

        for (int i = 0; i < nums.length - 1; i++) {
            int remain = target - nums[i];
            Integer findIndex = numberIndex.get(remain);
            if (findIndex != null && i != findIndex) {
                return new int[]{i , findIndex};
            }
        }

        return new int[]{};
    }

    /**
     * One-pass Hash Table
     *
     * Time complexity O(n)
     * Space complexity O(n)
     */
    public int[] twoSum3(int[] nums, int target) {
        HashMap<Integer, Integer> numberIndex = new HashMap<>();
        for (int i = 0; i < nums.length; i++) {
            int remain = target - nums[i];
            Integer findIndex = numberIndex.get(remain);
            if (findIndex != null) {
                return new int[]{i , findIndex};
            } else {
                numberIndex.put(nums[i], i);
            }
        }

        return new int[]{};
    }

    public static void main(String[] args) {
        TwoSum twoSum = new TwoSum();
        System.out.println(Arrays.asList(twoSum.twoSum(new int[]{2, 7, 11, 15}, 9)));

    }
}
