package ZeroToTwentyFive;

import org.junit.Test;

import static org.junit.Assert.*;

public class PalindromeNumberTest {
    private static class TestCase {
        int input;
        boolean expect;

        TestCase(int input, boolean expect) {
            this.input = input;
            this.expect = expect;
        }
    }

    @Test
    public void isPalindrome() {
        TestCase[] tests = new TestCase[]{
                new TestCase(121,true),
                new TestCase(-121,false),
                new TestCase(10,false),
        };

        for (TestCase test : tests) {
            String msg = String.format("test failed for input: %s", test.input);
            assertEquals(msg, test.expect, PalindromeNumber.isPalindrome(test.input));
        }
    }
}