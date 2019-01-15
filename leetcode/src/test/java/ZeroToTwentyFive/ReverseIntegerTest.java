package ZeroToTwentyFive;

import org.junit.Test;

import static org.junit.Assert.*;

public class ReverseIntegerTest {
    private static class TestCase {
        int input;
        int expect;

        TestCase(int input, int expect) {
            this.input = input;
            this.expect = expect;
        }
    }

    @Test
    public void reverse() {
        TestCase[] tests = new TestCase[]{
                new TestCase(0,0),
                new TestCase(123,321),
                new TestCase(-123,-321),
                new TestCase(120,21),
                new TestCase(Integer.MAX_VALUE,0),
                new TestCase(Integer.MIN_VALUE,0),
                new TestCase(1534236469,0),
        };

        for (TestCase test : tests) {
            String msg = String.format("test failed for input: %s", test.input);
            assertEquals(msg, test.expect, ReverseInteger.reverse(test.input));
        }
    }

}