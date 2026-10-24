package com.example.mblab2.dal;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import com.example.mblab2.models.Account;
import com.example.mblab2.models.Employee;
import com.example.mblab2.models.WorkLog;
import java.util.Calendar;
import java.util.Date;

/** Demo accounts and previous-month work logs, used only when the database is created. */
final class DatabaseSeeder {
    private DatabaseSeeder() { }

    static void seed(SQLiteDatabase db, Date now) {
        AccountDao.insertAccount(db, "admin", "admin123", Account.ROLE_ADMIN);
        long firstAccount = AccountDao.insertAccount(db, "employee1", "employee123", Account.ROLE_EMPLOYEE);
        long secondAccount = AccountDao.insertAccount(db, "employee2", "employee123", Account.ROLE_EMPLOYEE);
        int firstEmployee = seedEmployee(db, "NV001", "Nguyễn Văn An", "an@example.com",
                "0901000001", 50_000.0, firstAccount);
        int secondEmployee = seedEmployee(db, "NV002", "Trần Thị Bình", "binh@example.com",
                "0901000002", 60_000.0, secondAccount);
        Calendar month = Calendar.getInstance();
        month.setTime(now);
        month.set(Calendar.DAY_OF_MONTH, 1);
        month.add(Calendar.MONTH, -1);
        String prefix = DateTimeUtils.format("yyyy-MM", month.getTime());
        seedWorkLog(db, firstEmployee, prefix + "-01", "08:00:00", "16:00:00");
        seedWorkLog(db, firstEmployee, prefix + "-02", "08:00:00", "18:00:00");
        seedWorkLog(db, secondEmployee, prefix + "-01", "08:00:00", "17:00:00");
        seedWorkLog(db, secondEmployee, prefix + "-02", "08:00:00", "15:30:00");
        int monthNumber = month.get(Calendar.MONTH) + 1;
        int year = month.get(Calendar.YEAR);
        db.insertOrThrow("Salary", null, SalaryDao.salaryValues(SalaryDao.computeSalary(db,
                EmployeeDao.requireEmployee(db, firstEmployee), monthNumber, year)));
        db.insertOrThrow("Salary", null, SalaryDao.salaryValues(SalaryDao.computeSalary(db,
                EmployeeDao.requireEmployee(db, secondEmployee), monthNumber, year)));
    }

    private static int seedEmployee(SQLiteDatabase db, String code, String name, String email,
                                    String phone, double rate, long accountId) {
        ContentValues values = EmployeeDao.employeeValues(new Employee(0, code, name, email, phone, rate, 0));
        values.put("accountId", accountId);
        return (int) db.insertOrThrow("Employee", null, values);
    }

    private static void seedWorkLog(SQLiteDatabase db, int employeeId, String date,
                                    String checkInTime, String checkOutTime) {
        ContentValues values = new ContentValues();
        values.put("employeeId", employeeId);
        values.put("workDate", date);
        values.put("checkInTime", checkInTime);
        values.put("checkOutTime", checkOutTime);
        values.put("totalHours", (DateTimeUtils.parseDateTime(date, checkOutTime)
                - DateTimeUtils.parseDateTime(date, checkInTime)) / 3_600_000.0);
        values.put("status", WorkLog.STATUS_COMPLETED);
        db.insertOrThrow("WorkLog", null, values);
    }
}
