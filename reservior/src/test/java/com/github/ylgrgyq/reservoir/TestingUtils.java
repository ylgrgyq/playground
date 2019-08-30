package com.github.ylgrgyq.reservoir;

import java.nio.charset.StandardCharsets;
import java.util.Random;

public class TestingUtils {
    public static String numberString(Number num) {
        return "" + num;
    }

    public static byte[] numberStringBytes(Number num) {
        return ("" + num).getBytes(StandardCharsets.UTF_8);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    public static int nextPositiveInt(Random random, int bound) {
        int nextInt;
        while ((nextInt = random.nextInt(bound)) <= 0) {
            // loop
        }
        return nextInt;
    }
}
