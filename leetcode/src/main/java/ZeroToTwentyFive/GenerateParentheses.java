package ZeroToTwentyFive;

import java.util.*;

public class GenerateParentheses {

    private static class Record {
        String b;
        int leftRemain;
        int rightRemain;

        public Record(String b, int leftRemain, int rightRemain) {
            this.b = b;
            this.leftRemain = leftRemain;
            this.rightRemain = rightRemain;
        }
    }

    public static List<String> generateParenthesis(int n) {
        Queue<Record> rs = new LinkedList<>();
        rs.add(new Record("", n, n));
        for (int i = 0; i < 2 * n; i++) {
            Record r;
            while ((r = rs.peek()) != null && r.b.length() == i) {
                rs.poll();
                if (r.leftRemain > 0) {
                    rs.add(new Record(r.b + "(", r.leftRemain - 1, r.rightRemain));
                }

                if (r.rightRemain > 0 && r.rightRemain > r.leftRemain) {
                    rs.add(new Record(r.b + ")", r.leftRemain, r.rightRemain - 1));
                }
            }
        }

        List<String> dup = new ArrayList<>();
        for (Record b : rs) {
            dup.add(b.b);
        }
        return dup;
    }

    private static void doGenerateParenthesis2(List<String> ret, String cur, int leftUsed, int rightUsed, int max) {
        if (leftUsed == max && rightUsed == max) {
            ret.add(cur);
            return;
        }

        if (leftUsed < max) {
            doGenerateParenthesis2(ret, cur + "(", leftUsed + 1, rightUsed, max);
        }

        if (leftUsed > rightUsed) {
            doGenerateParenthesis2(ret, cur + ")", leftUsed, rightUsed + 1, max);
        }
    }

    public static List<String> generateParenthesis2(int n) {
        List<String> ret = new ArrayList<>();
        doGenerateParenthesis2(ret, "", 0, 0, n);
        return ret;
    }
}
