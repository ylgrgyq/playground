package ZeroToFifty;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class ThreeSumClosestTest {
    private static class TestCase {
        int[] input;
        int inputTarget;
        int expect;

        TestCase(int[] input, int target, int expect) {
            this.input = input;
            this.inputTarget = target;
            this.expect = expect;
        }
    }

    @Test
    public void threeSumClosest() {
        TestCase[] tests = new TestCase[]{
                new TestCase(new int[]{-1, 2, 1, -4}, 1, 2),
        };

        for (TestCase test : tests) {
            String msg = String.format("test failed for input: %s", Arrays.toString(test.input));
            assertEquals(msg, test.expect, ThreeSumClosest.threeSumClosest(test.input, test.inputTarget));
        }
    }
}