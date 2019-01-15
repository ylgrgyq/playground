package ZeroToTwentyFive;

import org.junit.Test;
import util.ListNode;

import java.util.Arrays;

import static org.junit.Assert.*;

public class MergeTwoSortedListsTest {
    private static class TestCase {
        int[] left;
        int[] right;
        int[] expect;

        TestCase(int[] inputLeft, int[] inputRight, int expect[]) {
            this.left = inputLeft;
            this.right = inputRight;
            this.expect = expect;
        }
    }

    @Test
    public void mergeTwoLists() {
        TestCase[] tests = new TestCase[]{
                new TestCase(new int[]{1, 2, 4}, new int[]{1, 3, 4}, new int[]{1, 1, 2, 3, 4, 4}),
                new TestCase(new int[]{}, new int[]{1, 3, 4}, new int[]{1, 3, 4}),
                new TestCase(new int[]{1, 2, 4}, new int[]{}, new int[]{1, 2, 4}),
                new TestCase(new int[]{1, 2, 4, 6, 19}, new int[]{-1, 3, 4}, new int[]{-1, 1, 2, 3, 4, 4, 6, 19}),
        };

        for (TestCase test : tests) {
            String msg = String.format("test failed for input: %s, %s", Arrays.toString(test.left), Arrays.toString(test.right));
            assertArrayEquals(msg, test.expect,
                    MergeTwoSortedLists.mergeTwoLists(ListNode.of(test.left), ListNode.of(test.right)).toArray());

        }
    }
}