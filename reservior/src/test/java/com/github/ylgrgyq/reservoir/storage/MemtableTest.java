package com.github.ylgrgyq.reservoir.storage;

import com.github.ylgrgyq.reservoir.ObjectWithId;
import com.github.ylgrgyq.reservoir.TestingUtils;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


public class MemtableTest {
    private Memtable mm;

    @Before
    public void setUp() throws Exception {
        mm = new Memtable();
    }

    @Test
    public void testIsEmpty() {
        assertThat(mm.isEmpty()).isEqualTo(true);
        mm.add(new ObjectWithId(100, new byte[]{1, 2, 3}));
        assertThat(mm.isEmpty()).isEqualTo(false);
    }

    @Test
    public void testFirstIdLastId() {
        assertThat(mm.firstId()).isEqualTo(-1);
        assertThat(mm.lastId()).isEqualTo(-1);

        for (int i = 1; i < 10000; i++) {
            mm.add(new ObjectWithId(i, new byte[]{1, 2, 3}));
            assertThat(mm.firstId()).isEqualTo(1);
            assertThat(mm.lastId()).isEqualTo(i);
        }
    }

    @Test
    public void testGetMemoryUsedInBytes() {
        assertThat(mm.getMemoryUsedInBytes()).isZero();

        final byte[] data = "Hello".getBytes(StandardCharsets.UTF_8);
        for (int i = 1; i < 10000; i++) {
            mm.add(new ObjectWithId(i, data));
            assertThat(mm.getMemoryUsedInBytes()).isEqualTo(i * (Long.BYTES + data.length));
        }
    }

    @Test
    public void testGetFromEmptyMemtable() {
        assertThat(mm.getEntries(Long.MIN_VALUE, 100)).isEmpty();
    }

    @Test
    public void testGetFromSingleElementMemtable() {
        final long id = 100;
        final ObjectWithId val = new ObjectWithId(100, new byte[]{1, 2, 3});
        mm.add(val);
        assertThat(mm.getEntries(Long.MIN_VALUE, 100)).hasSize(1).containsExactly(val);
        assertThat(mm.getEntries(id, 100)).isEmpty();
    }

    @Test
    public void testGetBeforeStartElement() {
        final List<ObjectWithId> savedElements = addElementsToMemtable(10000);

        assertThat(mm.getEntries(Long.MIN_VALUE, 100))
                .hasSize(100)
                .isEqualTo(savedElements.subList(0, 100));
    }

    @Test
    public void testGetFromStartToEnd() {
        final List<ObjectWithId> savedElements = addElementsToMemtable(10000);
        final long startId = savedElements.get(0).getId();
        final int bound = 10;

        for (long i = startId; i < savedElements.size() - bound; i += bound) {
            final List<ObjectWithId> expect = savedElements.subList((int) (i - startId + 1), (int) (i - startId + bound + 1));
            assertThat(mm.getEntries(i, bound))
                    .hasSize(bound)
                    .isEqualTo(expect);
        }
    }

    @Test
    public void testLimitGreaterThanSize() {
        final List<ObjectWithId> savedElements = addElementsToMemtable(100);

        for (long i = 1; i < savedElements.size(); ++i) {
            final List<ObjectWithId> expect = savedElements.subList((int) i, savedElements.size());
            assertThat(mm.getEntries(i, 100))
                    .isEqualTo(expect);
        }
    }

    @Test
    public void testIterateFromEmptyMemtable() {
        assertThat(mm.iterator()).isExhausted();
    }

    @Test
    public void testIterateBeforeStart() {
        final List<ObjectWithId> savedElements = addElementsToMemtable(10000);
        assertThat(mm.iterator()).toIterable().isEqualTo(savedElements);
        assertThat(mm.iterator().seek(Long.MIN_VALUE)).toIterable().isEqualTo(savedElements);
    }

    @Test
    public void testIterateFromMiddle() {
        final List<ObjectWithId> savedElements = addElementsToMemtable(100);

        for (long i = 1; i < savedElements.size(); ++i) {
            final List<ObjectWithId> expect = savedElements.subList((int) i, savedElements.size());
            assertThat(mm.iterator().seek(i))
                    .toIterable()
                    .isEqualTo(expect);
        }
    }

    @Test
    public void testIterateFromEnd() {
        final List<ObjectWithId> savedElements = addElementsToMemtable(10);
        final long lastId = savedElements.get(savedElements.size() - 1).getId();
        assertThat(mm.iterator().seek(lastId))
                .isExhausted();
    }

    private List<ObjectWithId> addElementsToMemtable(int size) {
        final List<ObjectWithId> objs = new ArrayList<>();
        for (int i = 1; i <= size; i++) {
            ObjectWithId obj = new ObjectWithId(i, TestingUtils.numberStringBytes(i));
            objs.add(obj);
            mm.add(obj);
        }
        return objs;
    }
}