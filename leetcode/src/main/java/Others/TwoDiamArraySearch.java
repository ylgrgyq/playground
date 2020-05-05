package Others;

public class TwoDiamArraySearch {
    public static boolean search(int[][] input, int target) {
        int left = 0;
        int right = input.length;
        int up = 0;
        int down = input.length;

        while (left < right && up < down) {
            if (target == input[up][right - 1]) {
                return true;
            } else if (target < input[up][right - 1]) {
                right--;
            } else {
                up++;
            }
        }

        return false;
    }

    public static void main(String[] args) {
        int[][] input = new int[][]{
                {1, 2, 3, 4},
                {5, 6, 7, 8},
                {6, 7, 8, 9},
                {13, 14, 15, 16}};
        System.out.println(search(input, 8));
        System.out.println(search(input, 20));
        System.out.println(search(input, -1));
        System.out.println(search(input, 6));

    }
}
