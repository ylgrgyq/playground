package com.github.ylgrgyq.reservoir;

import java.nio.charset.StandardCharsets;

public class TestingUtils {
    public static String numberString(Number num) {
        return "" + num;
    }

    public static byte[] numberStringBytes(Number num) {
        return ("" + num).getBytes(StandardCharsets.UTF_8);
    }
}
