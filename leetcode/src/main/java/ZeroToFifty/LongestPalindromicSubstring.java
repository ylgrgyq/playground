package ZeroToFifty;

public class LongestPalindromicSubstring {

    private static boolean isPalindrom(String s, int start, int stop) {
        while (start < stop) {
            char a = s.charAt(start);
            char b = s.charAt(stop - 1);
            if (a != b) {
                return false;
            }

            ++start;
            --stop;
        }

        return true;
    }

    /**
     * Please note that a string with only one character is also a palindrome.
     * Such that "a" 's longest palindrome is "a"; "ac" 's longest palindrome is "a" too.
     * <p>
     * Time complexity O(n^3)
     * Space complexity O(1)
     */
    public static String longestPalindrome(String s) {
        String max = "";
        for (int i = 0; i < s.length(); i++) {
            for (int j = i + 1; j <= s.length(); j++) {
                if (isPalindrom(s, i, j)) {
                    int len = j - i;
                    if (len > max.length()) {
                        max = s.substring(i, j);
                    }
                }
            }
        }

        return max;
    }

    private static int searchFromCenter(String s, int center1, int center2) {
        int left = center1;
        int right = center2;
        while (left >= 0 && right < s.length() && s.charAt(left) == s.charAt(right)) {
            --left;
            ++right;
        }

        return right - left - 1;
    }

    /**
     * Time complexity O(n^2)
     * Space complexity O(1)
     * <p>
     * For a string with n characters, we have 2n - 1 candidates as the center for a palindrome.
     * Such as for string "abcb", 'a', 'b', 'c', 'b' maybe a center for a palindrome with
     * odd number of characters. and 'ab', 'bc', 'cb' maybe a center for a palindrome with even
     * number of characters. We need to search all of them so it's 2n - 1 times we need to search.
     */
    public static String longestPalindrome2(String s) {
        if (s == null || s.isEmpty()) return "";
        int start = 0, end = 0;

        for (int i = 0; i < s.length(); i++) {
            int len1 = searchFromCenter(s, i, i);
            int len2 = searchFromCenter(s, i, i + 1);
            int len = Math.max(len1, len2);
            if (len > (end - start)) {
                start = i - (len - 1) / 2;
                end = start + len;
            }
        }

        return s.substring(start, end);
    }

    /**
     * Time complexity O(n^2)
     * Space complexity O(n^2)
     */
    public static String longestPalindrome3(String s) {
        if (s == null || s.isEmpty()) return "";
        boolean[][] palindromeTable = new boolean[s.length()][s.length()];
        String max = "";

        for (int step = 0; step < s.length(); step++) {
            for (int i = 0; i < s.length() - step; i++) {
                int j = i + step;

                // i is adjacent to j, such as "bb", i = 0, j = 1
                // or only single character between i and j, such as "bab", i = 0, j = 2
                if (j - i < 3) {
                    palindromeTable[i][j] = s.charAt(i) == s.charAt(j);
                } else {
                    // a palindrome is between i and j, such as "cbaabc", i = 0, j = 5
                    palindromeTable[i][j] = s.charAt(i) == s.charAt(j) && palindromeTable[i + 1][j - 1];
                }

                if (palindromeTable[i][j]) {
                    if (j - i + 1 > max.length()) {
                        max = s.substring(i, j + 1);
                    }
                }

            }
        }

        return max;
    }

}
