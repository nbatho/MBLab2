package com.example.mblab2.models;

/** Account IDs identify sessions; they are not employee IDs. */
public final class Account {
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_EMPLOYEE = "EMPLOYEE";

    private final int id;
    private final String username;
    private final String password;
    private final String role;

    public Account(int id, String username, String password, String role) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.role = role;
    }

    public int getId() { return id; }
    public String getUsername() { return username; }
    /** New accounts carry input passwords; accounts read from DB carry password hashes. */
    public String getPassword() { return password; }
    public String getRole() { return role; }
}
