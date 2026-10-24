package com.example.mblab2.models;

public final class Employee {
    private final int id;
    private final String code;
    private final String fullname;
    private final String email;
    private final String phone;
    private final double hourlyRate;
    private final int accountId;

    public Employee(int id, String code, String fullname, String email, String phone,
                    double hourlyRate, int accountId) {
        this.id = id;
        this.code = code;
        this.fullname = fullname;
        this.email = email;
        this.phone = phone;
        this.hourlyRate = hourlyRate;
        this.accountId = accountId;
    }

    public int getId() { return id; }
    public String getCode() { return code; }
    public String getFullname() { return fullname; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public double getHourlyRate() { return hourlyRate; }
    public int getAccountId() { return accountId; }
}
