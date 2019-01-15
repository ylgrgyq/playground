package ZeroToTwentyFive;

import org.junit.Test;

import static org.junit.Assert.*;

public class IntegerToRomanTest {

    private static class TestCase {
        int input;
        String expect;

        TestCase(int input, String expect) {
            this.input = input;
            this.expect = expect;
        }
    }

    @Test
    public void intToRoman() {
        TestCase[] tests = new TestCase[]{
                new TestCase(3, "III"),
                new TestCase(4, "IV"),
                new TestCase(9, "IX"),
                new TestCase(58, "LVIII"),
                new TestCase(1994, "MCMXCIV"),
        };

        for (TestCase test : tests) {
            String msg = String.format("test failed for input: %s", test.input);
            assertEquals(msg, test.expect, IntegerToRoman.intToRoman(test.input));
        }
    }
}