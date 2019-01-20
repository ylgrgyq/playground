package TwentySixToFifty;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class CombinationSumTest {
    private static class TestCase {
        int[] input;
        int target;
        int[][] expect;

        TestCase(int[] input, int target, int[][] expect) {
            this.input = input;
            this.target = target;
            this.expect = expect;
        }
    }

    @Test
    public void combinationSum() {
        TestCase[] tests = new TestCase[]{
                new TestCase(new int[]{2, 3, 6, 7}, 7, new int[][]{new int[]{7}, new int[]{2, 2, 3}}),
                new TestCase(new int[]{2, 3, 5}, 8, new int[][]{new int[]{2, 2, 2, 2}, new int[]{2, 3, 3}, new int[]{3, 5}}),
        };

        for (TestCase test : tests) {
            List<List<Integer>> actual = CombinationSum.combinationSum(test.input, test.target);
            String msg = String.format("test failed for input: %s, target: %s",
                    Arrays.toString(test.input), test.target);
            Object[] a = actual.stream().map(List::toArray).toArray();
//            assertArrayEquals(msg, test.expect, a);
        }
    }
}