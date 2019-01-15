package ZeroToTwentyFive;

import java.util.Stack;

public class ValidParentheses {
    private static boolean isLeftParentheses(char c) {
        return c == '(' || c == '[' || c == '{';
    }

    private static boolean isMatch(char left, char right) {
        switch (left){
            case '(': return right == ')';
            case '[': return right == ']';

            case '{': return right == '}';
            default: return false;
        }
    }

    /**
     * Time complexity O(n)
     * Space complexity O(n)
     */
    public static boolean isValid(String s) {
        if (s == null || s.isEmpty()) return true;
        Stack<Character> stack = new Stack<>();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (isLeftParentheses(c)){
                stack.push(c);
            } else if (!stack.isEmpty() && isMatch(stack.peek(), c)) {
                stack.pop();
            } else {
                return false;
            }

        }

        return stack.isEmpty();
    }
}
