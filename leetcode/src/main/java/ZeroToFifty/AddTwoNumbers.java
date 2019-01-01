package ZeroToFifty;

public class AddTwoNumbers {
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

    private ListNode addTwoNumbersRecursive(ListNode l1, ListNode l2, int carry) {
        if (l1 == null && l2 == null && carry == 0) {
            return null;
        }

        int newVal = carry;
        ListNode x = null;
        ListNode y = null;
        if (l1 != null) {
            newVal += l1.val;
            x = l1.next;
        }

        if (l2 != null) {
            newVal += l2.val;
            y = l2.next;
        }

        if (newVal > 9) {
            carry = 1;
            newVal -= 10;
        } else {
            carry = 0;
        }

        ListNode newNode = new ListNode(newVal);

        newNode.next = addTwoNumbersRecursive(x, y, carry);

        return newNode;
    }

    /**
     *
     * Time Complexity O(max(m + n))
     * Space Complexity O(max(m + n))
     */
    public ListNode addTwoNumbers1(ListNode l1, ListNode l2) {
        return addTwoNumbersRecursive(l1, l2, 0);
    }

    /**
     *
     * Time Complexity O(max(m + n))
     * Space Complexity O(max(m + n))
     */
    public ListNode addTwoNumbersIterate(ListNode l1, ListNode l2) {
        ListNode head = null;
        ListNode prev = null;
        int carry = 0;

        while(l1 != null || l2 != null || carry != 0) {
            int newVal = carry;
            if (l1 != null) {
                newVal += l1.val;
                l1 = l1.next;
            }

            if (l2 != null) {
                newVal += l2.val;
                l2 = l2.next;
            }

            if (newVal > 9) {
                carry = 1;
                newVal -= 10;
            } else {
                carry = 0;
            }

            ListNode newNode = new ListNode(newVal);
            if (prev == null) {
                head = newNode;
            } else {
                prev.next = newNode;
            }
            prev = newNode;
        }

        return head;
    }

    public static void main(String[] args) {
        ListNode num1 = ListNode.of(new int[]{2, 4, 3});
        ListNode num2 = ListNode.of(new int[]{5, 6, 4});
        AddTwoNumbers add = new AddTwoNumbers();
        System.out.println(add.addTwoNumbersIterate(num1, num2));

        num1 = ListNode.of(new int[]{2, 4});
        num2 = ListNode.of(new int[]{5, 6, 4});
        System.out.println(add.addTwoNumbersIterate(num1, num2));

        num1 = ListNode.of(new int[]{});
        num2 = ListNode.of(new int[]{5, 6, 4});
        System.out.println(add.addTwoNumbersIterate(num1, num2));

        num1 = ListNode.of(new int[]{1});
        num2 = ListNode.of(new int[]{9, 9, 9, 2});
        System.out.println(add.addTwoNumbersIterate(num1, num2));

        num1 = ListNode.of(new int[]{8});
        num2 = ListNode.of(new int[]{5, 9, 9});
        System.out.println(add.addTwoNumbersIterate(num1, num2));
    }


}
