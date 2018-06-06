package oi.github.ylgrgyq;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Author: ylgrgyq
 * Date: 18/6/5
 */
public class SkipList<K extends Comparable<? super K>, V>{
    private static final int levelLimit = 32;
    private static final double p = 1/4;

    private static class NodePtr {
        private Node next;

        NodePtr(Node node) {
            this.next = node;
        }
    }

    private static class Node<K, V> {
        private final K key;
        private final int level;
        private final NodePtr[] forwards;

        private V val;

        Node(K key, V value, int level) {
            this.key = key;
            this.level = level;
            this.val = value;
            this.forwards = new NodePtr[level];
            for (int i = 0; i < level; ++i) {
                this.forwards[i] = new NodePtr(null);
            }
        }

        @SuppressWarnings("unchecked")
        Node<K, V> getNextOnLevel(int level) {
            return (Node<K,V>)forwards[level].next;
        }

        void setNextOnLevel(Node nextNode, int level) {
            forwards[level].next = nextNode;
        }

        NodePtr getPointerOnLevel(int level) {
            return forwards[level];
        }

        NodePtr[] getPointers() {
            return forwards;
        }

        V swapVal(V newVal) {
            V old = this.val;
            this.val = newVal;
            return old;
        }
    }

    private final Node<K,V> header;

    public SkipList() {
        this.header = new Node<>(null, null, 1);
    }

    public V get(K key) {
        Node<K, V> x = header.getNextOnLevel(getMaxLevel() - 1);
        for (int i = getMaxLevel() - 1; i >= 0; --i) {
            while (x != null && x.key.compareTo(key) < 0) {
                x = x.getNextOnLevel(i);
            }
        }

        V v = null;
        if (x != null && x.key == key) {
            v = x.val;
        }
        return v;
    }

    private int getMaxLevel() {
        return header.level;
    }

    public V put(K key, V value) {
        NodePtr[] update = new NodePtr[levelLimit];

        Node<K,V> x = header;
        for (int i = getMaxLevel() - 1; i >= 0; --i) {
            Node<K,V> compareNode = x.getNextOnLevel(i);
            while (compareNode != null && compareNode.key.compareTo(key) < 0) {
                x = compareNode;
                compareNode = x.getNextOnLevel(i);
            }
            update[i] = x.getPointerOnLevel(i);
        }

        V prev = null;
        x = x.getNextOnLevel(0);
        if (x != null && x.key == key) {
            prev = x.swapVal(value);
        } else {
            int newLevel = makeRandomLevel();
            if (newLevel > getMaxLevel()) {
                int maxLevel = getMaxLevel();
                System.arraycopy(header.getPointers(), maxLevel, update, maxLevel, newLevel - maxLevel);
            }
            x = new Node<>(key, value, newLevel);

            for (int i = 0; i < newLevel; ++i) {
                x.setNextOnLevel(update[i].next, i);
                update[i].next = x;
            }
        }
        return prev;
    }

    private int makeRandomLevel() {
        int level = 1;
        while (ThreadLocalRandom.current().nextDouble() < p && level < levelLimit) {
            ++level;
        }
        return level;
    }

    public void remove(K key) {
        NodePtr[] update = new NodePtr[levelLimit];
        Node<K, V> x = header;

        for (int i = getMaxLevel() - 1; i >= 0; --i) {
            Node<K, V> compareNode = x.getNextOnLevel(i);
            while (compareNode != null && compareNode.key.compareTo(key) < 0) {
                x = compareNode;
                compareNode = x.getNextOnLevel(i);
            }
            update[i] = x.getPointerOnLevel(i);
        }

        x = x.getNextOnLevel(0);
        if (x != null && x.key == key) {
            for (int i = 0; i < x.level; i++) {
                update[i].next = x.getNextOnLevel(i);
                x.setNextOnLevel(null, i);
            }
        }
    }

    public static void main(String[] args) {
        SkipList<Integer, Integer> l = new SkipList<>();


        l.put(50, 1);
        l.put(21, 1);
        l.put(6, 3);
        l.put(68, 4);
        l.put(44, 6);
        l.put(25, 100);
        l.put(17, 8);

        System.out.println(l.get(5));
        System.out.println(l.get(2));
        System.out.println(l.get(6));
        System.out.println(l.get(4));
        System.out.println(l.get(1));

        l.remove(17);
        l.remove(44);
        l.remove(21);
        l.remove(25);
        l.remove(11);
        l.remove(68);
        l.remove(50);
    }
}
