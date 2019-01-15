package ZeroToTwentyFive;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class RemoveNthNodeFromEndOfListTest {
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
    public void removeNthFromEnd() {
        TestCase[] tests = new TestCase[]{
                new TestCase(new int[]{1, 2, 3, 4, 5}, 2, new int[]{1, 2, 3, 5}),
                new TestCase(new int[]{1, 2, 3, 4, 5}, 1, new int[]{1, 2, 3, 4}),
                new TestCase(new int[]{1, 2, 3, 4, 5}, 10, new int[]{1, 2, 3, 4, 5}),
                new TestCase(new int[]{1, 2, 3, 4, 5}, 5, new int[]{2, 3, 4, 5}),
                new TestCase(new int[]{1}, 1, null),
                new TestCase(new int[]{}, 1, null),
        };

        for (TestCase test : tests) {
            String msg = String.format("test failed for input: %s, target: %s", Arrays.toString(test.input), test.target);
            RemoveNthNodeFromEndOfList.ListNode inputList = RemoveNthNodeFromEndOfList.ListNode.of(test.input);
            RemoveNthNodeFromEndOfList.ListNode ret =
                    RemoveNthNodeFromEndOfList.removeNthFromEnd(inputList, test.target);
            if (test.expect == null) {
                assertNull(msg, ret);
            } else {
                assertNotNull(msg, ret);
                assertArrayEquals(msg, test.expect, ret.toArray());
            }
        }
    }
}