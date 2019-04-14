package TwentySixToFifty;

public class MultiplyStrings {
    /*
    解法完全模拟了竖式乘法
    123 * 321

      123
      321
    ----
      123
     246
    369
    -----
    39483

    m = [1 2 3]
    n = [3 2 1]
     */
    public static String multiply(String num1, String num2) {
        assert num1 != null && num2 != null && !num1.isEmpty() && !num2.isEmpty();

        int m = num1.length();
        int n = num2.length();
        int[] product = new int[m + n];

        for (int i = m - 1; i >= 0; i--) {
            for (int j = n - 1; j >= 0; j--) {
                if (Character.isDigit(num1.charAt(i)) && Character.isDigit(num1.charAt(i))) {
                    product[i + j + 1] += (num1.charAt(i) - '0') * (num2.charAt(j) - '0');
                } else {
                    throw new IllegalArgumentException("multiply needs two numbers");
                }
            }
        }

        int carry = 0;
        for (int i = m + n - 1; i >= 0; i--) {
            int sum = product[i] + carry;
            carry = sum / 10;
            product[i] = sum % 10;
        }

        StringBuilder b = new StringBuilder();
        boolean start = false;
        for (int i = 0; i < m + n; i++) {
            if (product[i] != 0) {
                start = true;
            }
            if (start) {
                b.append(product[i]);
            }
        }
        return start ? b.toString() : "0";
    }


}
