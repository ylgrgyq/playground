package util;

import java.util.ArrayList;
import java.util.List;

public class ListNode {
    public int val;
    public ListNode next;

    public ListNode(int x) {
        val = x;
    }

    public static ListNode of(int[] nums) {
        ListNode head = null;
        ListNode prev = null;
        for (int num : nums) {
            ListNode node = new ListNode(num);
            if (prev != null) {
                prev.next = node;
            }
            if (head == null) {
                head = node;
            }
            prev = node;
        }

        return head;
    }

    public int[] toArray() {
        List<Integer> s  = new ArrayList<>();
        ListNode n = this;
        while (n != null) {
            s.add(n.val);
            n = n.next;
        }
        return s.stream().mapToInt(Integer::intValue).toArray();
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("[");

        boolean first = true;
        ListNode x = this;
        while (x != null) {
            if (!first) {
                str.append(",");
            }
            first = false;

            str.append(x.val);
            x = x.next;
        }

        str.append("]");

        return str.toString();
    }
}
