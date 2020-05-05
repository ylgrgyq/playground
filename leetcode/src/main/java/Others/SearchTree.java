package Others;

public class SearchTree {
    public static class Node {
        int val;
        Node left;
        Node right;
    }

    public static boolean search(Node root, int val) {
        Node cur = root;
        while (cur != null) {
            if (val == cur.val) {
                return true;
            } else if (val < cur.val) {
                cur = cur.left;
            } else {
                cur = cur.right;
            }
        }
        return false;
    }

    public static Node setVal(Node root, int input) {
        if (root == null) {
            Node node = new Node();
            node.val = input;
            return node;
        }

        if (input < root.val) {
            root.left = setVal(root.left, input);
        } else {
            root.right = setVal(root.right, input);
        }

        return root;
    }

    public static Node buildTree(int[] input) {
        if (input == null || input.length == 0) {
            return null;
        }

        Node root = null;
        for (int val : input) {
            root = setVal(root, val);
        }
        return root;
    }

    public static void inOrderPrintTree(Node root) {
        if (root == null) {
            return;
        }

        inOrderPrintTree(root.left);
        System.out.println(root.val);
        inOrderPrintTree(root.right);
    }

    public static void main(String[] args) {
        int[] input = new int[]{4, 1, 2, 8, -1, 6, 3, 2};
        Node root = buildTree(input);
        inOrderPrintTree(root);
        System.out.println(search(root, 8));
        System.out.println(search(root, -1));
        System.out.println(search(root, 3));
        System.out.println(search(root, 2));
        System.out.println(search(root, 7));
        System.out.println(search(root, 21));

    }
}
