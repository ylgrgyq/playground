package TwentySixToFifty;

public class StrStr {
    private static boolean match(String str, int startPos, String pattern) {
        for (int i = 0; i < pattern.length(); i++) {
            int pos = startPos + i;
            if (str.charAt(pos) != pattern.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    public static int strStr(String haystack, String needle) {
        if (needle == null || needle.length() == 0) return 0;
        if (haystack == null || haystack.length() == 0) return -1;
        if (haystack.length() < needle.length()) return -1;

        for (int i = 0; i < haystack.length() - needle.length() + 1; i++) {
            if (match(haystack, i, needle)) {
                return i;
            }
        }

        return -1;
    }
}
