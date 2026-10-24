package com.example.mblab2;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.mblab2.activities.EmployeeActivity;
import com.example.mblab2.activities.LoginActivity;
import com.example.mblab2.dal.DBHelper;
import com.example.mblab2.models.Account;

public class MainActivity extends AppCompatActivity {

    private DBHelper db;
    private TextView txtGreeting;
    private View groupAdmin;
    private View groupEmployee;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DBHelper(this);
        if (LoginActivity.getValidSession(this, db) == null) {
            LoginActivity.logout(this);
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        txtGreeting = findViewById(R.id.main_txt_greeting);
        groupAdmin = findViewById(R.id.main_group_admin);
        groupEmployee = findViewById(R.id.main_group_employee);

        Button btnEmployees = findViewById(R.id.main_btn_employees);
        Button btnLogout = findViewById(R.id.main_btn_logout);
        btnEmployees.setOnClickListener(v -> openEmployeeManagement());
        btnLogout.setOnClickListener(v -> LoginActivity.logout(this));

        // Màn hình chấm công/lương thuộc phần Hùng; tạm vô hiệu hóa cho tới khi tích hợp.
        findViewById(R.id.main_btn_admin_salary).setEnabled(false);
        findViewById(R.id.main_btn_attendance).setEnabled(false);
        findViewById(R.id.main_btn_my_salary).setEnabled(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (txtGreeting == null) {
            return;
        }
        Account account = LoginActivity.getValidSession(this, db);
        if (account == null) {
            LoginActivity.logout(this);
            return;
        }
        bindMenu(account);
    }

    @Override
    protected void onDestroy() {
        if (db != null) {
            db.close();
        }
        super.onDestroy();
    }

    private void bindMenu(Account account) {
        boolean isAdmin = Account.ROLE_ADMIN.equals(account.getRole());
        txtGreeting.setText(getString(R.string.login_menu_greeting, account.getUsername(),
                getString(isAdmin ? R.string.login_role_admin : R.string.login_role_employee)));
        groupAdmin.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
        groupEmployee.setVisibility(isAdmin ? View.GONE : View.VISIBLE);
    }

    private void openEmployeeManagement() {
        Account account = LoginActivity.getValidSession(this, db);
        if (account == null) {
            LoginActivity.logout(this);
            return;
        }
        if (!Account.ROLE_ADMIN.equals(account.getRole())) {
            bindMenu(account);
            return;
        }
        startActivity(new Intent(this, EmployeeActivity.class));
    }
}
