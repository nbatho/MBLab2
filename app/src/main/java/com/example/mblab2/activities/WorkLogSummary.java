package com.example.mblab2.activities;

import com.example.mblab2.models.WorkLog;
import com.example.mblab2.utils.PayrollCalculator;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Tổng hợp WorkLog trong một tháng cho màn hình chấm công và lương.
 * Chỉ ca COMPLETED được cộng giờ; ca CHECKED_IN được đếm riêng là ca mở.
 */
final class WorkLogSummary {

    static final String DB_DATE_PATTERN = "yyyy-MM-dd";
    private static final String DISPLAY_DATE_PATTERN = "dd/MM/yyyy";

    final int completedShifts;
    final int openShifts;
    final double totalHours;
    final double overtimeHours;

    private WorkLogSummary(int completedShifts, int openShifts, double totalHours, double overtimeHours) {
        this.completedShifts = completedShifts;
        this.openShifts = openShifts;
        this.totalHours = totalHours;
        this.overtimeHours = overtimeHours;
    }

    static WorkLogSummary of(List<WorkLog> logs) {
        int completed = 0;
        int open = 0;
        double total = 0;
        double overtime = 0;
        for (WorkLog log : logs) {
            if (WorkLog.STATUS_COMPLETED.equals(log.getStatus())) {
                completed++;
                // Không làm tròn từng ca, tăng ca tính riêng theo từng ngày.
                total += log.getTotalHours();
                overtime += PayrollCalculator.calculateDailyOvertime(log.getTotalHours());
            } else if (WorkLog.STATUS_CHECKED_IN.equals(log.getStatus())) {
                open++;
            }
        }
        return new WorkLogSummary(completed, open, total, overtime);
    }

    double getRegularHours() {
        return totalHours - overtimeHours;
    }

    boolean hasOpenShifts() {
        return openShifts > 0;
    }

    static String today() {
        return new SimpleDateFormat(DB_DATE_PATTERN, Locale.US).format(new Date());
    }

    /** Đổi ngày lưu yyyy-MM-dd sang dd/MM/yyyy; giữ nguyên chuỗi nếu không parse được. */
    static String formatWorkDate(String workDate) {
        if (workDate == null) {
            return "";
        }
        SimpleDateFormat parser = new SimpleDateFormat(DB_DATE_PATTERN, Locale.US);
        parser.setLenient(false);
        try {
            Date date = parser.parse(workDate);
            return new SimpleDateFormat(DISPLAY_DATE_PATTERN, Locale.US).format(date);
        } catch (ParseException e) {
            return workDate;
        }
    }
}
