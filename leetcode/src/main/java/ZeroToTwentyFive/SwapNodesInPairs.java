package ZeroToTwentyFive;

import util.ListNode;

public class SwapNodesInPairs {
    public static ListNode swapPairs(ListNode head) {
        ListNode dummy = new ListNode(0);
        dummy.next = head;

        ListNode p = dummy;
        ListNode q = head;
        while (q != null && q.next != null) {
            // p -> q -> x -> y -> ...
            // we'd like to swap q and x
            ListNode x = q.next;
            ListNode y = x.next;

            p.next = x;
            x.next = q;
            q.next = y;

            // after swap we have list: p -> x -> q -> y -> ...
            // next time we need swap y and y.next
            p = q;
            q = p.next;
        }

        return dummy.next;
    }
}
