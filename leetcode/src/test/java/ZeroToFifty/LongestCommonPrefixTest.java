package ZeroToFifty;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class LongestCommonPrefixTest {
    private static class TestCase {
        String[] input;
        String expect;

        TestCase(String[] input, String expect) {
            this.input = input;
            this.expect = expect;
        }
    }

    @Test
    public void longestCommonPrefix() {
        TestCase[] tests = new TestCase[]{
                new TestCase(new String[]{"flower","flow","flight"}, "fl"),
                new TestCase(new String[]{"flower","flow","flowwe"}, "flow"),
                new TestCase(new String[]{"flower","flower","flower"}, "flower"),
                new TestCase(new String[]{"dog","racecar","car"}, ""),
                new TestCase(new String[]{"","caracecar","car"}, ""),
                new TestCase(new String[]{}, ""),
                new TestCase(new String[]{"hahaha"}, "hahaha"),
                new TestCase(new String[]{"hahaha", "hoho"}, "h"),
                new TestCase(new String[]{"a"}, "a"),
        };

        for (TestCase test : tests) {
            String msg = String.format("test failed for input: %s", Arrays.toString(test.input));
            assertEquals(msg, test.expect, LongestCommonPrefix.longestCommonPrefix(test.input));
            assertEquals(msg, test.expect, LongestCommonPrefix.longestCommonPrefix2(test.input));
        }
    }

}