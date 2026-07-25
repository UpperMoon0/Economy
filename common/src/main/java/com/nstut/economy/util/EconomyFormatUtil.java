package com.nstut.economy.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class EconomyFormatUtil {

    public static String formatCompact(double val) {
        if (Double.isNaN(val) || Double.isInfinite(val)) return "0";
        double abs = Math.abs(val);
        if (abs < 1000) {
            return String.valueOf((long) Math.round(val));
        }

        String suffix;
        double divisor;
        if (abs >= 1_000_000_000_000.0) {
            suffix = " t";
            divisor = 1_000_000_000_000.0;
        } else if (abs >= 1_000_000_000.0) {
            suffix = " b";
            divisor = 1_000_000_000.0;
        } else if (abs >= 1_000_000.0) {
            suffix = " m";
            divisor = 1_000_000.0;
        } else {
            suffix = " k";
            divisor = 1_000.0;
        }

        double scaled = val / divisor;
        DecimalFormat df = new DecimalFormat("#.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
        return df.format(scaled) + suffix;
    }

    public static String formatCompact(BigDecimal val) {
        if (val == null) return "0";
        return formatCompact(val.doubleValue());
    }

    public static String formatCompact(long val) {
        return formatCompact((double) val);
    }

    public static String formatCompact(String str) {
        if (str == null || str.isEmpty() || str.equals("--")) return "0";
        try {
            return formatCompact(Double.parseDouble(str));
        } catch (Exception e) {
            return str;
        }
    }

    public static String formatPriceChange(double percent) {
        if (Double.isNaN(percent)) {
            return "No Change";
        }
        DecimalFormat df = new DecimalFormat("#.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
        df.setMinimumFractionDigits(2);
        df.setMaximumFractionDigits(2);
        if (percent > 0) {
            return "+" + df.format(percent) + "%";
        } else {
            return df.format(percent) + "%";
        }
    }

    public static int getPriceChangeColor(double percent) {
        if (Double.isNaN(percent)) return 0xFF9E9E9E; // Gray
        if (percent > 0.001) return 0xFF66FF66; // Green
        if (percent < -0.001) return 0xFFFF6666; // Red
        return 0xFF9E9E9E; // Gray
    }
}
