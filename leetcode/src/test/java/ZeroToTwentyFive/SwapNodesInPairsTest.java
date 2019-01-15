package ZeroToTwentyFive;

import org.junit.Test;
import util.ListNode;

import java.util.Arrays;

import static org.junit.Assert.*;

public class SwapNodesInPairsTest {

    private static class TestCase {
        int[] input;
        int[] expect;

        TestCase(int[] input, int[] expect) {
            this.input = input;
            this.expect = expect;
        }
    }

    @Test
    public void swapPairs() {
        TestCase[] tests = new TestCase[]{
                new TestCase(new int[]{1}, new int[]{1}),
                new TestCase(new int[]{1, 2}, new int[]{2, 1}),
                new TestCase(new int[]{1, 2, 3}, new int[]{2, 1, 3}),
                new TestCase(new int[]{1, 2, 3, 4}, new int[]{2, 1, 4, 3}),
        };

        for (TestCase test : tests) {
            String msg = String.format("test failed for input: %s", Arrays.toString(test.input));
            assertArrayEquals(msg, test.expect, SwapNodesInPairs.swapPairs(ListNode.of(test.input)).toArray());
        }
    }
}