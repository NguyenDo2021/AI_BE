package com.frontendbase.api.payment.print;

import java.util.ArrayList;
import java.util.List;

public final class VietnameseMoneyWords {
    private static final String[] DIGITS = {"không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín"};
    private static final String[] SCALES = {"", "nghìn", "triệu", "tỷ", "nghìn tỷ", "triệu tỷ", "tỷ tỷ"};
    private VietnameseMoneyWords() {}

    public static String of(long amount) {
        if (amount < 0) throw new IllegalArgumentException("Amount must be nonnegative");
        if (amount == 0) return "Không đồng";
        List<Integer> groups = new ArrayList<>();
        for (long n = amount; n > 0; n /= 1000) groups.add((int) (n % 1000));
        List<String> parts = new ArrayList<>();
        for (int i = groups.size() - 1; i >= 0; i--) {
            int group = groups.get(i);
            if (group == 0) continue;
            parts.add(triple(group, !parts.isEmpty()) + (i == 0 ? "" : " " + SCALES[i]));
        }
        String result = String.join(" ", parts);
        return Character.toUpperCase(result.charAt(0)) + result.substring(1) + " đồng";
    }

    private static String triple(int n, boolean full) {
        int h = n / 100, t = n / 10 % 10, u = n % 10;
        List<String> words = new ArrayList<>();
        if (h > 0 || full) { words.add(DIGITS[h]); words.add("trăm"); }
        if (t > 1) { words.add(DIGITS[t]); words.add("mươi"); }
        else if (t == 1) words.add("mười");
        else if (u > 0 && (h > 0 || full)) words.add("lẻ");
        if (u > 0) words.add(u == 1 && t > 1 ? "mốt" : u == 5 && t > 0 ? "lăm" : DIGITS[u]);
        return String.join(" ", words);
    }
}
