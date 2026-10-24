package com.example.mblab2.models;

public final class Salary {
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PAID = "PAID";

    private final int id;
    private final int employeeId;
    private final int month;
    private final int year;
    private final double totalHours;
    private final double overtimeHours;
    private final double totalSalary;
    private final String status;

    public Salary(int id, int employeeId, int month, int year, double totalHours,
                  double overtimeHours, double totalSalary, String status) {
        this.id = id;
        this.employeeId = employeeId;
        this.month = month;
        this.year = year;
        this.totalHours = totalHours;
        this.overtimeHours = overtimeHours;
        this.totalSalary = totalSalary;
        this.status = status;
    }

    public int getId() { return id; }
    public int getEmployeeId() { return employeeId; }
    public int getMonth() { return month; }
    public int getYear() { return year; }
    public double getTotalHours() { return totalHours; }
    public double getOvertimeHours() { return overtimeHours; }
    public double getTotalSalary() { return totalSalary; }
    public String getStatus() { return status; }
}
