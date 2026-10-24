package com.example.mblab2.dal;

import android.database.sqlite.SQLiteDatabase;

/** Version 1 tables. Schema changes require an explicit DBHelper migration. */
final class DatabaseSchema {
    private DatabaseSchema() { }

    static void create(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE Account ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "username TEXT NOT NULL UNIQUE CHECK(length(trim(username)) > 0),"
                + "password TEXT NOT NULL,"
                + "role TEXT NOT NULL CHECK(role IN ('ADMIN','EMPLOYEE')))");
        db.execSQL("CREATE TABLE Employee ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "code TEXT NOT NULL UNIQUE CHECK(length(trim(code)) > 0),"
                + "fullname TEXT NOT NULL CHECK(length(trim(fullname)) > 0),"
                + "email TEXT NOT NULL, phone TEXT NOT NULL,"
                + "hourlyRate REAL NOT NULL CHECK(hourlyRate > 0),"
                + "accountId INTEGER NOT NULL UNIQUE REFERENCES Account(id) ON DELETE RESTRICT)");
        db.execSQL("CREATE TABLE WorkLog ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "employeeId INTEGER NOT NULL REFERENCES Employee(id) ON DELETE RESTRICT,"
                + "workDate TEXT NOT NULL, checkInTime TEXT NOT NULL, checkOutTime TEXT,"
                + "totalHours REAL NOT NULL DEFAULT 0 CHECK(totalHours >= 0),"
                + "status TEXT NOT NULL CHECK(status IN ('CHECKED_IN','COMPLETED')),"
                + "UNIQUE(employeeId,workDate),"
                + "CHECK((status = 'CHECKED_IN' AND checkOutTime IS NULL AND totalHours = 0)"
                + " OR (status = 'COMPLETED' AND checkOutTime IS NOT NULL"
                + " AND checkOutTime > checkInTime AND totalHours > 0)))");
        db.execSQL("CREATE TABLE Salary ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "employeeId INTEGER NOT NULL REFERENCES Employee(id) ON DELETE RESTRICT,"
                + "month INTEGER NOT NULL CHECK(month BETWEEN 1 AND 12),"
                + "year INTEGER NOT NULL CHECK(year BETWEEN 1 AND 9999),"
                + "totalHours REAL NOT NULL CHECK(totalHours >= 0),"
                + "overtimeHours REAL NOT NULL CHECK(overtimeHours >= 0 AND overtimeHours <= totalHours),"
                + "totalSalary REAL NOT NULL CHECK(totalSalary >= 0),"
                + "status TEXT NOT NULL CHECK(status IN ('DRAFT','PAID')),"
                + "UNIQUE(employeeId,month,year))");
    }
}
