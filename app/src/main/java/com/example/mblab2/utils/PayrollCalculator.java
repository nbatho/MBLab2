package com.example.mblab2.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Demo payroll policy shared by DB operations and attendance summaries. */
public final class PayrollCalculator {
    public static final double STANDARD_DAILY_HOURS = 8.0;
    public static final double OVERTIME_MULTIPLIER = 1.5;

    private PayrollCalculator() { }

    public static double calculateDailyOvertime(double totalHours) {
        requireNonNegativeFinite(totalHours, "Tổng giờ");
        return Math.max(totalHours - STANDARD_DAILY_HOURS, 0.0);
    }

    /** totalHours already includes overtimeHours; round only the final VND amount. */
    public static double calculateMonthlySalary(double totalHours, double overtimeHours,
                                                double hourlyRate) {
        requireNonNegativeFinite(totalHours, "Tổng giờ");
        requireNonNegativeFinite(overtimeHours, "Giờ tăng ca");
        if (!Double.isFinite(hourlyRate) || hourlyRate <= 0.0) {
            throw new IllegalArgumentException("Đơn giá phải là số hữu hạn lớn hơn 0");
        }
        if (overtimeHours > totalHours) {
            throw new IllegalArgumentException("Giờ tăng ca không được vượt tổng giờ");
        }
        BigDecimal total = BigDecimal.valueOf(totalHours);
        BigDecimal overtime = BigDecimal.valueOf(overtimeHours);
        BigDecimal rate = BigDecimal.valueOf(hourlyRate);
        double amount = total.subtract(overtime).multiply(rate)
                .add(overtime.multiply(rate).multiply(BigDecimal.valueOf(OVERTIME_MULTIPLIER)))
                .setScale(0, RoundingMode.HALF_UP).doubleValue();
        if (!Double.isFinite(amount)) {
            throw new IllegalArgumentException("Tiền lương vượt giới hạn tính toán");
        }
        return amount;
    }

    private static void requireNonNegativeFinite(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " phải là số hữu hạn không âm");
        }
    }
}
