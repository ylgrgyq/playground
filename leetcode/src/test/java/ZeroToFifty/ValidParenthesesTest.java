package ZeroToFifty;

import org.junit.Test;

import static org.junit.Assert.*;

public class ValidParenthesesTest {
    private static class TestCase {
        String input;
        boolean expect;

        TestCase(String input, boolean expect) {
            this.input = input;
            this.expect = expect;
        }
    }

    @Test
    public void isValid() {
        TestCase[] tests = new TestCase[]{
                new TestCase("()", true),
                new TestCase("()[]{}", true),
                new TestCase("(]", false),
                new TestCase("([)]", false),
                new TestCase("{[]}", true),
                new TestCase("", true),

        };

        for (TestCase test : tests) {
            String msg = String.format("test failed for input: %s", test.input);
            assertEquals(msg, test.expect, ValidParentheses.isValid(test.input));
        }
    }
}