package ZeroToTwentyFive;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.*;

public class GenerateParenthesesTest {
    private static class TestCase {
        int input;
        String[] expect;

        TestCase(int input, String[] expect) {
            this.input = input;
            this.expect = expect;
        }
    }

    private static String[] listToArray(List<String> list){
        if (list == null || list.isEmpty()) return new String[]{};
        return list.toArray(new String[0]);
    }

    private static boolean setEqual(String[] e, String[] a) {
        HashSet<String> expect = new HashSet<>(Arrays.asList(e));
        HashSet<String> actual = new HashSet<>(Arrays.asList(a));
        if (expect.size() == actual.size()) {
            for (String s: expect) {
                if (!actual.contains(s)){
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    @Test
    public void generateParenthesis() {
        TestCase[] tests = new TestCase[]{
                new TestCase(1, new String[]{"()"}),
                new TestCase(2, new String[]{"()()", "(())"}),
                new TestCase(3, new String[]{"((()))", "(()())", "(())()", "()(())", "()()()"}),
                new TestCase(4, new String[]{"((()))()", "(()(()))", "((())())",
                        "((()()))", "(()())()", "()(()())", "(()()())",
                        "()(())()", "()()()()", "()((()))", "()()(())", "(((())))", "(())()()", "(())(())"}),
        };

        for (TestCase test : tests) {
            String msg = String.format("test failed for input: %s", test.input);
            assertTrue(msg, setEqual(test.expect,
                    GenerateParenthesesTest.listToArray(GenerateParentheses.generateParenthesis(test.input))));


            assertTrue(msg, setEqual(test.expect,
                    GenerateParenthesesTest.listToArray(GenerateParentheses.generateParenthesis2(test.input))));
        }
    }
}