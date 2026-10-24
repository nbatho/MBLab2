package com.example.mblab2.dal;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.mblab2.models.Account;
import com.example.mblab2.models.Employee;

/** Account queries, login, and role checks shared by data operations. */
final class AccountDao {
    private static final String[] ACCOUNT_COLUMNS = {"id", "username", "password", "role"};

    private final DBHelper helper;

    AccountDao(DBHelper helper) {
        this.helper = helper;
    }

    Account login(String username, String password) {
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            return null;
        }
        SQLiteDatabase db = helper.getReadableDatabase();
        Account account;
        try (Cursor cursor = db.query("Account", ACCOUNT_COLUMNS, "username = ?",
                new String[]{username.trim()}, null, null, null)) {
            account = cursor.moveToFirst() ? readAccount(cursor) : null;
        }
        if (account == null || !PasswordHasher.verifyPassword(password, account.getPassword())) {
            return null;
        }
        if (Account.ROLE_EMPLOYEE.equals(account.getRole())
                && EmployeeDao.findEmployeeByAccountId(db, account.getId()) == null) {
            return null;
        }
        return account;
    }

    Account getAccountById(int accountId) {
        return findAccount(helper.getReadableDatabase(), accountId);
    }

    static Account findAccount(SQLiteDatabase db, int accountId) {
        try (Cursor cursor = db.query("Account", ACCOUNT_COLUMNS, "id = ?",
                new String[]{String.valueOf(accountId)}, null, null, null)) {
            return cursor.moveToFirst() ? readAccount(cursor) : null;
        }
    }

    static Account requireAccount(SQLiteDatabase db, int accountId) {
        Account account = findAccount(db, accountId);
        if (account == null) {
            throw new SecurityException("Phiên đăng nhập không còn hợp lệ");
        }
        return account;
    }

    static void requireAdmin(SQLiteDatabase db, int currentAccountId) {
        if (!Account.ROLE_ADMIN.equals(requireAccount(db, currentAccountId).getRole())) {
            throw new SecurityException("Chức năng chỉ dành cho Admin");
        }
    }

    static Employee requireEmployeeAccount(SQLiteDatabase db, int currentAccountId) {
        if (!Account.ROLE_EMPLOYEE.equals(requireAccount(db, currentAccountId).getRole())) {
            throw new SecurityException("Chức năng chỉ dành cho Employee");
        }
        Employee employee = EmployeeDao.findEmployeeByAccountId(db, currentAccountId);
        if (employee == null) {
            throw new IllegalStateException("Tài khoản chưa có hồ sơ nhân viên hợp lệ");
        }
        return employee;
    }

    static void requireEmployeeReadAccess(SQLiteDatabase db, int accountId, int employeeId) {
        Account account = requireAccount(db, accountId);
        if (!Account.ROLE_ADMIN.equals(account.getRole())
                && requireEmployeeAccount(db, accountId).getId() != employeeId) {
            throw new SecurityException("Không được xem công của nhân viên khác");
        }
        EmployeeDao.requireEmployee(db, employeeId);
    }

    private static Account readAccount(Cursor cursor) {
        return new Account(cursor.getInt(0), cursor.getString(1), cursor.getString(2), cursor.getString(3));
    }

    static long insertAccount(SQLiteDatabase db, String username, String password, String role) {
        ContentValues values = new ContentValues();
        values.put("username", username);
        values.put("password", PasswordHasher.hashPassword(password));
        values.put("role", role);
        return db.insertOrThrow("Account", null, values);
    }
}
