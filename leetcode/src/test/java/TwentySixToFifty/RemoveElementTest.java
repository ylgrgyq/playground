package TwentySixToFifty;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class RemoveElementTest {
    private static class TestCase {
        int[] input;
        int val;
        int expect;
        int[] expectArray;

        TestCase(int[] input, int val, int expect, int[] expectArray) {
            this.input = input;
            this.val = val;
            this.expect = expect;
            this.expectArray = expectArray;
        }
    }

    @Test
    public void removeElement() {
        TestCase[] tests = new TestCase[]{
                new TestCase(new int[]{3}, 3, 0, new int[]{}),
                new TestCase(new int[]{3, 3, 3, 3, 3, 3}, 3, 0, new int[]{}),
                new TestCase(new int[]{3, 2, 2, 3}, 3, 2, new int[]{2, 2}),
                new TestCase(new int[]{0, 1, 2, 2, 3, 0, 4, 2}, 2, 5, new int[]{0, 1, 3, 0, 4}),
        };

        for (TestCase test : tests) {
            String inputInString = Arrays.toString(test.input);
            int actual = RemoveElement.removeElement(test.input, test.val);
            String msg = String.format("test failed for hayStack: %s, expect: %s, actual: %s", inputInString, test.expect, actual);
            assertEquals(msg, test.expect, actual);
            int[] actualArray = new int[actual];
            System.arraycopy(test.input, 0, actualArray, 0, actual);
            assertArrayEquals(test.expectArray, actualArray);
        }
    }
}