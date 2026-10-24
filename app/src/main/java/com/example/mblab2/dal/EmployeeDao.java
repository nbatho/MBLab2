package com.example.mblab2.dal;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Patterns;
import com.example.mblab2.models.Account;
import com.example.mblab2.models.Employee;
import java.util.ArrayList;
import java.util.List;

/** Employee CRUD and linked account transactions. Rate changes refresh draft salaries. */
final class EmployeeDao {
    private static final String[] EMPLOYEE_COLUMNS = {
            "id", "code", "fullname", "email", "phone", "hourlyRate", "accountId"};

    private final DBHelper helper;

    EmployeeDao(DBHelper helper) {
        this.helper = helper;
    }

    Employee getEmployeeByAccountId(int accountId) {
        return findEmployeeByAccountId(helper.getReadableDatabase(), accountId);
    }

    List<Employee> getEmployees(int currentAccountId, String keyword) {
        SQLiteDatabase db = helper.getReadableDatabase();
        AccountDao.requireAdmin(db, currentAccountId);
        String search = keyword == null ? "" : keyword.trim();
        String selection = null;
        String[] args = null;
        if (!search.isEmpty()) {
            // Treat % and _ as literal user input instead of widening a search.
            String pattern = "%" + search.replace("\\", "\\\\")
                    .replace("%", "\\%").replace("_", "\\_") + "%";
            selection = "code LIKE ? ESCAPE '\\' OR fullname LIKE ? ESCAPE '\\'"
                    + " OR email LIKE ? ESCAPE '\\' OR phone LIKE ? ESCAPE '\\'";
            args = new String[]{pattern, pattern, pattern, pattern};
        }
        List<Employee> employees = new ArrayList<>();
        try (Cursor cursor = db.query("Employee", EMPLOYEE_COLUMNS, selection, args,
                null, null, "fullname COLLATE NOCASE, id")) {
            while (cursor.moveToNext()) {
                employees.add(readEmployee(cursor));
            }
        }
        return employees;
    }

