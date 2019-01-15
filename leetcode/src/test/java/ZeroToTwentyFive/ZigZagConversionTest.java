package ZeroToTwentyFive;

import org.junit.Test;

import static org.junit.Assert.*;

public class ZigZagConversionTest {
    private static class TestCase {
        String input;
        int inputRow;
        String expect;

        TestCase(String input, int row, String expect) {
            this.input = input;
            this.inputRow = row;
            this.expect = expect;
        }
    }

    @Test
    public void convert() {
        TestCase[] tests = new TestCase[]{
                new TestCase("PAYPALISHIRING",3, "PAHNAPLSIIGYIR"),
                new TestCase("ABC",5, "ABC"),
                new TestCase("ABC",3, "ABC"),
                new TestCase("PAYPALISHIRING",4, "PINALSIGYAHRPI"),
                new TestCase("PAYPALISHIRING",5, "PHASIYIRPLIGAN"),
                new TestCase("PAYPALISHIRING",6, "PRAIIYHNPSGAIL"),
                new TestCase("PAYPALISHIRING",0, "PAYPALISHIRING"),
                new TestCase("AB",1, "AB"),
                new TestCase("ABC",2, "ACB"),
                new TestCase("",4, ""),
        };

        for (TestCase test : tests) {
            assertEquals(test.expect, ZigZagConversion.convert(test.input, test.inputRow));
        }
    }

}