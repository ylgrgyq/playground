package TwentySixToFifty;

public class DivideTwoIntegers {
    public static int divide(int A, int B) {
        if (A == Integer.MIN_VALUE && B == -1) return Integer.MAX_VALUE;
        boolean sign = A<0 == B<0;

        A=Math.abs(A);
        B=Math.abs(B);

        int result = div(A,B);
        return sign ? result : -result;
    }

    private static int div(int A, int B) {
        int total = B, prev = 0, result = 0;

        while (A - total >= 0) {
            result = result == 0 ? 1 : result+result;
            prev = total;
            total += total;
        }

        return ( result == 0) ? result : result + div(A - prev, B);
    }
}
