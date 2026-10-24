package com.example.mblab2.activities;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.SQLException;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.mblab2.R;
import com.example.mblab2.dal.DBHelper;
import com.example.mblab2.models.Account;
import com.example.mblab2.models.Employee;
import com.example.mblab2.models.WorkLog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class AttendanceActivity extends AppCompatActivity {

    private static final String STATE_MONTH = "attendance_month";
    private static final String STATE_YEAR = "attendance_year";

    private DBHelper dbHelper;
    private int accountId = -1;
    private Employee employee;
    private int selectedMonth;
    private int selectedYear;

    private TextView tvEmployee;
    private TextView tvTodayDate;
    private TextView tvTodayStatus;
    private TextView tvTodayCheckIn;
    private TextView tvTodayCheckOut;
    private TextView tvTodayHours;
    private MaterialButton btnCheckIn;
    private MaterialButton btnCheckOut;
    private TextView tvMonth;
    private TextView tvSummaryShifts;
    private TextView tvSummaryTotal;
    private TextView tvSummaryRegular;
    private TextView tvSummaryOvertime;
    private TextView tvOpenWarning;
    private TextView tvHistoryEmpty;
    private LinearLayout historyContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_attendance);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.attendance_root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        dbHelper = new DBHelper(this);
        bindViews();

        Calendar now = Calendar.getInstance();
        selectedMonth = now.get(Calendar.MONTH) + 1;
        selectedYear = now.get(Calendar.YEAR);
        if (savedInstanceState != null) {
            selectedMonth = savedInstanceState.getInt(STATE_MONTH, selectedMonth);
            selectedYear = savedInstanceState.getInt(STATE_YEAR, selectedYear);
        }

        btnCheckIn.setOnClickListener(v -> onCheckIn());
        btnCheckOut.setOnClickListener(v -> onCheckOut());
        findViewById(R.id.attendance_btn_prev_month).setOnClickListener(v -> shiftMonth(-1));
        findViewById(R.id.attendance_btn_next_month).setOnClickListener(v -> shiftMonth(1));
        tvMonth.setOnClickListener(v -> pickMonth());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Kiểm tra lại tài khoản thật mỗi lần quay lại màn hình.
        if (loadSession()) {
            refreshAll();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_MONTH, selectedMonth);
        outState.putInt(STATE_YEAR, selectedYear);
    }

    @Override
    protected void onDestroy() {
        if (dbHelper != null) {
            dbHelper.close();
        }
        super.onDestroy();
    }

    private void bindViews() {
        tvEmployee = findViewById(R.id.attendance_tv_employee);
        tvTodayDate = findViewById(R.id.attendance_tv_today_date);
        tvTodayStatus = findViewById(R.id.attendance_tv_today_status);
        tvTodayCheckIn = findViewById(R.id.attendance_tv_today_check_in);
        tvTodayCheckOut = findViewById(R.id.attendance_tv_today_check_out);
        tvTodayHours = findViewById(R.id.attendance_tv_today_hours);
        btnCheckIn = findViewById(R.id.attendance_btn_check_in);
        btnCheckOut = findViewById(R.id.attendance_btn_check_out);
        tvMonth = findViewById(R.id.attendance_tv_month);
        tvSummaryShifts = findViewById(R.id.attendance_tv_summary_shifts);
        tvSummaryTotal = findViewById(R.id.attendance_tv_summary_total);
        tvSummaryRegular = findViewById(R.id.attendance_tv_summary_regular);
        tvSummaryOvertime = findViewById(R.id.attendance_tv_summary_overtime);
        tvOpenWarning = findViewById(R.id.attendance_tv_open_warning);
        tvHistoryEmpty = findViewById(R.id.attendance_tv_history_empty);
        historyContainer = findViewById(R.id.attendance_history_container);
    }

    /** Chỉ EMPLOYEE có hồ sơ khớp accountId được dùng màn hình này. */
    private boolean loadSession() {
        SharedPreferences prefs = getSharedPreferences(LoginActivity.PREFS, MODE_PRIVATE);
        int id = prefs.getInt(LoginActivity.KEY_ACCOUNT_ID, -1);
        Employee profile = null;
        try {
            Account account = id > 0 ? dbHelper.getAccountById(id) : null;
            if (account != null && Account.ROLE_EMPLOYEE.equals(account.getRole())) {
                profile = dbHelper.getEmployeeByAccountId(id);
            }
        } catch (SQLException e) {
            showError(e);
        }
        if (profile == null) {
            redirectToLogin();
            return false;
        }
        accountId = id;
        employee = profile;
        tvEmployee.setText(getString(R.string.attendance_employee_info,
                profile.getCode(), profile.getFullname()));
        return true;
    }

    private void redirectToLogin() {
        accountId = -1;
        employee = null;
        getSharedPreferences(LoginActivity.PREFS, MODE_PRIVATE).edit().clear().apply();
        Toast.makeText(this, R.string.attendance_session_invalid, Toast.LENGTH_LONG).show();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void refreshAll() {
        refreshToday();
        refreshHistory();
    }

    private void refreshToday() {
        WorkLog today = null;
        try {
            today = dbHelper.getTodayWorkLog(accountId);
        } catch (IllegalArgumentException | IllegalStateException | SecurityException | SQLException e) {
            showError(e);
        }

        String workDate = today != null ? today.getWorkDate() : WorkLogSummary.today();
        tvTodayDate.setText(getString(R.string.attendance_today_date, WorkLogSummary.formatWorkDate(workDate)));

        String empty = getString(R.string.attendance_time_empty);
        if (today == null) {
            tvTodayStatus.setText(R.string.attendance_status_none);
            tvTodayCheckIn.setText(getString(R.string.attendance_check_in_time, empty));
            tvTodayCheckOut.setText(getString(R.string.attendance_check_out_time, empty));
            tvTodayHours.setText(getString(R.string.attendance_total_hours, getString(R.string.attendance_hours_empty)));
            btnCheckIn.setEnabled(true);
            btnCheckOut.setEnabled(false);
            return;
        }

        boolean open = WorkLog.STATUS_CHECKED_IN.equals(today.getStatus());
        boolean completed = WorkLog.STATUS_COMPLETED.equals(today.getStatus());
        tvTodayStatus.setText(statusText(today));
        tvTodayCheckIn.setText(getString(R.string.attendance_check_in_time, valueOrEmpty(today.getCheckInTime())));
        tvTodayCheckOut.setText(getString(R.string.attendance_check_out_time, valueOrEmpty(today.getCheckOutTime())));
        tvTodayHours.setText(getString(R.string.attendance_total_hours, completed
                ? formatHours(today.getTotalHours())
                : getString(R.string.attendance_hours_empty)));
        btnCheckIn.setEnabled(false);
        btnCheckOut.setEnabled(open);
    }

    private void refreshHistory() {
        tvMonth.setText(getString(R.string.attendance_month_label, selectedMonth, selectedYear));

        List<WorkLog> logs = Collections.emptyList();
        try {
            logs = new ArrayList<>(dbHelper.getWorkLogs(accountId, employee.getId(), selectedMonth, selectedYear));
            // Ngày dạng yyyy-MM-dd nên so sánh chuỗi đúng thứ tự thời gian; ngày mới nhất lên đầu.
            Collections.sort(logs, (a, b) -> String.valueOf(b.getWorkDate()).compareTo(String.valueOf(a.getWorkDate())));
        } catch (IllegalArgumentException | IllegalStateException | SecurityException | SQLException e) {
            showError(e);
        }

        historyContainer.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        for (WorkLog log : logs) {
            View item = inflater.inflate(R.layout.item_attendance_worklog, historyContainer, false);
            bindHistoryItem(item, log);
            historyContainer.addView(item);
        }
        tvHistoryEmpty.setVisibility(logs.isEmpty() ? View.VISIBLE : View.GONE);

        WorkLogSummary summary = WorkLogSummary.of(logs);
        tvSummaryShifts.setText(getString(R.string.attendance_summary_shifts, summary.completedShifts));
        tvSummaryTotal.setText(getString(R.string.attendance_summary_total, formatHours(summary.totalHours)));
        tvSummaryRegular.setText(getString(R.string.attendance_summary_regular, formatHours(summary.getRegularHours())));
        tvSummaryOvertime.setText(getString(R.string.attendance_summary_overtime, formatHours(summary.overtimeHours)));
        if (summary.hasOpenShifts()) {
            tvOpenWarning.setText(getString(R.string.attendance_summary_open_warning, summary.openShifts));
            tvOpenWarning.setVisibility(View.VISIBLE);
        } else {
            tvOpenWarning.setVisibility(View.GONE);
        }
    }

    private void bindHistoryItem(View item, WorkLog log) {
        TextView tvDate = item.findViewById(R.id.attendance_item_date);
        TextView tvStatus = item.findViewById(R.id.attendance_item_status);
        TextView tvTimes = item.findViewById(R.id.attendance_item_times);
        TextView tvHours = item.findViewById(R.id.attendance_item_hours);

        boolean completed = WorkLog.STATUS_COMPLETED.equals(log.getStatus());
        tvDate.setText(WorkLogSummary.formatWorkDate(log.getWorkDate()));
        tvStatus.setText(statusText(log));
        tvTimes.setText(getString(R.string.attendance_item_times,
                getString(R.string.attendance_check_in_time, valueOrEmpty(log.getCheckInTime())),
                getString(R.string.attendance_check_out_time, valueOrEmpty(log.getCheckOutTime()))));
        tvHours.setText(getString(R.string.attendance_total_hours, completed
                ? formatHours(log.getTotalHours())
                : getString(R.string.attendance_hours_empty)));
        if (!completed) {
            tvStatus.setTextColor(MaterialColors.getColor(tvStatus, androidx.appcompat.R.attr.colorError));
        }
    }

    private String statusText(WorkLog log) {
        if (WorkLog.STATUS_COMPLETED.equals(log.getStatus())) {
            return getString(R.string.attendance_status_completed);
        }
        if (WorkLog.STATUS_CHECKED_IN.equals(log.getStatus())) {
            // Ca mở của ngày trước không được tự hoàn tất, hiển thị là thiếu giờ ra.
            return WorkLogSummary.today().equals(log.getWorkDate())
                    ? getString(R.string.attendance_status_checked_in)
                    : getString(R.string.attendance_status_missing_checkout);
        }
        return getString(R.string.attendance_status_unknown, String.valueOf(log.getStatus()));
    }

    private void onCheckIn() {
        if (!loadSession()) {
            return;
        }
        btnCheckIn.setEnabled(false);
        try {
            if (dbHelper.getTodayWorkLog(accountId) != null) {
                toast(getString(R.string.attendance_already_checked_in));
            } else {
                long id = dbHelper.checkIn(accountId);
                WorkLog today = id > 0 ? dbHelper.getTodayWorkLog(accountId) : null;
                if (today == null) {
                    toast(getString(R.string.attendance_save_failed));
                } else {
                    toast(getString(R.string.attendance_check_in_success, valueOrEmpty(today.getCheckInTime())));
                }
            }
        } catch (IllegalArgumentException | IllegalStateException | SecurityException | SQLException e) {
            showError(e);
        }
        refreshAll();
    }

    private void onCheckOut() {
        if (!loadSession()) {
            return;
        }
        btnCheckOut.setEnabled(false);
        try {
            WorkLog today = dbHelper.getTodayWorkLog(accountId);
            if (today == null) {
                toast(getString(R.string.attendance_not_checked_in));
            } else if (!WorkLog.STATUS_CHECKED_IN.equals(today.getStatus())) {
                toast(getString(R.string.attendance_already_checked_out));
            } else {
                int rows = dbHelper.checkOut(accountId);
                WorkLog updated = rows > 0 ? dbHelper.getTodayWorkLog(accountId) : null;
                if (updated == null || !WorkLog.STATUS_COMPLETED.equals(updated.getStatus())) {
                    toast(getString(R.string.attendance_save_failed));
                } else {
                    toast(getString(R.string.attendance_check_out_success, formatHours(updated.getTotalHours())));
                }
            }
        } catch (IllegalArgumentException | IllegalStateException | SecurityException | SQLException e) {
            showError(e);
        }
        refreshAll();
    }

    private void shiftMonth(int delta) {
        int index = selectedYear * 12 + (selectedMonth - 1) + delta;
        int year = index / 12;
        if (year < 1) {
            return;
        }
        selectedYear = year;
        selectedMonth = index % 12 + 1;
        if (employee != null) {
            refreshHistory();
        }
    }

    private void pickMonth() {
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            selectedYear = year;
            selectedMonth = month + 1;
            if (employee != null) {
                refreshHistory();
            }
        }, selectedYear, selectedMonth - 1, 1).show();
    }

    private String formatHours(double hours) {
        return getString(R.string.attendance_hours_value, hours);
    }

    private String valueOrEmpty(String time) {
        return time == null || time.isEmpty() ? getString(R.string.attendance_time_empty) : time;
    }

    private void showError(RuntimeException e) {
        if (e instanceof SecurityException) {
            toast(getString(R.string.attendance_error_permission));
            return;
        }
        String message = e.getMessage();
        toast(message == null || message.trim().isEmpty() ? getString(R.string.attendance_error_generic) : message);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
