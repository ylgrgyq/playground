package ZeroToTwentyFive;

public class PalindromeNumber {
    public static int reverse(int x) {
        int ret = 0;
        while (x != 0) {
            int rem = x % 10;
            x = x / 10;

            if (ret > Integer.MAX_VALUE / 10 || (ret == Integer.MAX_VALUE / 10 && rem > 7)) return 0;

            ret = ret * 10 + rem;
        }

        return ret;
    }

    public static boolean isPalindrome(int x) {
        if (x < 0) return false;

        return x == reverse(x);
    }
}
