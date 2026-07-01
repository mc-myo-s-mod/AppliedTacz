package me.myogoo.appliedtacz.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class AEUtils {
    private static final String[] SUFFIXES = new String[] { "", "k", "m", "g", "t", "p", "e" };
    private static final DecimalFormat WHOLE_FORMAT = new DecimalFormat("0",
            DecimalFormatSymbols.getInstance(Locale.ROOT));
    private static final DecimalFormat SINGLE_DECIMAL_FORMAT = new DecimalFormat("0.#",
            DecimalFormatSymbols.getInstance(Locale.ROOT));
    private static final DecimalFormat DOUBLE_DECIMAL_FORMAT = new DecimalFormat("0.##",
            DecimalFormatSymbols.getInstance(Locale.ROOT));

    public static String formatAmount(long amount) {
        if (amount < 1000) {
            return Long.toString(amount);
        }

        int index = 0;
        double value = amount;
        while (value >= 999.95 && index < SUFFIXES.length - 1) {
            value /= 1000.0;
            index++;
        }

        DecimalFormat format = value >= 100 ? WHOLE_FORMAT : value >= 10 ? SINGLE_DECIMAL_FORMAT : DOUBLE_DECIMAL_FORMAT;
        return format.format(value) + SUFFIXES[index];
    }
}
