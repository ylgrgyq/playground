package ZeroToFifty;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class ContainerWithMostWaterTest {
    private static class TestCase {
        int[] input;
        int expect;

        TestCase(int[] input, int expect) {
            this.input = input;
            this.expect = expect;
        }
    }

    @Test
    public void maxArea() {
        TestCase[] tests = new TestCase[]{
                new TestCase(new int[]{1, 8, 6, 2, 5, 4, 8, 3, 7}, 49),
        };

        for (TestCase test : tests) {
            String msg = String.format("test failed for input: %s", Arrays.toString(test.input));
            assertEquals(msg, test.expect, ContainerWithMostWater.maxArea(test.input));
            assertEquals(msg, test.expect, ContainerWithMostWater.maxArea2(test.input));
        }
    }
}