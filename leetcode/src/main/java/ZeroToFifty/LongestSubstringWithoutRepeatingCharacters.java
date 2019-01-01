package ZeroToFifty;

import java.util.HashMap;
import java.util.HashSet;

public class LongestSubstringWithoutRepeatingCharacters {
    private boolean noRepeating(String s, int start, int end) {
        HashSet<Character> set = new HashSet<>();
        for (int i = start; i < end; i++) {
            Character c = s.charAt(i);
            if (set.contains(c)) {
                return false;
            } else {
                set.add(c);
            }
        }

        return true;
    }

    /**
     * Brutal force
     * <p>
     * Time Complexity O(n^3)
     * <p>
     * Space Complexity O(min(m,n)) where n is the length of the input String, m is
     * the character set of the input String.
     */
    public int lengthOfLongestSubstring(String s) {
        int max = 0;
        for (int i = 0; i < s.length(); i++) {
            for (int j = i + 1; j <= s.length(); j++) {
                if (noRepeating(s, i, j)) {
                    int len = j - i;
                    if (len > max) {
                        max = len;
                    }
                }
            }
        }

        return max;
    }

    private int searchLongestSubFromPos(String s, int pos) {
        HashMap<Character, Boolean> found = new HashMap<>();

        for (int i = pos; i < s.length(); i++) {

        }

        return s.length() - pos;
    }

    /**
     * Time Complexity O(n)
     * Space Complexity O(min(m,n)) where n is the length of the input String, m is
     * the character set of the input String.
     */
    public int lengthOfLongestSubstring2(String s) {
        HashSet<Character> set = new HashSet<>();
        int strLength = s.length();
        int max = 0;
        int i = 0;
        int j = 0;
        while (i < strLength && j < strLength) {
            char c = s.charAt(j);
            if (set.contains(c)) {
                set.remove(s.charAt(i));
                ++i;
            } else {
                set.add(c);
                ++j;
                if (set.size() > max) {
                    max = set.size();
                }
            }
        }

        return max;
    }

    /**
     * Time Complexity O(n)
     * Space Complexity O(min(m,n)) where n is the length of the input String, m is
     * the character set of the input String.
     */
    public int lengthOfLongestSubstring3(String s) {
        HashMap<Character, Integer> map = new HashMap<>();
        int max = 0;
        for (int i =0, j = 0; i < s.length() && j < s.length(); ++j) {
            char c = s.charAt(j);
            Integer index = map.get(c);
            if (index != null) {
                // for s like this: "abcdefca", we need to prevent i goes backwards
                i = Math.max(index + 1, i);
            }

            int size = j - i + 1;
            max = Math.max(size, max);
            map.put(c, j);
        }

        return max;
    }

    public static void main(String[] args) {
        LongestSubstringWithoutRepeatingCharacters l = new LongestSubstringWithoutRepeatingCharacters();
        System.out.println(l.lengthOfLongestSubstring3("bbbbb"));
        System.out.println(l.lengthOfLongestSubstring3("abcabcbb"));
        System.out.println(l.lengthOfLongestSubstring3("pwwkew"));
        System.out.println(l.lengthOfLongestSubstring3("pwwkew !&"));
        System.out.println(l.lengthOfLongestSubstring3("abcdefghijklmnopqrstuvwxyz ABCDEFG !@#$%^&*() 1234567890"));
        System.out.println(l.lengthOfLongestSubstring3(""));
        System.out.println(l.lengthOfLongestSubstring3("abcdefca"));
    }
}
