package ZeroToTwentyFive;

import java.util.*;

public class LetterCombinationsOfAPhoneNumber {
    private static Map<Character, List<Character>> digitsCharacterMap;

    static {
        digitsCharacterMap = new HashMap<>();
        digitsCharacterMap.put('2', Arrays.asList('a', 'b', 'c'));
        digitsCharacterMap.put('3', Arrays.asList('d', 'e', 'f'));
        digitsCharacterMap.put('4', Arrays.asList('g', 'h', 'i'));
        digitsCharacterMap.put('5', Arrays.asList('j', 'k', 'l'));
        digitsCharacterMap.put('6', Arrays.asList('m', 'n', 'o'));
        digitsCharacterMap.put('7', Arrays.asList('p', 'q', 'r', 's'));
        digitsCharacterMap.put('8', Arrays.asList('t', 'u', 'v'));
        digitsCharacterMap.put('9', Arrays.asList('w','x', 'y','z'));
    }

    public static List<String> letterCombinations(String digits) {
        if (digits == null || digits.isEmpty()) return Collections.emptyList();

        List<StringBuilder> queue  = new LinkedList<>();

        for(Character c : digitsCharacterMap.get(digits.charAt(0))) {
            StringBuilder b = new StringBuilder();
            b.append(c);
            queue.add(b);
        }

        for (int i = 1; i < digits.length(); i++) {
            char digit = digits.charAt(i);
            List<Character> newCharList = digitsCharacterMap.get(digit);


            List<StringBuilder> newQ = new LinkedList<>();
            for(StringBuilder b : queue) {
                for(Character c : newCharList) {
                    newQ.add(new StringBuilder(b).append(c));
                }
            }
            queue = newQ;
        }

        List<String> ret = new ArrayList<>(queue.size());
        for (StringBuilder b : queue) {
            ret.add(b.toString());
        }

        return ret;
    }
}
