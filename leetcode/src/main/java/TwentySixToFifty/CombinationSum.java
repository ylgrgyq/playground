package TwentySixToFifty;

import java.util.*;

public class CombinationSum {

    public static List<List<Integer>> combinationSum(int[] candidates, int target) {
        Arrays.sort(candidates);

        List<List<List<Integer>>> ret = new ArrayList<>();

        for (int partialTarget = 0; partialTarget <= target; partialTarget++) {
            List<List<Integer>> combinedList = new ArrayList<>();

            for (int c : candidates) {
                if (c > partialTarget) {
                    break;
                } else if (c == partialTarget) {
                    combinedList.add(Collections.singletonList(c));
                } else {
                    for (List<Integer> partResult : ret.get(partialTarget - c)) {
                        if (c >= partResult.get(partResult.size() - 1)) {
                            List<Integer> nextPart = new ArrayList<>(partResult);
                            nextPart.add(c);
                            combinedList.add(nextPart);
                        }
                    }
                }
            }

            ret.add(partialTarget, combinedList);
        }

        return ret.get(target);
    }
}
