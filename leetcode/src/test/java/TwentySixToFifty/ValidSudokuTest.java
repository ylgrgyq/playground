package TwentySixToFifty;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class ValidSudokuTest {
    private static class TestCase {
        String[][] input;
        boolean expect;

        TestCase(String[][] input, boolean expect) {
            this.input = input;
            this.expect = expect;
        }
    }

    @Test
    public void isValidSudoku() {
        TestCase[] tests = new TestCase[]{
                new TestCase(new String[][]{
                        new String[]{"5", "3", ".", ".", "7", ".", ".", ".", "."},
                        new String[]{"6", ".", ".", "1", "9", "5", ".", ".", "."},
                        new String[]{".","9","8",".",".",".",".","6","."},

                },
                        true),
        };

        for (TestCase test : tests) {
//            int actual = SearchInRotatedSortedArray.search(test.input, test.target);
//            String msg = String.format("test failed for input: %s, target: %s, expect: %s, actual: %s",
//                    Arrays.toString(test.input), test.target, test.expect, actual);
//            assertEquals(msg, test.expect, actual);
        }
    }
}