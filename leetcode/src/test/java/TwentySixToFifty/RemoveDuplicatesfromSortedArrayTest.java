package TwentySixToFifty;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class RemoveDuplicatesfromSortedArrayTest {
    private static class TestCase {
        int[] input;
        int expect;
        int[] expectArray;

        TestCase(int[] input, int expect, int[] expectArray) {
            this.input = input;
            this.expect = expect;
            this.expectArray = expectArray;
        }
    }

    @Test
    public void removeDuplicates() {
        TestCase[] tests = new TestCase[]{
                new TestCase(new int[]{1}, 1, new int[]{1}),
                new TestCase(new int[]{1, 1, 2}, 2, new int[]{1, 2}),
                new TestCase(new int[]{1, 2, 3, 4, 5}, 5, new int[]{1, 2, 3, 4, 5}),
                new TestCase(new int[]{0, 0, 1, 1, 1, 2, 2, 3, 3, 4}, 5, new int[]{0, 1, 2, 3, 4}),
        };

        for (TestCase test : tests) {
            String inputInString = Arrays.toString(test.input);
            int actual = RemoveDuplicatesfromSortedArray.removeDuplicates(test.input);
            String msg = String.format("test failed for hayStack: %s, expect: %s, actual: %s", inputInString, test.expect, actual);
            assertEquals(msg, test.expect, actual);
            int[] actualArray = new int[actual];
            System.arraycopy(test.input, 0, actualArray, 0, actual);
            assertArrayEquals(test.expectArray, actualArray);
        }
    }
}