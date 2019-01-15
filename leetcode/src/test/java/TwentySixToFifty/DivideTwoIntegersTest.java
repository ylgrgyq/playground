package TwentySixToFifty;

import org.junit.Test;

import static org.junit.Assert.*;

public class DivideTwoIntegersTest {
    private static class TestCase {
        int dividend;
        int divisor;
        int expect;

        TestCase(int dividend, int divisor, int expect) {
            this.dividend = dividend;
            this.divisor = divisor;
            this.expect = expect;
        }
    }

    @Test
    public void divide() {
        TestCase[] tests = new TestCase[]{
                new TestCase(10, 3, 3),
                new TestCase(7, -3, -2),
        };

        for (TestCase test : tests) {
            int actual = DivideTwoIntegers.divide(test.dividend, test.divisor);
            String msg = String.format("test failed for hayStack: %s, needle: %s, expect: %s, actual: %s",
                    test.dividend, test.divisor, test.expect, actual);
            assertEquals(msg, test.expect, actual);
        }
    }
}