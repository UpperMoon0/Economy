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
            suffix = "t";
            divisor = 1_000_000_000_000.0;
        } else if (abs >= 1_000_000_000.0) {
            suffix = "b";
            divisor = 1_000_000_000.0;
        } else if (abs >= 1_000_000.0) {
            suffix = "m";
            divisor = 1_000_000.0;
        } else {
            suffix = "k";
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

    public static String formatMoney(BigDecimal val) {
        if (val == null) return "0";
        return val.stripTrailingZeros().toPlainString();
    }

    public static String formatMoneyCompact(BigDecimal val) {
        if (val == null) return "0";
        if (val.abs().compareTo(BigDecimal.valueOf(1000)) < 0) return formatMoney(val);
        return formatCompact(val);
    }

    public static String formatMoneyCompact(String str) {
        if (str == null || str.isEmpty()) return "0";
        try {
            return formatMoneyCompact(new BigDecimal(str));
        } catch (NumberFormatException e) {
            return str;
        }
    }

    public static double chartEpsilon(double min, double max) {
        double magnitude = Math.max(Math.abs(min), Math.abs(max));
        return Math.max(1e-9, magnitude * 1e-6);
    }

    public static double chartPadding(double min, double max) {
        double rawRange = max - min;
        double epsilon = chartEpsilon(min, max);
        double magnitude = Math.max(Math.abs(min), Math.abs(max));
        return rawRange <= epsilon
                ? Math.max(epsilon, magnitude * 0.05)
                : Math.max(epsilon, rawRange * 0.08);
    }

    public static double chartGraphMin(double min, double max) {
        double padding = chartPadding(min, max);
        return min >= 0 ? Math.max(0.0, min - padding) : min - padding;
    }

    public static double chartGraphRange(double min, double max) {
        double epsilon = chartEpsilon(min, max);
        double graphMax = max + chartPadding(min, max);
        return Math.max(epsilon, graphMax - chartGraphMin(min, max));
    }

    public static String formatFluidAmount(int milliBuckets) {
        return formatCompact(milliBuckets) + " mB";
    }

    public static String formatFluidAmountDetailed(int milliBuckets) {
        return formatFluidAmount(milliBuckets);
    }

    public static String formatCommodityQuantity(int quantity, boolean fluid) {
        return fluid
                ? formatFluidAmount(quantity)
                : formatItemAmount(quantity);
    }

    public static String formatCommodityQuantityDetailed(int quantity, boolean fluid) {
        return formatCommodityQuantity(quantity, fluid);
    }

    public static String formatItemAmount(int itemCount) {
        return formatCompact(itemCount) + (itemCount == 1 ? " item" : " items");
    }

    public static String formatCount(int count, String singular, String plural) {
        return formatCompact(count) + " " + (count == 1 ? singular : plural);
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
