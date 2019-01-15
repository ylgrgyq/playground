package ZeroToTwentyFive;

public class LongestCommonPrefix {
    /**
     * Time complexity O(m) m is the length of the longest common prefix
     * Space complexity O(1)
     */
    public static String longestCommonPrefix(String[] strs) {
        if (strs == null || strs.length < 1) return "";
        int i = 0;
        outer:
        while (true) {
            Character c = null;
            for (String s : strs) {
                if (i >= s.length()) {
                    break outer;
                }

                if (c == null) {
                    c = s.charAt(i);
                } else if (c != s.charAt(i)) {
                    break outer;
                }
            }

            ++i;
        }

        return strs[0].substring(0, i);
    }

    private static int minLength(String[] strs) {
        int minLength = strs[0].length();
        for (String s : strs){
            if (s.length() < minLength) {
                minLength = s.length();
            }
        }
        return minLength;
    }

    public static String longestCommonPrefix2(String[] strs) {
        if (strs == null || strs.length < 1) return "";

        String firstStr = strs[0];
        int start = 0;
        int end = minLength(strs);

        while (start < end) {
            int mid = (start + end) / 2 + 1;
            String sub = firstStr.substring(start, mid);
            boolean changed = false;
            for (int j = 1; j < strs.length; j++) {
                String s = strs[j];

                if (!sub.equals(s.substring(start, mid))) {
                    end = mid - 1;
                    changed = true;
                    break;
                }
            }
            if (!changed) {
                start = mid;
            }
        }

        if (start > 0) {
            return strs[0].substring(0, start);
        } else {
            return "";
        }
    }
}
