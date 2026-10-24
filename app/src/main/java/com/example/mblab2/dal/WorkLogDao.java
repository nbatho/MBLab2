package com.example.mblab2.dal;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import com.example.mblab2.models.Employee;
import com.example.mblab2.models.WorkLog;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Daily check-in/out and attendance queries. Checkout and salary refresh share one transaction. */
final class WorkLogDao {
    private static final String[] WORK_LOG_COLUMNS = {
            "id", "employeeId", "workDate", "checkInTime", "checkOutTime", "totalHours", "status"};

    private final DBHelper helper;

    WorkLogDao(DBHelper helper) {
        this.helper = helper;
    }

    WorkLog getTodayWorkLog(int currentAccountId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Employee employee = AccountDao.requireEmployeeAccount(db, currentAccountId);
        return findWorkLog(db, employee.getId(), DateTimeUtils.format("yyyy-MM-dd", helper.now()));
    }

    long checkIn(int currentAccountId) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            Employee employee = AccountDao.requireEmployeeAccount(db, currentAccountId);
            Date now = helper.now();
            String workDate = DateTimeUtils.format("yyyy-MM-dd", now);
            if (findWorkLog(db, employee.getId(), workDate) != null) {
                throw new IllegalStateException("Hôm nay đã check-in");
            }
            ContentValues values = new ContentValues();
            values.put("employeeId", employee.getId());
            values.put("workDate", workDate);
            values.put("checkInTime", DateTimeUtils.format("HH:mm:ss", now));
            values.putNull("checkOutTime");
            values.put("totalHours", 0.0);
            values.put("status", WorkLog.STATUS_CHECKED_IN);
            long id = db.insertOrThrow("WorkLog", null, values);
            db.setTransactionSuccessful();
            return id;
        } catch (SQLiteException ex) {
            return -1;
        } finally {
            db.endTransaction();
        }
    }

    int checkOut(int currentAccountId) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            Employee employee = AccountDao.requireEmployeeAccount(db, currentAccountId);
            Date now = helper.now();
            String workDate = DateTimeUtils.format("yyyy-MM-dd", now);
            WorkLog workLog = findWorkLog(db, employee.getId(), workDate);
            if (workLog == null || !WorkLog.STATUS_CHECKED_IN.equals(workLog.getStatus())) {
                throw new IllegalStateException("Không có ca đang mở hôm nay để check-out");
            }
            String checkOutTime = DateTimeUtils.format("HH:mm:ss", now);
            long checkInMillis = DateTimeUtils.parseDateTime(workDate, workLog.getCheckInTime());
            long checkOutMillis = DateTimeUtils.parseDateTime(workDate, checkOutTime);
            if (checkOutMillis <= checkInMillis) {
                throw new IllegalStateException("Giờ ra phải lớn hơn giờ vào trong cùng ngày");
            }
            ContentValues values = new ContentValues();
            values.put("checkOutTime", checkOutTime);
            values.put("totalHours", (checkOutMillis - checkInMillis) / 3_600_000.0);
            values.put("status", WorkLog.STATUS_COMPLETED);
            int changed = db.update("WorkLog", values,
                    "id = ? AND employeeId = ? AND status = ?", new String[]{
                            String.valueOf(workLog.getId()), String.valueOf(employee.getId()),
                            WorkLog.STATUS_CHECKED_IN});
            if (changed != 1) {
                throw new IllegalStateException("Ca làm đã thay đổi; hãy tải lại dữ liệu");
            }
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(now);
            SalaryDao.refreshDraftSalary(db, employee.getId(), calendar.get(Calendar.MONTH) + 1,
                    calendar.get(Calendar.YEAR));
            db.setTransactionSuccessful();
            return changed;
        } catch (SQLiteException ex) {
            return 0;
        } finally {
            db.endTransaction();
        }
    }

    List<WorkLog> getWorkLogs(int currentAccountId, int employeeId, int month, int year) {
        SQLiteDatabase db = helper.getReadableDatabase();
        AccountDao.requireEmployeeReadAccess(db, currentAccountId, employeeId);
        DateTimeUtils.validateMonth(month, year);
        return loadWorkLogs(db, employeeId, month, year);
    }

    static List<WorkLog> loadWorkLogs(SQLiteDatabase db, int employeeId, int month, int year) {
        String prefix = String.format(Locale.US, "%04d-%02d-", year, month);
        List<WorkLog> logs = new ArrayList<>();
        try (Cursor cursor = db.query("WorkLog", WORK_LOG_COLUMNS,
                "employeeId = ? AND workDate LIKE ?",
                new String[]{String.valueOf(employeeId), prefix + "%"},
                null, null, "workDate DESC, id DESC")) {
            while (cursor.moveToNext()) {
                logs.add(readWorkLog(cursor));
            }
        }
        return logs;
    }

    private static WorkLog findWorkLog(SQLiteDatabase db, int employeeId, String workDate) {
        try (Cursor cursor = db.query("WorkLog", WORK_LOG_COLUMNS,
                "employeeId = ? AND workDate = ?", new String[]{String.valueOf(employeeId), workDate},
                null, null, null)) {
            return cursor.moveToFirst() ? readWorkLog(cursor) : null;
        }
    }

    private static WorkLog readWorkLog(Cursor cursor) {
        return new WorkLog(cursor.getInt(0), cursor.getInt(1), cursor.getString(2),
                cursor.getString(3), cursor.getString(4), cursor.getDouble(5), cursor.getString(6));
    }
}