    long insertEmployee(Employee employee, Account account, int currentAccountId) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            AccountDao.requireAdmin(db, currentAccountId);
            validateEmployee(employee);
            if (employee.getId() != 0 || employee.getAccountId() != 0
                    || account == null || account.getId() != 0) {
                throw new IllegalArgumentException("Nhân viên/tài khoản mới phải có ID bằng 0");
            }
            if (!Account.ROLE_EMPLOYEE.equals(account.getRole())) {
                throw new IllegalArgumentException("Chỉ được tạo tài khoản EMPLOYEE tại đây");
            }
            String username = requireText(account.getUsername(), "Tên đăng nhập");
            requireText(account.getPassword(), "Mật khẩu");
            if (exists(db, "Account", "username = ?", new String[]{username})) {
                throw new IllegalArgumentException("Tên đăng nhập đã tồn tại");
            }
            requireUniqueEmployeeCode(db, employee.getCode().trim(), 0);
            long accountId = AccountDao.insertAccount(db, username, account.getPassword(), Account.ROLE_EMPLOYEE);
            ContentValues values = employeeValues(employee);
            values.put("accountId", accountId);
            long employeeId = db.insertOrThrow("Employee", null, values);
            db.setTransactionSuccessful();
            return employeeId;
        } catch (SQLiteException ex) {
            return -1;
        } finally {
            db.endTransaction();
        }
    }

    int updateEmployee(Employee employee, int currentAccountId) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            AccountDao.requireAdmin(db, currentAccountId);
            validateEmployee(employee);
            Employee previous = requireEmployee(db, employee.getId());
            if (previous.getAccountId() != employee.getAccountId()) {
                throw new IllegalArgumentException("Không được đổi tài khoản của nhân viên");
            }
            requireUniqueEmployeeCode(db, employee.getCode().trim(), employee.getId());
            int changed = db.update("Employee", employeeValues(employee), "id = ?",
                    new String[]{String.valueOf(employee.getId())});
            if (Double.compare(previous.getHourlyRate(), employee.getHourlyRate()) != 0) {
                SalaryDao.refreshDraftSalaries(db, employee.getId());
            }
            db.setTransactionSuccessful();
            return changed;
        } catch (SQLiteException ex) {
            return 0;
        } finally {
            db.endTransaction();
        }
    }

    int deleteEmployee(int employeeId, int currentAccountId) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            AccountDao.requireAdmin(db, currentAccountId);
            Employee employee = findEmployee(db, employeeId);
            if (employee == null) {
                db.setTransactionSuccessful();
                return 0;
            }
            String[] args = {String.valueOf(employeeId)};
            if (exists(db, "WorkLog", "employeeId = ?", args)
                    || exists(db, "Salary", "employeeId = ?", args)) {
                throw new IllegalStateException("Không thể xóa nhân viên đã có công hoặc lương");
            }
            Account account = AccountDao.requireAccount(db, employee.getAccountId());
            if (!Account.ROLE_EMPLOYEE.equals(account.getRole())) {
                throw new IllegalStateException("Hồ sơ không liên kết tài khoản EMPLOYEE");
            }
            int changed = db.delete("Employee", "id = ?", args);
            if (db.delete("Account", "id = ? AND role = ?", new String[]{
                    String.valueOf(account.getId()), Account.ROLE_EMPLOYEE}) != 1) {
                throw new IllegalStateException("Không thể xóa tài khoản liên kết");
            }
            db.setTransactionSuccessful();
            return changed;
        } catch (SQLiteException ex) {
            return 0;
        } finally {
            db.endTransaction();
        }
    }

    static Employee findEmployeeByAccountId(SQLiteDatabase db, int accountId) {
        try (Cursor cursor = db.query("Employee", EMPLOYEE_COLUMNS, "accountId = ?",
                new String[]{String.valueOf(accountId)}, null, null, null)) {
            return cursor.moveToFirst() ? readEmployee(cursor) : null;
        }
    }

    static Employee findEmployee(SQLiteDatabase db, int employeeId) {
        try (Cursor cursor = db.query("Employee", EMPLOYEE_COLUMNS, "id = ?",
                new String[]{String.valueOf(employeeId)}, null, null, null)) {
            return cursor.moveToFirst() ? readEmployee(cursor) : null;
        }
    }

    static Employee requireEmployee(SQLiteDatabase db, int employeeId) {
        Employee employee = findEmployee(db, employeeId);
        if (employee == null) {
            throw new IllegalArgumentException("Không tìm thấy nhân viên");
        }
        return employee;
    }

    private static void validateEmployee(Employee employee) {
        if (employee == null) {
            throw new IllegalArgumentException("Thiếu thông tin nhân viên");
        }
        requireText(employee.getCode(), "Mã nhân viên");
        requireText(employee.getFullname(), "Họ tên");
        String email = requireText(employee.getEmail(), "Email");
        String phone = requireText(employee.getPhone(), "Điện thoại");
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            throw new IllegalArgumentException("Email không hợp lệ");
        }
        if (!phone.matches("\\+?[0-9]{7,15}")) {
            throw new IllegalArgumentException("Điện thoại cần 7–15 chữ số, có thể bắt đầu bằng +");
        }
        if (!Double.isFinite(employee.getHourlyRate()) || employee.getHourlyRate() <= 0.0) {
            throw new IllegalArgumentException("Đơn giá phải là số hữu hạn lớn hơn 0");
        }
    }

    private static String requireText(String text, String name) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " không được để trống");
        }
        return text.trim();
    }

    private static void requireUniqueEmployeeCode(SQLiteDatabase db, String code, int exceptId) {
        if (exists(db, "Employee", "code = ? AND id <> ?",
                new String[]{code, String.valueOf(exceptId)})) {
            throw new IllegalArgumentException("Mã nhân viên đã tồn tại");
        }
    }

    static boolean exists(SQLiteDatabase db, String table, String where, String[] args) {
        try (Cursor cursor = db.query(table, new String[]{"id"}, where, args,
                null, null, null, "1")) {
            return cursor.moveToFirst();
        }
    }

    static ContentValues employeeValues(Employee employee) {
        ContentValues values = new ContentValues();
        values.put("code", employee.getCode().trim());
        values.put("fullname", employee.getFullname().trim());
        values.put("email", employee.getEmail().trim());
        values.put("phone", employee.getPhone().trim());
        values.put("hourlyRate", employee.getHourlyRate());
        return values;
    }

    private static Employee readEmployee(Cursor cursor) {
        return new Employee(cursor.getInt(0), cursor.getString(1), cursor.getString(2),
                cursor.getString(3), cursor.getString(4), cursor.getDouble(5), cursor.getInt(6));
    }
}
