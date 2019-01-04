package ZeroToFifty;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class LetterCombinationsOfAPhoneNumberTest {

    private static class TestCase {
        String input;
        List<String> expect;

        TestCase(String input, List<String> expect) {
            this.input = input;
            this.expect = expect;
        }
    }

    @Test
    public void letterCombinations() {
        TestCase[] tests = new TestCase[]{
                new TestCase("23", Arrays.asList("ad", "ae", "af", "bd", "be", "bf", "cd", "ce", "cf")),
        };

        for (TestCase test : tests) {
            String msg = String.format("test failed for input: %s", test.input);
            assertEquals(msg, test.expect, LetterCombinationsOfAPhoneNumber.letterCombinations(test.input));
        }
    }
}