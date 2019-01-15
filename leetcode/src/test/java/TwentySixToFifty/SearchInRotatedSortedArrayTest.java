package TwentySixToFifty;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class SearchInRotatedSortedArrayTest {
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
    public void search() {
        TestCase[] tests = new TestCase[]{
                new TestCase(new int[]{4, 5, 6, 7, 0, 1, 2},0, 4),
                new TestCase(new int[]{4, 5, 6, 7, 0, 1, 2},4, 0),
                new TestCase(new int[]{4, 5, 6, 7, 0, 1, 2},2, 6),
                new TestCase(new int[]{4, 5, 6, 7, 0, 1, 2},7, 3),
                new TestCase(new int[]{4, 5, 6, 7, 0, 1, 2},3, -1),
        };

        for (TestCase test : tests) {
            int actual = SearchInRotatedSortedArray.search(test.input, test.target);
            String msg = String.format("test failed for input: %s, target: %s, expect: %s, actual: %s",
                    Arrays.toString(test.input), test.target, test.expect, actual);
            assertEquals(msg, test.expect, actual);
        }
    }
}