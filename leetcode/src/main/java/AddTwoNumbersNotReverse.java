import java.util.Stack;

public class AddTwoNumbersNotReverse {
    private AddTwoNumbers.ListNode reversList(AddTwoNumbers.ListNode l1, AddTwoNumbers.ListNode head) {
        if (l1 == null) {
            return null;
        }
        AddTwoNumbers.ListNode prev = reversList(l1.next, head);
        if (prev == null) {
            head.next = l1;
            return l1;
        }

        l1.next = null;
        prev.next = l1;

        return l1;
    }

    /**
     * Need to reverse the list three times.
     *
     * Time Complexity O(max(m + n))
     * Space Complexity O(max(m + n))
     */
    public AddTwoNumbers.ListNode addTwoNumbers(AddTwoNumbers.ListNode l1, AddTwoNumbers.ListNode l2) {
        AddTwoNumbers.ListNode h1 = new AddTwoNumbers.ListNode(0);
        reversList(l1, h1);

        AddTwoNumbers.ListNode h2 = new AddTwoNumbers.ListNode(0);
        reversList(l2, h2);

        AddTwoNumbers add = new AddTwoNumbers();
        AddTwoNumbers.ListNode ret = add.addTwoNumbersIterate(h1.next, h2.next);

        AddTwoNumbers.ListNode h3 = new AddTwoNumbers.ListNode(0);
        reversList(ret, h3);
        return h3.next;
    }

    private Integer popStack(Stack<Integer> stack) {
        if (stack != null && !stack.empty()){
            return stack.pop();
        }
        return null;
    }

    /**
     *
     * Time Complexity O(max(m + n))
     * Space Complexity O(max(m + n))
     */
    public AddTwoNumbers.ListNode addTwoNumbers2(AddTwoNumbers.ListNode l1, AddTwoNumbers.ListNode l2) {
        Stack<Integer> stack1 = new Stack<>();
        while (l1 != null) {
            stack1.push(l1.val);
            l1 = l1.next;
        }

        Stack<Integer> stack2 = new Stack<>();
        while (l2 != null) {
            stack2.push(l2.val);
            l2 = l2.next;
        }


        Integer x = popStack(stack1);
        Integer y = popStack(stack2);
        int carry = 0;
        AddTwoNumbers.ListNode prev = null;
        while (x != null || y != null || carry != 0) {
            int newVal = carry;
            if (x != null) {
                newVal += x;
                x = popStack(stack1);
            }

            if (y != null) {
                newVal += y;
                y = popStack(stack2);
            }

            if (newVal > 9) {
                carry = 1;
                newVal -= 10;
            } else {
                carry = 0;
            }

            AddTwoNumbers.ListNode newNode = new AddTwoNumbers.ListNode(newVal);
            if (prev != null) {
                newNode.next = prev;
            }

            prev = newNode;
        }

        return prev;
    }

    public static void main(String[] args) {
        AddTwoNumbers.ListNode num1 = AddTwoNumbers.ListNode.of(new int[]{3, 4, 2});
        AddTwoNumbers.ListNode num2 = AddTwoNumbers.ListNode.of(new int[]{4, 5, 6});
        AddTwoNumbersNotReverse add = new AddTwoNumbersNotReverse();

        System.out.println(add.addTwoNumbers2(num1, num2));

        num1 = AddTwoNumbers.ListNode.of(new int[]{4, 2});
        num2 = AddTwoNumbers.ListNode.of(new int[]{4, 6, 5});

        System.out.println(add.addTwoNumbers2(num1, num2));

        num1 = AddTwoNumbers.ListNode.of(new int[]{});
        num2 = AddTwoNumbers.ListNode.of(new int[]{4, 6, 5});
        System.out.println(add.addTwoNumbers2(num1, num2));

        num1 = AddTwoNumbers.ListNode.of(new int[]{1});
        num2 = AddTwoNumbers.ListNode.of(new int[]{2, 9, 9, 9});
        System.out.println(add.addTwoNumbers2(num1, num2));

        num1 = AddTwoNumbers.ListNode.of(new int[]{8});
        num2 = AddTwoNumbers.ListNode.of(new int[]{9, 9, 5});
        System.out.println(add.addTwoNumbers2(num1, num2));
    }


}

