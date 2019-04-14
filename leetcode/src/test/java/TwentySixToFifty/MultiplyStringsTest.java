package TwentySixToFifty;

import org.junit.Test;

import static org.junit.Assert.*;

public class MultiplyStringsTest {
    private static class TestCase {
        String num1;
        String num2;
        String expect;

        TestCase(String num1, String num2, String expect) {
            this.num1 = num1;
            this.num2 = num2;
            this.expect = expect;
        }
    }

    @Test
    public void multiply() {
        MultiplyStringsTest.TestCase[] tests = new MultiplyStringsTest.TestCase[]{
                new MultiplyStringsTest.TestCase("2", "3", "6"),
                new MultiplyStringsTest.TestCase("123", "321", "39483"),
                new MultiplyStringsTest.TestCase("00123", "00321", "39483"),
                new MultiplyStringsTest.TestCase("00123", "0", "0"),
                new MultiplyStringsTest.TestCase("123", "456", "56088"),
        };

        for (MultiplyStringsTest.TestCase test : tests) {
            String actual = MultiplyStrings.multiply(test.num1, test.num2);
            String msg = String.format("test failed for input: [%s, %s], expect: %s, actual: %s",
                    test.num1, test.num2, test.expect, actual);
            assertEquals(msg, test.expect, actual);
        }
    }
}