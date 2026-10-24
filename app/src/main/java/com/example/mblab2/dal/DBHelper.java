package com.example.mblab2.dal;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.mblab2.models.Account;
import com.example.mblab2.models.Employee;
import com.example.mblab2.models.Salary;
import com.example.mblab2.models.WorkLog;

import java.util.Date;
import java.util.List;

/**
 * Database lifecycle and compatibility API for Activities.
 * Each function delegates to its DAO; multi-step transactions stay inside the DAO.
 * Pass the current Account.id to scoped operations. Invalid inputs, permissions,
 * and state transitions retain their existing exceptions and storage result values.
 */
public class DBHelper extends SQLiteOpenHelper {
    public static final String DB_NAME = "mblab2.db";
    public static final int DB_VERSION = 1;

    interface TimeProvider { long nowMillis(); }

    private final TimeProvider timeProvider;
    private final AccountDao accountDao;
    private final EmployeeDao employeeDao;
    private final WorkLogDao workLogDao;
    private final SalaryDao salaryDao;

    public DBHelper(Context context) {
        this(context, DB_NAME, System::currentTimeMillis);
    }

    // Isolated database and clock for tests; production always uses the device clock.
    DBHelper(Context context, String databaseName, TimeProvider timeProvider) {
        super(context.getApplicationContext(), databaseName, null, DB_VERSION);
        if (timeProvider == null) {
            throw new IllegalArgumentException("Thiếu đồng hồ hệ thống");
        }
        this.timeProvider = timeProvider;
        accountDao = new AccountDao(this);
        employeeDao = new EmployeeDao(this);
        workLogDao = new WorkLogDao(this);
        salaryDao = new SalaryDao(this);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        DatabaseSchema.create(db);
        DatabaseSeeder.seed(db, now());
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1 is the first schema. Add explicit migrations before increasing DB_VERSION.
        throw new IllegalStateException("Chưa có migration từ phiên bản "
                + oldVersion + " lên " + newVersion + "; giữ nguyên dữ liệu hiện có");
    }

    public Account login(String username, String password) {
        return accountDao.login(username, password);
    }

    public Account getAccountById(int accountId) {
        return accountDao.getAccountById(accountId);
    }

    public Employee getEmployeeByAccountId(int accountId) {
        return employeeDao.getEmployeeByAccountId(accountId);
    }

    public List<Employee> getEmployees(int currentAccountId, String keyword) {
        return employeeDao.getEmployees(currentAccountId, keyword);
    }

    public long insertEmployee(Employee employee, Account account, int currentAccountId) {
        return employeeDao.insertEmployee(employee, account, currentAccountId);
    }

    public int updateEmployee(Employee employee, int currentAccountId) {
        return employeeDao.updateEmployee(employee, currentAccountId);
    }

    public int deleteEmployee(int employeeId, int currentAccountId) {
        return employeeDao.deleteEmployee(employeeId, currentAccountId);
    }

    public WorkLog getTodayWorkLog(int currentAccountId) {
        return workLogDao.getTodayWorkLog(currentAccountId);
    }

    public long checkIn(int currentAccountId) {
        return workLogDao.checkIn(currentAccountId);
    }

    public int checkOut(int currentAccountId) {
        return workLogDao.checkOut(currentAccountId);
    }

    public List<WorkLog> getWorkLogs(int currentAccountId, int employeeId, int month, int year) {
        return workLogDao.getWorkLogs(currentAccountId, employeeId, month, year);
    }

    public List<Salary> getSalaries(int currentAccountId, int month, int year) {
        return salaryDao.getSalaries(currentAccountId, month, year);
    }

    public Salary calculateSalary(int employeeId, int month, int year, int currentAccountId) {
        return salaryDao.calculateSalary(employeeId, month, year, currentAccountId);
    }

    public int markSalaryPaid(int salaryId, int currentAccountId) {
        return salaryDao.markSalaryPaid(salaryId, currentAccountId);
    }

    public int deleteDraftSalary(int salaryId, int currentAccountId) {
        return salaryDao.deleteDraftSalary(salaryId, currentAccountId);
    }

    Date now() {
        return new Date(timeProvider.nowMillis());
    }
}
