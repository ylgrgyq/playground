package ZeroToTwentyFive;

import java.util.ArrayList;
import java.util.List;

public class RemoveNthNodeFromEndOfList {
    public static class ListNode {
        int val;
        ListNode next;

        ListNode(int x) {
            val = x;
        }

        static ListNode of(int[] nums) {
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

        int[] toArray() {
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

    public static ListNode removeNthFromEnd(ListNode head, int n) {
        if (head == null || n < 1) return head;
        ListNode end = head;
        while (n > 0) {
            if (end == null) {
                return head;
            }
            end = end.next;
            --n;
        }

        ListNode prev = head;
        if (end == null) {
            return head.next;
        }

        while (end.next != null) {
            prev = prev.next;
            end = end.next;
        }

        ListNode deleteNode = prev.next;
        prev.next = deleteNode.next;
        deleteNode.next = null;

        return head;
    }
}
