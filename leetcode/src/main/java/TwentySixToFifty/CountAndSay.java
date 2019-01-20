package TwentySixToFifty;

public class CountAndSay {
    public static String countAndSay(int n) {
        if (n == 1) return "1";

        String ret = "1";
        for (int i = 2; i <= n; i++) {
            ret = doCountAndSay(ret);
        }
        return ret;
    }

    private static String doCountAndSay(String num) {
        StringBuilder b = new StringBuilder();

        int i = 0;
        while (i < num.length()) {
            int count = 1;
            for (int j = i + 1; j < num.length(); ++j) {
                if (num.charAt(i) == num.charAt(j)) {
                    ++count;
                    i = j;
                } else {
                    break;
                }
            }
            b.append(count);
            b.append(num.charAt(i));
            ++i;
        }

        return b.toString();
    }
}
