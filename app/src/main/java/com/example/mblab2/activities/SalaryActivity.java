package com.example.mblab2.activities;

import android.app.DatePickerDialog;
import android.database.SQLException;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
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
import com.example.mblab2.models.Salary;
import com.example.mblab2.models.WorkLog;
import com.example.mblab2.utils.PayrollCalculator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SalaryActivity extends AppCompatActivity {

    private static final String STATE_MONTH = "salary_month";
    private static final String STATE_YEAR = "salary_year";
    private static final double HOURS_TOLERANCE = 1e-6;
    private static final double MONEY_TOLERANCE = 1.0;
    private static final Locale VIETNAMESE = Locale.forLanguageTag("vi-VN");

    private DBHelper dbHelper;
    private int accountId = -1;
    private Account account;
    /** Hồ sơ của Employee đang đăng nhập; null khi là Admin. */
    private Employee ownProfile;
    private final List<Employee> employees = new ArrayList<>();
    private final Map<Integer, Employee> employeeById = new HashMap<>();
    private final List<Salary> salaries = new ArrayList<>();
    private int selectedMonth;
    private int selectedYear;

    private TextView tvTitle;
    private TextView tvSubtitle;
    private TextView tvMonth;

    private View sectionEmployee;
    private TextView tvNoRecord;
    private View groupDetail;
    private TextView tvDetailStatus;
    private TextView tvDetailTotalHours;
    private TextView tvDetailRegularHours;
    private TextView tvDetailOvertimeHours;
    private TextView tvDetailRate;
    private TextView tvDetailAmount;
    private TextView tvDetailWarning;
    private TextView tvCurrentShifts;
    private TextView tvCurrentTotal;
    private TextView tvCurrentOvertime;
    private TextView tvCurrentWarning;

    private View sectionAdmin;
    private Spinner spinnerEmployee;
    private TextView tvNoEmployees;
    private MaterialButton btnCalculate;
    private TextView tvListEmpty;
    private LinearLayout listContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_salary);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.salary_root), (v, insets) -> {
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

        findViewById(R.id.salary_btn_prev_month).setOnClickListener(v -> shiftMonth(-1));
        findViewById(R.id.salary_btn_next_month).setOnClickListener(v -> shiftMonth(1));
        tvMonth.setOnClickListener(v -> pickMonth());
        btnCalculate.setOnClickListener(v -> onCalculate());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Đơn giá/chấm công có thể đã đổi ở màn hình khác nên tải lại mỗi lần quay lại.
        if (loadSession()) {
            refresh();
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
        tvTitle = findViewById(R.id.salary_tv_title);
        tvSubtitle = findViewById(R.id.salary_tv_subtitle);
        tvMonth = findViewById(R.id.salary_tv_month);

        sectionEmployee = findViewById(R.id.salary_section_employee);
        tvNoRecord = findViewById(R.id.salary_tv_no_record);
        groupDetail = findViewById(R.id.salary_group_detail);
        tvDetailStatus = findViewById(R.id.salary_tv_detail_status);
        tvDetailTotalHours = findViewById(R.id.salary_tv_detail_total_hours);
        tvDetailRegularHours = findViewById(R.id.salary_tv_detail_regular_hours);
        tvDetailOvertimeHours = findViewById(R.id.salary_tv_detail_overtime_hours);
        tvDetailRate = findViewById(R.id.salary_tv_detail_rate);
        tvDetailAmount = findViewById(R.id.salary_tv_detail_amount);
        tvDetailWarning = findViewById(R.id.salary_tv_detail_warning);
        tvCurrentShifts = findViewById(R.id.salary_tv_current_shifts);
        tvCurrentTotal = findViewById(R.id.salary_tv_current_total);
        tvCurrentOvertime = findViewById(R.id.salary_tv_current_overtime);
        tvCurrentWarning = findViewById(R.id.salary_tv_current_warning);

        sectionAdmin = findViewById(R.id.salary_section_admin);
        spinnerEmployee = findViewById(R.id.salary_spinner_employee);
        tvNoEmployees = findViewById(R.id.salary_tv_no_employees);
        btnCalculate = findViewById(R.id.salary_btn_calculate);
        tvListEmpty = findViewById(R.id.salary_tv_list_empty);
        listContainer = findViewById(R.id.salary_list_container);
    }

    // region Phiên và quyền

    /** ADMIN vào chế độ quản lý; EMPLOYEE cần hồ sơ khớp accountId. */
    private boolean loadSession() {
        Account current = LoginActivity.getValidSession(this, dbHelper);
        Employee profile = null;
        boolean valid = false;
        try {
            if (current != null && Account.ROLE_ADMIN.equals(current.getRole())) {
                valid = true;
            } else if (current != null && Account.ROLE_EMPLOYEE.equals(current.getRole())) {
                profile = dbHelper.getEmployeeByAccountId(current.getId());
                valid = profile != null;
            }
        } catch (SQLException e) {
            showError(e);
        }
        if (!valid) {
            redirectToLogin();
            return false;
        }

        accountId = current.getId();
        account = current;
        ownProfile = profile;
        boolean admin = isAdmin();
        tvTitle.setText(admin ? R.string.salary_title_admin : R.string.salary_title_employee);
        if (admin) {
            tvSubtitle.setVisibility(View.GONE);
        } else {
            tvSubtitle.setText(getString(R.string.salary_employee_info, profile.getCode(), profile.getFullname()));
            tvSubtitle.setVisibility(View.VISIBLE);
        }
        sectionAdmin.setVisibility(admin ? View.VISIBLE : View.GONE);
        sectionEmployee.setVisibility(admin ? View.GONE : View.VISIBLE);
        return true;
    }

    private boolean isAdmin() {
        return account != null && Account.ROLE_ADMIN.equals(account.getRole());
    }

    /** Kiểm tra lại trong event handler, không chỉ dựa vào việc ẩn nút. */
    private boolean requireAdmin() {
        if (!loadSession()) {
            return false;
        }
        if (!isAdmin()) {
            toast(getString(R.string.salary_error_permission));
            return false;
        }
        return true;
    }

    private void redirectToLogin() {
        accountId = -1;
        account = null;
        ownProfile = null;
        Toast.makeText(this, R.string.salary_session_invalid, Toast.LENGTH_LONG).show();
        LoginActivity.logout(this);
    }

    // endregion

    private void refresh() {
        tvMonth.setText(getString(R.string.salary_month_label, selectedMonth, selectedYear));
        if (isAdmin()) {
            refreshAdmin();
        } else {
            refreshEmployee();
        }
    }

    // region Employee

    private void refreshEmployee() {
        Salary salary = null;
        try {
            for (Salary s : dbHelper.getSalaries(accountId, selectedMonth, selectedYear)) {
                if (s.getEmployeeId() == ownProfile.getId()) {
                    salary = s;
                    break;
                }
            }
        } catch (IllegalArgumentException | IllegalStateException | SecurityException | SQLException e) {
            showError(e);
        }
        WorkLogSummary current = loadCurrentSummary(ownProfile.getId());

        if (salary == null) {
            tvNoRecord.setVisibility(View.VISIBLE);
            groupDetail.setVisibility(View.GONE);
        } else {
            tvNoRecord.setVisibility(View.GONE);
            groupDetail.setVisibility(View.VISIBLE);
            tvDetailStatus.setText(getString(R.string.salary_label_status, statusText(salary)));
            tvDetailTotalHours.setText(getString(R.string.salary_label_total_hours, formatHours(salary.getTotalHours())));
            tvDetailRegularHours.setText(getString(R.string.salary_label_regular_hours,
                    formatHours(salary.getTotalHours() - salary.getOvertimeHours())));
            tvDetailOvertimeHours.setText(getString(R.string.salary_label_overtime_hours, formatHours(salary.getOvertimeHours())));
            tvDetailRate.setText(getString(R.string.salary_label_hourly_rate,
                    getString(R.string.salary_rate_value, formatAmount(ownProfile.getHourlyRate()))));
            tvDetailAmount.setText(getString(R.string.salary_label_total_salary, formatMoney(salary.getTotalSalary())));
            showWarning(tvDetailWarning, buildSyncWarning(salary, current, ownProfile));
        }

        if (current == null) {
            tvCurrentShifts.setText(R.string.salary_current_unavailable);
            tvCurrentTotal.setText("");
            tvCurrentOvertime.setText("");
            showWarning(tvCurrentWarning, null);
        } else {
            tvCurrentShifts.setText(getString(R.string.salary_current_shifts, current.completedShifts));
            tvCurrentTotal.setText(getString(R.string.salary_label_total_hours, formatHours(current.totalHours)));
            tvCurrentOvertime.setText(getString(R.string.salary_label_overtime_hours, formatHours(current.overtimeHours)));
            showWarning(tvCurrentWarning, current.hasOpenShifts()
                    ? getString(R.string.salary_warning_open_shifts, current.openShifts)
                    : null);
        }
    }

    // endregion

    // region Admin

    private void refreshAdmin() {
        int previousEmployeeId = -1;
        Employee previous = selectedEmployee();
        if (previous != null) {
            previousEmployeeId = previous.getId();
        }

        employees.clear();
        employeeById.clear();
        salaries.clear();
        try {
            employees.addAll(dbHelper.getEmployees(accountId, ""));
            salaries.addAll(dbHelper.getSalaries(accountId, selectedMonth, selectedYear));
        } catch (IllegalArgumentException | IllegalStateException | SecurityException | SQLException e) {
            showError(e);
        }

        List<String> labels = new ArrayList<>();
        int selection = 0;
        for (int i = 0; i < employees.size(); i++) {
            Employee e = employees.get(i);
            employeeById.put(e.getId(), e);
            labels.add(employeeLabel(e));
            if (e.getId() == previousEmployeeId) {
                selection = i;
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerEmployee.setAdapter(adapter);
        if (!employees.isEmpty()) {
            spinnerEmployee.setSelection(selection);
        }
        spinnerEmployee.setEnabled(!employees.isEmpty());
        btnCalculate.setEnabled(!employees.isEmpty());
        tvNoEmployees.setVisibility(employees.isEmpty() ? View.VISIBLE : View.GONE);

        listContainer.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        for (Salary salary : salaries) {
            Employee employee = employeeById.get(salary.getEmployeeId());
            WorkLogSummary current = loadCurrentSummary(salary.getEmployeeId());
            View item = inflater.inflate(R.layout.item_salary_record, listContainer, false);
            bindSalaryItem(item, salary, employee, current);
            item.setOnClickListener(v -> showSalaryActions(salary));
            listContainer.addView(item);
        }
        tvListEmpty.setVisibility(salaries.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void bindSalaryItem(View item, Salary salary, Employee employee, WorkLogSummary current) {
        TextView tvEmployee = item.findViewById(R.id.salary_item_employee);
        TextView tvStatus = item.findViewById(R.id.salary_item_status);
        TextView tvHours = item.findViewById(R.id.salary_item_hours);
        TextView tvAmount = item.findViewById(R.id.salary_item_amount);
        TextView tvWarning = item.findViewById(R.id.salary_item_warning);

        tvEmployee.setText(employeeLabel(employee, salary.getEmployeeId()));
        tvStatus.setText(statusText(salary));
        tvHours.setText(getString(R.string.salary_hours_line,
                formatHours(salary.getTotalHours()), formatHours(salary.getOvertimeHours())));
        tvAmount.setText(getString(R.string.salary_label_total_salary, formatMoney(salary.getTotalSalary())));
        showWarning(tvWarning, buildSyncWarning(salary, current, employee));
    }

    private void onCalculate() {
        if (!requireAdmin()) {
            return;
        }
        Employee employee = selectedEmployee();
        if (employee == null) {
            toast(getString(R.string.salary_select_employee));
            return;
        }
        Salary existing = findSalary(employee.getId());
        if (existing != null && Salary.STATUS_PAID.equals(existing.getStatus())) {
            toast(getString(R.string.salary_paid_locked));
            return;
        }
        WorkLogSummary current = loadCurrentSummary(employee.getId());
        if (current != null && current.hasOpenShifts()) {
            toast(getString(R.string.salary_open_shifts_blocked, current.openShifts));
            return;
        }

        btnCalculate.setEnabled(false);
        try {
            Salary saved = dbHelper.calculateSalary(employee.getId(), selectedMonth, selectedYear, accountId);
            if (saved == null) {
                toast(getString(R.string.salary_calculate_failed));
            } else {
                toast(getString(existing == null ? R.string.salary_calculate_created : R.string.salary_calculate_updated,
                        formatMoney(saved.getTotalSalary())));
            }
        } catch (IllegalArgumentException | IllegalStateException | SecurityException | SQLException e) {
            showError(e);
        }
        refresh();
    }

    private void showSalaryActions(Salary salary) {
        if (!requireAdmin()) {
            return;
        }
        Employee employee = employeeById.get(salary.getEmployeeId());
        WorkLogSummary current = loadCurrentSummary(salary.getEmployeeId());
        String label = employeeLabel(employee, salary.getEmployeeId());

        StringBuilder message = new StringBuilder()
                .append(getString(R.string.salary_label_status, statusText(salary))).append('\n')
                .append(getString(R.string.salary_label_total_hours, formatHours(salary.getTotalHours()))).append('\n')
                .append(getString(R.string.salary_label_regular_hours,
                        formatHours(salary.getTotalHours() - salary.getOvertimeHours()))).append('\n')
                .append(getString(R.string.salary_label_overtime_hours, formatHours(salary.getOvertimeHours()))).append('\n');
        if (employee != null) {
            message.append(getString(R.string.salary_label_hourly_rate,
                    getString(R.string.salary_rate_value, formatAmount(employee.getHourlyRate())))).append('\n');
        }
        message.append(getString(R.string.salary_label_total_salary, formatMoney(salary.getTotalSalary())));
        String warning = buildSyncWarning(salary, current, employee);
        if (warning != null) {
            message.append("\n\n").append(warning);
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.salary_dialog_title, label, salary.getMonth(), salary.getYear()))
                .setMessage(message.toString())
                .setNeutralButton(R.string.salary_action_view_worklogs, (d, w) -> showWorkLogs(salary.getEmployeeId()));
        if (Salary.STATUS_DRAFT.equals(salary.getStatus())) {
            builder.setPositiveButton(R.string.salary_action_mark_paid, (d, w) -> confirmMarkPaid(salary))
                    .setNegativeButton(R.string.salary_action_delete_draft, (d, w) -> confirmDeleteDraft(salary));
        } else {
            builder.setPositiveButton(R.string.salary_action_close, null);
        }
        builder.show();
    }

    private void confirmMarkPaid(Salary salary) {
        if (!requireAdmin()) {
            return;
        }
        Employee employee = employeeById.get(salary.getEmployeeId());
        WorkLogSummary current = loadCurrentSummary(salary.getEmployeeId());
        // Không chốt bản nháp cũ hoặc tháng còn ca mở; DB cũng kiểm tra lại điều kiện này.
        if (!Salary.STATUS_DRAFT.equals(salary.getStatus()) || !isUpToDate(salary, current, employee)) {
            toast(getString(R.string.salary_mark_paid_blocked));
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.salary_confirm_paid_title)
                .setMessage(getString(R.string.salary_confirm_paid_message, employeeLabel(employee, salary.getEmployeeId())))
                .setPositiveButton(R.string.salary_action_confirm, (d, w) -> markPaid(salary))
                .setNegativeButton(R.string.salary_action_cancel, null)
                .show();
    }

    private void markPaid(Salary salary) {
        if (!requireAdmin()) {
            return;
        }
        try {
            int rows = dbHelper.markSalaryPaid(salary.getId(), accountId);
            toast(getString(rows > 0 ? R.string.salary_mark_paid_success : R.string.salary_mark_paid_failed));
        } catch (IllegalArgumentException | IllegalStateException | SecurityException | SQLException e) {
            showError(e);
        }
        refresh();
    }

    private void confirmDeleteDraft(Salary salary) {
        if (!requireAdmin()) {
            return;
        }
        if (!Salary.STATUS_DRAFT.equals(salary.getStatus())) {
            toast(getString(R.string.salary_paid_locked));
            return;
        }
        Employee employee = employeeById.get(salary.getEmployeeId());
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.salary_confirm_delete_title)
                .setMessage(getString(R.string.salary_confirm_delete_message, employeeLabel(employee, salary.getEmployeeId())))
                .setPositiveButton(R.string.salary_action_confirm, (d, w) -> deleteDraft(salary))
                .setNegativeButton(R.string.salary_action_cancel, null)
                .show();
    }

    private void deleteDraft(Salary salary) {
        if (!requireAdmin()) {
            return;
        }
        try {
            int rows = dbHelper.deleteDraftSalary(salary.getId(), accountId);
            toast(getString(rows > 0 ? R.string.salary_delete_success : R.string.salary_delete_failed));
        } catch (IllegalArgumentException | IllegalStateException | SecurityException | SQLException e) {
            showError(e);
        }
        refresh();
    }

    private void showWorkLogs(int employeeId) {
        if (!requireAdmin()) {
            return;
        }
        List<WorkLog> logs;
        try {
            logs = new ArrayList<>(dbHelper.getWorkLogs(accountId, employeeId, selectedMonth, selectedYear));
        } catch (IllegalArgumentException | IllegalStateException | SecurityException | SQLException e) {
            showError(e);
            return;
        }
        Collections.sort(logs, (a, b) -> String.valueOf(a.getWorkDate()).compareTo(String.valueOf(b.getWorkDate())));

        StringBuilder message = new StringBuilder();
        if (logs.isEmpty()) {
            message.append(getString(R.string.salary_worklogs_empty));
        } else {
            for (WorkLog log : logs) {
                String date = WorkLogSummary.formatWorkDate(log.getWorkDate());
                if (WorkLog.STATUS_COMPLETED.equals(log.getStatus())) {
                    message.append(getString(R.string.salary_worklog_completed, date,
                            log.getCheckInTime(), log.getCheckOutTime(), formatHours(log.getTotalHours())));
                } else {
                    message.append(getString(R.string.salary_worklog_open, date, log.getCheckInTime()));
                }
                message.append('\n');
            }
            WorkLogSummary summary = WorkLogSummary.of(logs);
            message.append('\n').append(getString(R.string.salary_worklogs_summary,
                    formatHours(summary.totalHours), formatHours(summary.overtimeHours), summary.openShifts));
        }

        Employee employee = employeeById.get(employeeId);
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.salary_worklogs_title, employeeLabel(employee, employeeId),
                        selectedMonth, selectedYear))
                .setMessage(message.toString())
                .setPositiveButton(R.string.salary_action_close, null)
                .show();
    }

    private Employee selectedEmployee() {
        int position = spinnerEmployee.getSelectedItemPosition();
        return position >= 0 && position < employees.size() ? employees.get(position) : null;
    }

    private Salary findSalary(int employeeId) {
        for (Salary salary : salaries) {
            if (salary.getEmployeeId() == employeeId) {
                return salary;
            }
        }
        return null;
    }

    // endregion

    // region So khớp bản lương với chấm công hiện tại

    private WorkLogSummary loadCurrentSummary(int employeeId) {
        try {
            return WorkLogSummary.of(dbHelper.getWorkLogs(accountId, employeeId, selectedMonth, selectedYear));
        } catch (IllegalArgumentException | IllegalStateException | SecurityException | SQLException e) {
            return null;
        }
    }

    /** Bản lương khớp tổng hợp công hiện tại và đơn giá hiện tại theo công thức của PayrollCalculator. */
    private boolean isUpToDate(Salary salary, WorkLogSummary current, Employee employee) {
        if (current == null || employee == null || current.hasOpenShifts()) {
            return false;
        }
        double expected = Math.round(PayrollCalculator.calculateMonthlySalary(
                current.totalHours, current.overtimeHours, employee.getHourlyRate()));
        return Math.abs(salary.getTotalHours() - current.totalHours) < HOURS_TOLERANCE
                && Math.abs(salary.getOvertimeHours() - current.overtimeHours) < HOURS_TOLERANCE
                && Math.abs(salary.getTotalSalary() - expected) < MONEY_TOLERANCE;
    }

    /** Trả về cảnh báo cần hiển thị, hoặc null khi bản lương khớp số liệu hiện tại. */
    private String buildSyncWarning(Salary salary, WorkLogSummary current, Employee employee) {
        if (current == null || employee == null) {
            return getString(R.string.salary_warning_unknown);
        }
        if (isUpToDate(salary, current, employee)) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        if (Salary.STATUS_PAID.equals(salary.getStatus())) {
            parts.add(getString(R.string.salary_warning_paid_changed,
                    formatHours(current.totalHours), formatHours(current.overtimeHours)));
        } else if (!current.hasOpenShifts()) {
            parts.add(getString(R.string.salary_warning_draft_outdated));
        }
        if (current.hasOpenShifts()) {
            parts.add(getString(R.string.salary_warning_open_shifts, current.openShifts));
        }
        return TextUtils.join("\n", parts);
    }

    // endregion

    private void shiftMonth(int delta) {
        if (!loadSession()) {
            return;
        }
        int index = selectedYear * 12 + (selectedMonth - 1) + delta;
        int year = index / 12;
        if (year < 1) {
            return;
        }
        selectedYear = year;
        selectedMonth = index % 12 + 1;
        if (account != null) {
            refresh();
        }
    }

    private void pickMonth() {
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            if (!loadSession()) {
                return;
            }
            selectedYear = year;
            selectedMonth = month + 1;
            if (account != null) {
                refresh();
            }
        }, selectedYear, selectedMonth - 1, 1).show();
    }

    private String employeeLabel(Employee employee) {
        return getString(R.string.salary_employee_info, employee.getCode(), employee.getFullname());
    }

    private String employeeLabel(Employee employee, int employeeId) {
        return employee != null ? employeeLabel(employee) : getString(R.string.salary_admin_unknown_employee, employeeId);
    }

    private String statusText(Salary salary) {
        if (Salary.STATUS_PAID.equals(salary.getStatus())) {
            return getString(R.string.salary_status_paid);
        }
        if (Salary.STATUS_DRAFT.equals(salary.getStatus())) {
            return getString(R.string.salary_status_draft);
        }
        return String.valueOf(salary.getStatus());
    }

    private String formatHours(double hours) {
        return getString(R.string.salary_hours_value, hours);
    }

    /** VND làm tròn đến đồng khi hiển thị. */
    private String formatAmount(double amount) {
        return NumberFormat.getIntegerInstance(VIETNAMESE).format(Math.round(amount));
    }

    private String formatMoney(double amount) {
        return getString(R.string.salary_money_value, formatAmount(amount));
    }

    private void showWarning(TextView view, String warning) {
        if (warning == null || warning.isEmpty()) {
            view.setVisibility(View.GONE);
        } else {
            view.setText(warning);
            view.setVisibility(View.VISIBLE);
        }
    }

    private void showError(RuntimeException e) {
        if (e instanceof SecurityException) {
            toast(getString(R.string.salary_error_permission));
            return;
        }
        String message = e.getMessage();
        toast(message == null || message.trim().isEmpty() ? getString(R.string.salary_error_generic) : message);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
