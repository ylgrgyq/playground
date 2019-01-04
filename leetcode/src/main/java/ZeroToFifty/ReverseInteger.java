package ZeroToFifty;

public class ReverseInteger {
    public static int reverse(int x) {
        boolean isNegative = x < 0;
        if (isNegative) {
            x = -x;
        }

        int ret = 0;
        while (x > 0) {
            int rem = x % 10;
            x = x / 10;

            if (ret > Integer.MAX_VALUE / 10 || (ret == Integer.MAX_VALUE / 10 && rem > 7)) return 0;

            ret = ret * 10 + rem;
        }

        if (isNegative) {
            ret = -ret;
        }
        return ret;
    }
}
