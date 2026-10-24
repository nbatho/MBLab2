package com.example.mblab2.models;

public final class WorkLog {
    public static final String STATUS_CHECKED_IN = "CHECKED_IN";
    public static final String STATUS_COMPLETED = "COMPLETED";

    private final int id;
    private final int employeeId;
    private final String workDate;
    private final String checkInTime;
    private final String checkOutTime;
    private final double totalHours;
    private final String status;

    public WorkLog(int id, int employeeId, String workDate, String checkInTime,
                   String checkOutTime, double totalHours, String status) {
        this.id = id;
        this.employeeId = employeeId;
        this.workDate = workDate;
        this.checkInTime = checkInTime;
        this.checkOutTime = checkOutTime;
        this.totalHours = totalHours;
        this.status = status;
    }

    public int getId() { return id; }
    public int getEmployeeId() { return employeeId; }
    public String getWorkDate() { return workDate; }
    public String getCheckInTime() { return checkInTime; }
    public String getCheckOutTime() { return checkOutTime; }
    public double getTotalHours() { return totalHours; }
    public String getStatus() { return status; }
}
