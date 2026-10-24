package com.example.mblab2.dal;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Strict date/time parsing and month validation for attendance and payroll. */
final class DateTimeUtils {
    private DateTimeUtils() { }

    static String format(String pattern, Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat(pattern, Locale.US);
        formatter.setLenient(false);
        return formatter.format(date);
    }

    static long parseDateTime(String date, String time) {
        String input = date + " " + time;
        if (!input.matches("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}")) {
            throw new IllegalStateException("Ngày giờ chấm công sai định dạng");
        }
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        formatter.setLenient(false);
        try {
            Date parsed = formatter.parse(input);
            if (parsed == null || !formatter.format(parsed).equals(input)) {
                throw new IllegalStateException("Ngày giờ chấm công không hợp lệ");
            }
            return parsed.getTime();
        } catch (ParseException ex) {
            throw new IllegalStateException("Ngày giờ chấm công không hợp lệ", ex);
        }
    }

    static void validateMonth(int month, int year) {
        if (month < 1 || month > 12 || year < 1 || year > 9999) {
            throw new IllegalArgumentException("Tháng/năm không hợp lệ");
        }
    }
}
