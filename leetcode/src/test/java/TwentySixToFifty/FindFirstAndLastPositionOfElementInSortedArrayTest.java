package TwentySixToFifty;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class FindFirstAndLastPositionOfElementInSortedArrayTest {
    private static class TestCase {
        int[] input;
        int target;
        int[] expect;

        TestCase(int[] input, int target, int[] expect) {
            this.input = input;
            this.target = target;
            this.expect = expect;
        }
    }

    @Test
    public void searchRange() {
        TestCase[] tests = new TestCase[]{
                new TestCase(new int[]{5, 7, 7, 8, 8, 10}, 8, new int[]{3, 4}),
                new TestCase(new int[]{5, 7, 7, 8, 8, 8, 10}, 8, new int[]{3, 5}),
                new TestCase(new int[]{5, 7, 7, 8, 8, 8, 10}, 100, new int[]{-1, -1}),
                new TestCase(new int[]{5, 7, 7, 8, 8, 8, 10}, 0, new int[]{-1, -1}),
                new TestCase(new int[]{8, 8, 8, 8, 8, 8}, 8, new int[]{0, 5}),
        };

        for (TestCase test : tests) {
            int[] actual = FindFirstAndLastPositionOfElementInSortedArray.searchRange(test.input, test.target);
            String msg = String.format("test failed for input: %s, target: %s, expect: %s, actual: %s",
                    Arrays.toString(test.input), test.target, Arrays.toString(test.expect), Arrays.toString(actual));
            assertArrayEquals(msg, test.expect, actual);
        }
    }
}