public class Empty {

    public static int distinct(int[] input) {
        if (input == null || input.length == 0) {
            return 0;
        }

        int left = 0;
        int right = input.length - 1;
        int distinct = 0;

        while (left <= right) {
            while (left + 1 <= right && input[left] == input[left + 1]) {
                ++left;
            }

            while (right - 1 >= left && input[right - 1] == input[right]) {
                --right;
            }

            if (-input[left] < input[right]) {
                --right;
            } else if (-input[left] > input[right]) {
                ++left;
            } else {
                ++left;
                --right;
            }

            ++distinct;
        }


        return distinct;
    }

    public static void main(String[] args) {
        int[] input;
        input = null;
        System.out.println(distinct(input));

        input = new int[]{1, 1, 1, 1};
        System.out.println(distinct(input));

        input = new int[]{1};
        System.out.println(distinct(input));


        input = new int[]{-7, -6, -5, 0, 1, 2, 6, 7, 8, 9};
        System.out.println(distinct(input));

        input = new int[]{0, 1, 1, 2, 6, 7, 8, 9};
        System.out.println(distinct(input));

        input = new int[]{0, 1, 1, 1, 6, 7, 8, 9};
        System.out.println(distinct(input));
    }
}
