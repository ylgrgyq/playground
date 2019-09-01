package com.github.ylgrgyq.reservoir.storage;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BlockHandleTest {

    @Test
    public void testEncodeDecode() {
        IndexBlockHandle handle = new IndexBlockHandle(Long.MIN_VALUE, Integer.MIN_VALUE);
        assertThat(IndexBlockHandle.decode(handle.encode())).isEqualTo(handle);
    }
}