package Others;

public class PlalindromeList<T extends Comparable<? super T>> {

    private static class Node<T> {
        T val;
        Node<T> next;
        Node<T> prev;
    }

    private Node<T> head;
    private Node<T> tail;

    public PlalindromeList() {
        tail = head = new Node<>();
    }

    public void add(T val) {
        if (val == null) {
            throw new NullPointerException();
        }

        final Node<T> newNode = new Node<>();
        newNode.val = val;
        newNode.prev = tail;

        tail.next = newNode;
        tail = newNode;
    }

    public boolean isPlalindrome() {
        if (head == tail) {
            return false;
        }

        if (head.next == tail) {
            return true;
        }

        Node<T> left = head.next;
        Node<T> right = tail;

        while (left != right) {
            if (left.val.compareTo(right.val) != 0) {
                return false;
            }

            if (left.next == right || left.next == right.prev) {
                break;
            }

            left = left.next;
            right = right.prev;
        }

        return true;
    }
}
