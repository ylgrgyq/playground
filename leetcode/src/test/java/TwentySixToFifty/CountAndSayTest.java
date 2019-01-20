package TwentySixToFifty;

import org.junit.Test;

import static org.junit.Assert.*;

public class CountAndSayTest {
    private static class TestCase {
        int input;
        String expect;

        TestCase(int input, String expect) {
            this.input = input;
            this.expect = expect;
        }
    }

    @Test
    public void countAndSay() {
        TestCase[] tests = new TestCase[]{
                new TestCase(1, "1"),
                new TestCase(2, "11"),
                new TestCase(3, "21"),
                new TestCase(4, "1211"),
                new TestCase(5, "111221"),
        };

        for (TestCase test : tests) {
            String actual = CountAndSay.countAndSay(test.input);
            String msg = String.format("test failed for input: %s, expect: %s, actual: %s",
                    test.input, test.expect, actual);
            assertEquals(msg, test.expect, actual);
        }
    }
}