package TwentySixToFifty;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class NextPermutationTest {
    private static class TestCase {
        int[] input;
        int[] expect;

        TestCase(int[] input, int[] expect) {
            this.input = input;
            this.expect = expect;
        }
    }

    @Test
    public void nextPermutation() {
        TestCase[] tests = new TestCase[]{
                new TestCase(new int[]{1, 2, 3}, new int[]{1, 3, 2}),
                new TestCase(new int[]{3, 2, 1}, new int[]{1, 2, 3}),
                new TestCase(new int[]{1, 1, 5}, new int[]{1, 5, 1}),
                new TestCase(new int[]{1, 9, 2, 3, 6, 5, 4, 1}, new int[]{1, 9, 2, 4, 1, 3, 5, 6}),
        };

        for (TestCase test : tests) {
            String msg = String.format("test failed for input: %s",
                    Arrays.toString(test.input));
            NextPermutation.nextPermutation(test.input);
            assertArrayEquals(msg, test.expect, test.input);
        }
    }
}