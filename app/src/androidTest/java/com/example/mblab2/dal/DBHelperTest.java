package com.example.mblab2.dal;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.mblab2.models.Account;
import com.example.mblab2.models.Employee;
import com.example.mblab2.models.Salary;
import com.example.mblab2.models.WorkLog;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.Assert.*;

/** Uses real Android SQLite and an isolated DB; never changes the app's mblab2.db. */
@RunWith(AndroidJUnit4.class)
public class DBHelperTest {
    private Context context;
    private String databaseName;
    private DBHelper helper;
    private long timeMillis;
    private int adminId;
    private int employeeAccountId;
    private int otherAccountId;
    private Employee employee;
    private Employee other;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        databaseName = "data-test-" + UUID.randomUUID() + ".db";
        setTime("2026-10-24 08:00:00");
        helper = new DBHelper(context, databaseName, () -> timeMillis);
        adminId = helper.login("admin", "admin123").getId();
        employeeAccountId = helper.login("employee1", "employee123").getId();
        otherAccountId = helper.login("employee2", "employee123").getId();
        employee = helper.getEmployeeByAccountId(employeeAccountId);
        other = helper.getEmployeeByAccountId(otherAccountId);
    }

    @After
    public void tearDown() {
        if (helper != null) {
            helper.close();
        }
        if (databaseName != null) {
            context.deleteDatabase(databaseName);
        }
    }

    @Test
    public void schemaMatchesAllFourTablesAndEnablesForeignKeys() {
        assertColumns("Account", "id", "username", "password", "role");
        assertColumns("Employee", "id", "code", "fullname", "email", "phone", "hourlyRate", "accountId");
        assertColumns("WorkLog", "id", "employeeId", "workDate", "checkInTime", "checkOutTime",
                "totalHours", "status");
        assertColumns("Salary", "id", "employeeId", "month", "year", "totalHours", "overtimeHours",
                "totalSalary", "status");
        try (Cursor cursor = helper.getReadableDatabase().rawQuery("PRAGMA foreign_keys", null)) {
            assertTrue(cursor.moveToFirst());
            assertEquals(1, cursor.getInt(0));
        }
        assertNotEquals(employeeAccountId, employee.getId());
    }

    @Test
    public void loginValidatesCredentialsAndStoresSaltedHashes() {
        assertEquals(Account.ROLE_ADMIN, helper.getAccountById(adminId).getRole());
        assertEquals(Account.ROLE_EMPLOYEE, helper.getAccountById(employeeAccountId).getRole());
        assertNull(helper.login("admin", "wrong"));
        assertNull(helper.login("' OR 1=1 --", "admin123"));
        assertNull(helper.login(" ", ""));
        assertNull(helper.login(null, null));
        assertNull(helper.getAccountById(-1));
        assertNull(helper.getEmployeeByAccountId(adminId));
        String hash = helper.getAccountById(employeeAccountId).getPassword();
        assertTrue(hash.startsWith("pbkdf2-sha1$"));
        assertNotEquals("employee123", hash);
        assertNotEquals(hash, helper.getAccountById(otherAccountId).getPassword());
    }

    @Test
    public void seedPersistsWithoutDuplicationAcrossReopen() {
        assertEquals(3, count("Account"));
        assertEquals(2, count("Employee"));
        assertEquals(4, count("WorkLog"));
        assertEquals(2, count("Salary"));
        String hash = helper.getAccountById(employeeAccountId).getPassword();
        helper.close();
        helper = new DBHelper(context, databaseName, () -> timeMillis);
        assertEquals(hash, helper.login("employee1", "employee123").getPassword());
        assertEquals(3, count("Account"));
        assertEquals(4, count("WorkLog"));
        assertEquals(2, count("Salary"));
        assertEquals(950_000, helper.getSalaries(employeeAccountId, 9, 2026).get(0).getTotalSalary(), 0);
    }

    @Test
    public void createsSearchesUpdatesAndDeletesAnEmployeeAndLinkedAccount() {
        long id = helper.insertEmployee(newEmployee("NV003"), newAccount("newuser"), adminId);
        assertTrue(id > 0);
        Account account = helper.login("newuser", "newpass");
        assertNotNull(account);
        Employee created = helper.getEmployeeByAccountId(account.getId());
        assertEquals(id, created.getId());
        assertEquals("0903000003", created.getPhone());
        assertEquals(1, helper.getEmployees(adminId, "NV003").size());
        assertEquals(1, helper.getEmployees(adminId, "new@example.com").size());
        assertTrue(helper.getEmployees(adminId, "' OR 1=1 --").isEmpty());
        assertTrue(helper.getEmployees(adminId, "%").isEmpty());
        Employee changed = new Employee(created.getId(), "NV003", "Updated Name",
                "updated@example.com", "0903000003", 70_000, account.getId());
        assertEquals(1, helper.updateEmployee(changed, adminId));
        assertEquals("Updated Name", helper.getEmployeeByAccountId(account.getId()).getFullname());
        assertEquals(1, helper.deleteEmployee(created.getId(), adminId));
        assertNull(helper.getAccountById(account.getId()));
        assertNull(helper.login("newuser", "newpass"));
        assertEquals(0, helper.deleteEmployee(created.getId(), adminId));
    }

    @Test
    public void rejectsDuplicateCodeUsernameAndAccountReassignmentWithoutOrphans() {
        assertThrows(IllegalArgumentException.class,
                () -> helper.insertEmployee(newEmployee("NV001"), newAccount("uniqueuser"), adminId));
        assertNull(helper.login("uniqueuser", "newpass"));
        assertThrows(IllegalArgumentException.class,
                () -> helper.insertEmployee(newEmployee("NV003"), newAccount("admin"), adminId));
        assertThrows(IllegalArgumentException.class, () -> helper.insertEmployee(newEmployee("NV003"),
                new Account(0, "newadmin", "newpass", Account.ROLE_ADMIN), adminId));
        assertThrows(IllegalArgumentException.class, () -> helper.updateEmployee(new Employee(
                employee.getId(), employee.getCode(), employee.getFullname(), employee.getEmail(),
                employee.getPhone(), employee.getHourlyRate(), otherAccountId), adminId));
        assertEquals(3, count("Account"));
        assertEquals(2, count("Employee"));
    }

    @Test
    public void rejectsBadEmployeeFieldsAndHourlyRates() {
        for (double rate : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> helper.insertEmployee(new Employee(
                    0, "NV003", "Name", "new@example.com", "0903000003", rate, 0),
                    newAccount("newuser"), adminId));
        }
        assertThrows(IllegalArgumentException.class, () -> helper.insertEmployee(new Employee(
                0, "", "Name", "bad email", "abc", 50_000, 0), newAccount("newuser"), adminId));
        assertThrows(IllegalArgumentException.class, () -> helper.insertEmployee(new Employee(
                0, "NV003", "Name", "bad email", "0903000003", 50_000, 0),
                newAccount("newuser"), adminId));
        assertThrows(IllegalArgumentException.class, () -> helper.insertEmployee(new Employee(
                0, "NV003", "Name", "new@example.com", "abc", 50_000, 0),
                newAccount("newuser"), adminId));
        assertEquals(3, count("Account"));
    }

    @Test
    public void accountInsertRollsBackWhenEmployeeStorageFails() {
        helper.getWritableDatabase().execSQL("CREATE TEMP TRIGGER reject_test_employee "
                + "BEFORE INSERT ON Employee BEGIN SELECT RAISE(ABORT, 'test storage error'); END");
        assertEquals(-1, helper.insertEmployee(newEmployee("NV003"), newAccount("newuser"), adminId));
        assertEquals(3, count("Account"));
        assertNull(helper.login("newuser", "newpass"));
        assertFalse(helper.getWritableDatabase().inTransaction());
    }

    @Test
    public void employeesCannotReadOtherWorkOrUseAdminOperations() {
        assertThrows(SecurityException.class, () -> helper.getEmployees(employeeAccountId, ""));
        assertThrows(SecurityException.class,
                () -> helper.getWorkLogs(employeeAccountId, other.getId(), 9, 2026));
        assertThrows(SecurityException.class,
                () -> helper.insertEmployee(newEmployee("NV003"), newAccount("newuser"), employeeAccountId));
        assertThrows(SecurityException.class, () -> helper.updateEmployee(employee, employeeAccountId));
        assertThrows(SecurityException.class, () -> helper.deleteEmployee(other.getId(), employeeAccountId));
        assertThrows(SecurityException.class,
                () -> helper.calculateSalary(employee.getId(), 9, 2026, employeeAccountId));
        int salaryId = helper.getSalaries(employeeAccountId, 9, 2026).get(0).getId();
        assertThrows(SecurityException.class, () -> helper.markSalaryPaid(salaryId, employeeAccountId));
        assertThrows(SecurityException.class, () -> helper.deleteDraftSalary(salaryId, employeeAccountId));
        assertEquals(1, helper.getSalaries(employeeAccountId, 9, 2026).size());
        assertEquals(employee.getId(), helper.getSalaries(employeeAccountId, 9, 2026).get(0).getEmployeeId());
        assertEquals(2, helper.getSalaries(adminId, 9, 2026).size());
        assertThrows(SecurityException.class, () -> helper.checkIn(adminId));
        assertThrows(SecurityException.class, () -> helper.checkOut(adminId));
        assertThrows(SecurityException.class, () -> helper.getTodayWorkLog(adminId));
        assertThrows(SecurityException.class, () -> helper.checkIn(-1));
    }

    @Test
    public void checksInAndOutOnceWithExactUnroundedHours() throws Exception {
        assertNull(helper.getTodayWorkLog(employeeAccountId));
        assertThrows(IllegalStateException.class, () -> helper.checkOut(employeeAccountId));
        long id = helper.checkIn(employeeAccountId);
        assertTrue(id > 0);
        WorkLog opened = helper.getTodayWorkLog(employeeAccountId);
        assertEquals(WorkLog.STATUS_CHECKED_IN, opened.getStatus());
        assertNull(opened.getCheckOutTime());
        assertEquals(0, opened.getTotalHours(), 0);
        assertThrows(IllegalStateException.class, () -> helper.checkIn(employeeAccountId));
        assertThrows(IllegalStateException.class, () -> helper.checkOut(employeeAccountId));
        setTime("2026-10-24 07:59:59");
        assertThrows(IllegalStateException.class, () -> helper.checkOut(employeeAccountId));
        setTime("2026-10-24 18:00:01");
        assertEquals(1, helper.checkOut(employeeAccountId));
        WorkLog closed = helper.getTodayWorkLog(employeeAccountId);
        assertEquals(id, closed.getId());
        assertEquals(WorkLog.STATUS_COMPLETED, closed.getStatus());
        assertEquals("18:00:01", closed.getCheckOutTime());
        assertEquals(10 + 1 / 3600.0, closed.getTotalHours(), 1e-12);
        assertThrows(IllegalStateException.class, () -> helper.checkOut(employeeAccountId));
        assertThrows(IllegalStateException.class, () -> helper.checkIn(employeeAccountId));
    }

    @Test
    public void abandonedShiftIsNeverAutomaticallyCompletedOrIncludedInPayroll() throws Exception {
        helper.checkIn(employeeAccountId);
        setTime("2026-10-25 08:00:00");
        assertNull(helper.getTodayWorkLog(employeeAccountId));
        assertThrows(IllegalStateException.class, () -> helper.checkOut(employeeAccountId));
        assertThrows(IllegalStateException.class,
                () -> helper.calculateSalary(employee.getId(), 10, 2026, adminId));
        List<WorkLog> logs = helper.getWorkLogs(employeeAccountId, employee.getId(), 10, 2026);
        assertEquals(1, logs.size());
        assertEquals(WorkLog.STATUS_CHECKED_IN, logs.get(0).getStatus());
        assertNull(logs.get(0).getCheckOutTime());
    }

    @Test
    public void monthAndYearFiltersHandleDecemberJanuaryAndEmptyPeriods() throws Exception {
        completeShift("2026-12-31", "08:00:00", "16:00:00");
        completeShift("2027-01-01", "08:00:00", "18:00:00");
        assertEquals(1, helper.getWorkLogs(employeeAccountId, employee.getId(), 12, 2026).size());
        assertEquals(1, helper.getWorkLogs(employeeAccountId, employee.getId(), 1, 2027).size());
        assertTrue(helper.getWorkLogs(employeeAccountId, employee.getId(), 1, 2026).isEmpty());
        assertTrue(helper.getSalaries(employeeAccountId, 1, 2026).isEmpty());
        Salary empty = helper.calculateSalary(employee.getId(), 2, 2026, adminId);
        assertNotNull(empty);
        assertEquals(0, empty.getTotalSalary(), 0);
        assertThrows(IllegalArgumentException.class,
                () -> helper.getWorkLogs(employeeAccountId, employee.getId(), 13, 2026));
        assertThrows(IllegalArgumentException.class, () -> helper.getSalaries(adminId, 0, 2026));
        assertThrows(IllegalArgumentException.class,
                () -> helper.calculateSalary(employee.getId(), 1, 0, adminId));
    }

    @Test
    public void calculatesKnownMonthlyPayrollAndReusesExistingDraftId() {
        Salary original = helper.getSalaries(employeeAccountId, 9, 2026).get(0);
        Salary recalculated = helper.calculateSalary(employee.getId(), 9, 2026, adminId);
        assertNotNull(recalculated);
        assertEquals(original.getId(), recalculated.getId());
        assertEquals(18, recalculated.getTotalHours(), 0);
        assertEquals(2, recalculated.getOvertimeHours(), 0);
        assertEquals(950_000, recalculated.getTotalSalary(), 0);
        assertEquals(2, count("Salary"));
        assertEquals(1, helper.deleteDraftSalary(recalculated.getId(), adminId));
        assertEquals(0, helper.deleteDraftSalary(recalculated.getId(), adminId));
    }

    @Test
    public void refreshesDraftAtomicallyOnCheckoutAndHourlyRateChange() throws Exception {
        Salary draft = helper.calculateSalary(employee.getId(), 10, 2026, adminId);
        assertNotNull(draft);
        helper.checkIn(employeeAccountId);
        assertThrows(IllegalStateException.class, () -> helper.markSalaryPaid(draft.getId(), adminId));
        assertThrows(IllegalStateException.class,
                () -> helper.calculateSalary(employee.getId(), 10, 2026, adminId));
        assertEquals(1, helper.updateEmployee(withRate(employee, 60_000), adminId));
        assertEquals(0, helper.getSalaries(employeeAccountId, 10, 2026).get(0).getTotalSalary(), 0);
        setTime("2026-10-24 18:00:00");
        assertEquals(1, helper.checkOut(employeeAccountId));
        Salary updated = helper.getSalaries(employeeAccountId, 10, 2026).get(0);
        assertEquals(draft.getId(), updated.getId());
        assertEquals(10, updated.getTotalHours(), 0);
        assertEquals(2, updated.getOvertimeHours(), 0);
        assertEquals(660_000, updated.getTotalSalary(), 0);
        assertEquals(1_140_000, helper.getSalaries(employeeAccountId, 9, 2026).get(0).getTotalSalary(), 0);
        assertEquals(1, helper.updateEmployee(withRate(employee, 100_000), adminId));
        assertEquals(1_100_000, helper.getSalaries(employeeAccountId, 10, 2026).get(0).getTotalSalary(), 0);
    }

    @Test
    public void checkoutRollsBackIfDraftRefreshCannotBeSaved() throws Exception {
        helper.calculateSalary(employee.getId(), 10, 2026, adminId);
        helper.checkIn(employeeAccountId);
        helper.getWritableDatabase().execSQL("CREATE TEMP TRIGGER reject_test_salary_update "
                + "BEFORE UPDATE ON Salary BEGIN SELECT RAISE(ABORT, 'test storage error'); END");
        setTime("2026-10-24 18:00:00");
        assertEquals(0, helper.checkOut(employeeAccountId));
        assertEquals(WorkLog.STATUS_CHECKED_IN, helper.getTodayWorkLog(employeeAccountId).getStatus());
        assertNull(helper.getTodayWorkLog(employeeAccountId).getCheckOutTime());
        assertEquals(0, helper.getSalaries(employeeAccountId, 10, 2026).get(0).getTotalSalary(), 0);
    }

    @Test
    public void paidSalaryIsImmutableEvenWhenRateOrWorkLogsChange() throws Exception {
        Salary salary = helper.calculateSalary(employee.getId(), 10, 2026, adminId);
        assertNotNull(salary);
        assertEquals(1, helper.markSalaryPaid(salary.getId(), adminId));
        completeShift("2026-10-24", "08:00:00", "18:00:00");
        helper.updateEmployee(withRate(employee, 100_000), adminId);
        Salary paid = helper.getSalaries(employeeAccountId, 10, 2026).get(0);
        assertEquals(Salary.STATUS_PAID, paid.getStatus());
        assertEquals(0, paid.getTotalHours(), 0);
        assertEquals(0, paid.getTotalSalary(), 0);
        assertThrows(IllegalStateException.class,
                () -> helper.calculateSalary(employee.getId(), 10, 2026, adminId));
        assertThrows(IllegalStateException.class, () -> helper.deleteDraftSalary(salary.getId(), adminId));
        assertThrows(IllegalStateException.class, () -> helper.markSalaryPaid(salary.getId(), adminId));
    }

    @Test
    public void refusesToMarkStaleDraftPaid() {
        Salary draft = helper.getSalaries(employeeAccountId, 9, 2026).get(0);
        helper.getWritableDatabase().execSQL("UPDATE Salary SET totalSalary = totalSalary + 1 WHERE id = ?",
                new Object[]{draft.getId()});
        assertThrows(IllegalStateException.class, () -> helper.markSalaryPaid(draft.getId(), adminId));
        Salary recalculated = helper.calculateSalary(employee.getId(), 9, 2026, adminId);
        assertNotNull(recalculated);
        assertEquals(1, helper.markSalaryPaid(recalculated.getId(), adminId));
    }

    @Test
    public void historyPreventsEmployeeDeletionAndDatabaseConstraintsPreventBrokenRows() {
        assertThrows(IllegalStateException.class, () -> helper.deleteEmployee(employee.getId(), adminId));
        SQLiteDatabase db = helper.getWritableDatabase();
        assertThrows(SQLiteConstraintException.class, () -> db.delete("Employee", "id = ?",
                new String[]{String.valueOf(employee.getId())}));
        ContentValues invalid = new ContentValues();
        invalid.put("employeeId", 999999);
        invalid.put("workDate", "2026-10-24");
        invalid.put("checkInTime", "08:00:00");
        invalid.put("totalHours", 0);
        invalid.put("status", WorkLog.STATUS_CHECKED_IN);
        assertThrows(SQLiteConstraintException.class, () -> db.insertOrThrow("WorkLog", null, invalid));
        invalid.put("employeeId", employee.getId());
        invalid.put("status", WorkLog.STATUS_COMPLETED);
        assertThrows(SQLiteConstraintException.class, () -> db.insertOrThrow("WorkLog", null, invalid));
        assertEquals(2, count("Employee"));
        assertEquals(3, count("Account"));
    }

    private void setTime(String value) throws Exception {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        formatter.setLenient(false);
        timeMillis = formatter.parse(value).getTime();
    }

    private void completeShift(String date, String start, String end) throws Exception {
        setTime(date + " " + start);
        assertTrue(helper.checkIn(employeeAccountId) > 0);
        setTime(date + " " + end);
        assertEquals(1, helper.checkOut(employeeAccountId));
    }

    private static Employee newEmployee(String code) {
        return new Employee(0, code, "New Employee", "new@example.com", "0903000003", 50_000, 0);
    }

    private static Account newAccount(String username) {
        return new Account(0, username, "newpass", Account.ROLE_EMPLOYEE);
    }

    private static Employee withRate(Employee source, double rate) {
        return new Employee(source.getId(), source.getCode(), source.getFullname(), source.getEmail(),
                source.getPhone(), rate, source.getAccountId());
    }

    private int count(String table) {
        try (Cursor cursor = helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + table, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }

    private void assertColumns(String table, String... expected) {
        List<String> actual = new ArrayList<>();
        try (Cursor cursor = helper.getReadableDatabase().rawQuery("PRAGMA table_info(" + table + ")", null)) {
            while (cursor.moveToNext()) {
                actual.add(cursor.getString(cursor.getColumnIndexOrThrow("name")));
            }
        }
        assertEquals(Arrays.asList(expected), actual);
    }
}
