package TwentySixToFifty;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class SearchInsertPositionTest {

    private static class TestCase {
        int[] input;
        int target;
        int expect;

        TestCase(int[] input, int target, int expect) {
            this.input = input;
            this.target = target;
            this.expect = expect;
        }
    }

    @Test
    public void searchInsert() {
        TestCase[] tests = new TestCase[]{
                new TestCase(new int[]{1, 3, 5, 6}, 5, 2),
                new TestCase(new int[]{1, 3, 5, 6}, 2, 1),
                new TestCase(new int[]{1, 3, 5, 6}, 7, 4),
                new TestCase(new int[]{1, 3, 5, 6}, 0, 0),
        };

        for (TestCase test : tests) {
            int actual = SearchInsertPosition.searchInsert(test.input, test.target);
            String msg = String.format("test failed for input: %s, target: %s, expect: %s, actual: %s",
                    Arrays.toString(test.input), test.target, test.expect, actual);
            assertEquals(msg, test.expect, actual);
        }
    }
}