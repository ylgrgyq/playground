package ZeroToTwentyFive;

public class ZigZagConversion {

    /**
     * Time complexity O(n)
     * Space complexity O(n)
     */
    public static String convert(String s, int numRows) {
        if (numRows <= 0 || s == null || s.isEmpty() || numRows >= s.length() || numRows == 1) return s;

        StringBuilder[] group = new StringBuilder[numRows];
        for (int i = 0; i < group.length; i++) {
            group[i] = new StringBuilder();
        }

        boolean forwardDirection = false;
        for (int i = 0, j = 0; i < s.length(); i++) {
            Character c = s.charAt(i);
            group[j].append(c);

            if (j == numRows - 1 || j == 0) {
                forwardDirection = !forwardDirection;
            }

            if (forwardDirection) {
                ++j;
            } else {
                --j;
            }
        }

        StringBuilder builder = new StringBuilder();
        for (StringBuilder b : group) {
            builder.append(b);
        }

        return builder.toString();
    }
}
