package ZeroToFifty;

public class ContainerWithMostWater {
    /**
     * Time complexity O(n^2)
     * Space complexity O(1)
     *
     */
    public static int maxArea(int[] height) {
        int max = 0;
        for (int i = 0; i < height.length; i++) {
            for (int j = i + 1; j < height.length; j++) {
                int area = Math.min(height[i], height[j]) * (j - i);
                if (area > max) {
                    max = area;
                }
            }
        }
        return max;
    }

    public static int maxArea2(int[] height) {
        int max = 0;
        int i = 0;
        int j = height.length - 1;
        while (i < j) {
            int area = Math.min(height[i], height[j]) * (j - i);
            if (area > max) {
                max = area;
            }
            if (height[i] > height[j]) {
                --j;
            } else {
                ++i;
            }
        }

        return max;
    }
}
