package com.example.mblab2.dal;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import com.example.mblab2.models.Account;
import com.example.mblab2.models.Employee;
import com.example.mblab2.models.Salary;
import com.example.mblab2.models.WorkLog;
import com.example.mblab2.utils.PayrollCalculator;
import java.util.ArrayList;
import java.util.List;

/** Monthly salary calculation, draft refresh, and payment state transitions. */
final class SalaryDao {
    private static final String[] SALARY_COLUMNS = {
            "id", "employeeId", "month", "year", "totalHours", "overtimeHours", "totalSalary", "status"};

    private final DBHelper helper;

    SalaryDao(DBHelper helper) {
        this.helper = helper;
    }

    List<Salary> getSalaries(int currentAccountId, int month, int year) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Account account = AccountDao.requireAccount(db, currentAccountId);
        DateTimeUtils.validateMonth(month, year);
        String selection = "month = ? AND year = ?";
        String[] args;
        if (Account.ROLE_ADMIN.equals(account.getRole())) {
            args = new String[]{String.valueOf(month), String.valueOf(year)};
        } else {
            Employee employee = AccountDao.requireEmployeeAccount(db, currentAccountId);
            selection += " AND employeeId = ?";
            args = new String[]{String.valueOf(month), String.valueOf(year),
                    String.valueOf(employee.getId())};
        }
        List<Salary> salaries = new ArrayList<>();
        try (Cursor cursor = db.query("Salary", SALARY_COLUMNS, selection, args,
                null, null, "employeeId, id")) {
            while (cursor.moveToNext()) {
                salaries.add(readSalary(cursor));
            }
        }
        return salaries;
    }

    Salary calculateSalary(int employeeId, int month, int year, int currentAccountId) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            AccountDao.requireAdmin(db, currentAccountId);
            DateTimeUtils.validateMonth(month, year);
            Employee employee = EmployeeDao.requireEmployee(db, employeeId);
            Salary existing = findSalary(db, employeeId, month, year);
            if (existing != null && Salary.STATUS_PAID.equals(existing.getStatus())) {
                throw new IllegalStateException("Không được tính lại lương đã thanh toán");
            }
            Salary computed = computeSalary(db, employee, month, year);
            ContentValues values = salaryValues(computed);
            if (existing == null) {
                db.insertOrThrow("Salary", null, values);
            } else {
                if (db.update("Salary", values, "id = ? AND status = ?", new String[]{
                        String.valueOf(existing.getId()), Salary.STATUS_DRAFT}) != 1) {
                    throw new IllegalStateException("Bản nháp lương đã thay đổi");
                }
            }
            Salary result = findSalary(db, employeeId, month, year);
            db.setTransactionSuccessful();
            return result;
        } catch (SQLiteException ex) {
            return null;
        } finally {
            db.endTransaction();
        }
    }

    int markSalaryPaid(int salaryId, int currentAccountId) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            AccountDao.requireAdmin(db, currentAccountId);
            Salary salary = requireSalary(db, salaryId);
            if (!Salary.STATUS_DRAFT.equals(salary.getStatus())) {
                throw new IllegalStateException("Chỉ bản nháp mới được chuyển thanh toán");
            }
            Salary computed = computeSalary(db, EmployeeDao.requireEmployee(db, salary.getEmployeeId()),
                    salary.getMonth(), salary.getYear());
            if (!sameTotals(salary, computed)) {
                throw new IllegalStateException("Bản nháp đã cũ; hãy tính lại lương trước khi chốt");
            }
            ContentValues values = new ContentValues();
            values.put("status", Salary.STATUS_PAID);
            int changed = db.update("Salary", values, "id = ? AND status = ?",
                    new String[]{String.valueOf(salaryId), Salary.STATUS_DRAFT});
            db.setTransactionSuccessful();
            return changed;
        } catch (SQLiteException ex) {
            return 0;
        } finally {
            db.endTransaction();
        }
    }

    int deleteDraftSalary(int salaryId, int currentAccountId) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            AccountDao.requireAdmin(db, currentAccountId);
            Salary salary = findSalaryById(db, salaryId);
            if (salary == null) {
                db.setTransactionSuccessful();
                return 0;
            }
            if (!Salary.STATUS_DRAFT.equals(salary.getStatus())) {
                throw new IllegalStateException("Không được xóa lương đã thanh toán");
            }
            int changed = db.delete("Salary", "id = ? AND status = ?",
                    new String[]{String.valueOf(salaryId), Salary.STATUS_DRAFT});
            db.setTransactionSuccessful();
            return changed;
        } catch (SQLiteException ex) {
            return 0;
        } finally {
            db.endTransaction();
        }
    }

    static Salary computeSalary(SQLiteDatabase db, Employee employee, int month, int year) {
        double total = 0.0;
        double overtime = 0.0;
        for (WorkLog log : WorkLogDao.loadWorkLogs(db, employee.getId(), month, year)) {
            if (!WorkLog.STATUS_COMPLETED.equals(log.getStatus()) || log.getCheckOutTime() == null) {
                throw new IllegalStateException("Tháng được chọn còn ca chưa hoàn tất");
            }
            long start = DateTimeUtils.parseDateTime(log.getWorkDate(), log.getCheckInTime());
            long end = DateTimeUtils.parseDateTime(log.getWorkDate(), log.getCheckOutTime());
            double hours = (end - start) / 3_600_000.0;
            if (hours <= 0.0 || !Double.isFinite(log.getTotalHours())
                    || Math.abs(hours - log.getTotalHours()) > 1e-9) {
                throw new IllegalStateException("Dữ liệu giờ công không hợp lệ");
            }
            total += hours;
            overtime += PayrollCalculator.calculateDailyOvertime(hours);
        }
        return new Salary(0, employee.getId(), month, year, total, overtime,
                PayrollCalculator.calculateMonthlySalary(total, overtime, employee.getHourlyRate()),
                Salary.STATUS_DRAFT);
    }

    static void refreshDraftSalaries(SQLiteDatabase db, int employeeId) {
        List<Salary> drafts = new ArrayList<>();
        try (Cursor cursor = db.query("Salary", SALARY_COLUMNS,
                "employeeId = ? AND status = ?", new String[]{String.valueOf(employeeId),
                        Salary.STATUS_DRAFT}, null, null, null)) {
            while (cursor.moveToNext()) {
                drafts.add(readSalary(cursor));
            }
        }
        for (Salary draft : drafts) {
            refreshDraftSalary(db, employeeId, draft.getMonth(), draft.getYear());
        }
    }

    static void refreshDraftSalary(SQLiteDatabase db, int employeeId, int month, int year) {
        Salary draft = findSalary(db, employeeId, month, year);
        if (draft == null || !Salary.STATUS_DRAFT.equals(draft.getStatus())) {
            return;
        }
        // The UI can detect this stale draft by querying the month's CHECKED_IN records.
        for (WorkLog log : WorkLogDao.loadWorkLogs(db, employeeId, month, year)) {
            if (!WorkLog.STATUS_COMPLETED.equals(log.getStatus())) {
                return;
            }
        }
        Salary computed = computeSalary(db, EmployeeDao.requireEmployee(db, employeeId), month, year);
        if (db.update("Salary", salaryValues(computed), "id = ? AND status = ?",
                new String[]{String.valueOf(draft.getId()), Salary.STATUS_DRAFT}) != 1) {
            throw new IllegalStateException("Không cập nhật được bản nháp lương");
        }
    }

    private static Salary findSalary(SQLiteDatabase db, int employeeId, int month, int year) {
        try (Cursor cursor = db.query("Salary", SALARY_COLUMNS,
                "employeeId = ? AND month = ? AND year = ?",
                new String[]{String.valueOf(employeeId), String.valueOf(month), String.valueOf(year)},
                null, null, null)) {
            return cursor.moveToFirst() ? readSalary(cursor) : null;
        }
    }

    private static Salary findSalaryById(SQLiteDatabase db, int salaryId) {
        try (Cursor cursor = db.query("Salary", SALARY_COLUMNS, "id = ?",
                new String[]{String.valueOf(salaryId)}, null, null, null)) {
            return cursor.moveToFirst() ? readSalary(cursor) : null;
        }
    }

    private static Salary requireSalary(SQLiteDatabase db, int salaryId) {
        Salary salary = findSalaryById(db, salaryId);
        if (salary == null) {
            throw new IllegalArgumentException("Không tìm thấy bản ghi lương");
        }
        return salary;
    }

    static ContentValues salaryValues(Salary salary) {
        ContentValues values = new ContentValues();
        values.put("employeeId", salary.getEmployeeId());
        values.put("month", salary.getMonth());
        values.put("year", salary.getYear());
        values.put("totalHours", salary.getTotalHours());
        values.put("overtimeHours", salary.getOvertimeHours());
        values.put("totalSalary", salary.getTotalSalary());
        values.put("status", salary.getStatus());
        return values;
    }

    private static boolean sameTotals(Salary left, Salary right) {
        return Math.abs(left.getTotalHours() - right.getTotalHours()) < 1e-9
                && Math.abs(left.getOvertimeHours() - right.getOvertimeHours()) < 1e-9
                && Double.compare(left.getTotalSalary(), right.getTotalSalary()) == 0;
    }

    private static Salary readSalary(Cursor cursor) {
        return new Salary(cursor.getInt(0), cursor.getInt(1), cursor.getInt(2), cursor.getInt(3),
                cursor.getDouble(4), cursor.getDouble(5), cursor.getDouble(6), cursor.getString(7));
    }
}
