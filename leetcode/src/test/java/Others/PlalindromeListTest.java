package Others;

import Others.PlalindromeList;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

public class PlalindromeListTest {
    private static Random random = new Random();

    @Test
    public void emptyList() {
        PlalindromeList<Integer> list = new PlalindromeList<>();
        assertFalse(list.isPlalindrome());
    }

    @Test
    public void singleElement() {
        PlalindromeList<Integer> list = new PlalindromeList<>();
        list.add(1);
        assertTrue(list.isPlalindrome());
    }

    @Test
    public void twoElement() {
        PlalindromeList<Integer> list = new PlalindromeList<>();
        list.add(1);
        list.add(1);
        assertTrue(list.isPlalindrome());
    }

    @Test
    public void twoElement2() {
        PlalindromeList<Integer> list = new PlalindromeList<>();
        list.add(1);
        list.add(2);
        assertFalse(list.isPlalindrome());
    }

    @Test(expected = NullPointerException.class)
    public void addNull() {
        PlalindromeList<Integer> list = new PlalindromeList<>();
        list.add(null);
    }

    @Test
    public void threeElement() {
        PlalindromeList<Integer> list = new PlalindromeList<>();
        list.add(1);
        list.add(2);
        list.add(1);
        assertTrue(list.isPlalindrome());
    }

    @Test
    public void threeElement2() {
        PlalindromeList<Integer> list = new PlalindromeList<>();
        list.add(1);
        list.add(2);
        list.add(2);
        assertFalse(list.isPlalindrome());
    }

    @Test
    public void testEven() {
        PlalindromeList<Integer> plalindromeList = buildPlalindromeWithList(generatePlalindromeList(random.nextInt(100), true));
        assertTrue(plalindromeList.isPlalindrome());
    }

    @Test
    public void testOdd() {
        PlalindromeList<Integer> plalindromeList = buildPlalindromeWithList(generatePlalindromeList(random.nextInt(100), false));
        assertTrue(plalindromeList.isPlalindrome());
    }
    
    private PlalindromeList<Integer> buildPlalindromeWithList(List<Integer> input) {
        final PlalindromeList<Integer> plalindromeList = new PlalindromeList<>();
        for (Integer in : input) {
            plalindromeList.add(in);
        }
        return plalindromeList;
    }

    private List<Integer> generatePlalindromeList(int size, boolean even) {
        final List<Integer> list = generateRandomList(size);

        if (even) {
            List<Integer> ret = new ArrayList<>(2 * size);
            ret.addAll(list);
            Collections.reverse(list);
            ret.addAll(list);
            return ret;
        } else {
            List<Integer> ret = new ArrayList<>(2 * size + 1);
            ret.addAll(list);
            ret.add(random.nextInt());
            Collections.reverse(list);
            ret.addAll(list);
            return ret;
        }
    }

    private List<Integer> generateRandomList(int size) {
        final List<Integer> list = new ArrayList<>(size);
        for (int i = 0; i < size; ++i) {
            list.add(random.nextInt());
        }

        return list;
    }
}