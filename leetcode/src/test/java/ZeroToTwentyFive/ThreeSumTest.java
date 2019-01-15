package ZeroToTwentyFive;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class ThreeSumTest {
    private static class TestCase {
        int[] input;
        int expectSize;

        TestCase(int[] input, int expectSize) {
            this.input = input;
            this.expectSize = expectSize;
        }
    }

    @Test
    public void threeSum() {
        TestCase[] tests = new TestCase[]{
                new TestCase(new int[]{-1, 0, 1, 2, -1, -4}, 2),
        };

        for (TestCase test : tests) {
            String msg = String.format("test failed for input: %s", Arrays.toString(test.input));
            List<List<Integer>> ret = ThreeSum.threeSum(test.input);
            assertEquals(msg, test.expectSize, ret.size());
            for (List<Integer> r : ret) {
                String subMsg = String.format("%s\ngot:%s", msg, r);
                assertEquals(subMsg, 0, r.stream().mapToInt(Integer::intValue).sum());
            }
        }
    }

}