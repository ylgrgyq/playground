package TwentySixToFifty;

import org.junit.Test;

import static org.junit.Assert.*;

public class StrStrTest {
    private static class TestCase {
        String hayStack;
        String needle;
        int expect;

        TestCase(String input, String needle, int expect) {
            this.hayStack = input;
            this.needle = needle;
            this.expect = expect;
        }
    }

    @Test
    public void strStr() {
        TestCase[] tests = new TestCase[]{
                new TestCase("asdfasdf", "", 0),
                new TestCase("", "llasdfasdf", -1),
                new TestCase("hello", "llasdfasdf", -1),
                new TestCase("hello", "ll", 2),
                new TestCase("hell", "ll", 2),
                new TestCase("hel", "ll", -1),
                new TestCase("aaaaa", "bba", -1),
        };

        for (TestCase test : tests) {
            int actual = StrStr.strStr(test.hayStack, test.needle);
            String msg = String.format("test failed for hayStack: %s, needle: %s, expect: %s, actual: %s",
                    test.hayStack, test.needle, test.expect, actual);
            assertEquals(msg, test.expect, actual);
        }
    }
}