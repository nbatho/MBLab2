package com.example.mblab2.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.mblab2.MainActivity;
import com.example.mblab2.R;
import com.example.mblab2.dal.DBHelper;
import com.example.mblab2.models.Account;

public class LoginActivity extends AppCompatActivity {

    public static final String PREFS = "login_prefs";
    public static final String KEY_ACCOUNT_ID = "accountId";
    public static final String KEY_USERNAME = "username";
    public static final String KEY_ROLE = "role";
    public static final String KEY_REMEMBER = "remember";

    // Phiên không ghi nhớ chỉ sống trong process hiện tại; khởi động lại app phải đăng nhập lại.
    private static boolean sessionStartedInProcess = false;

    private EditText edtUsername;
    private EditText edtPassword;
    private CheckBox chkRemember;
    private DBHelper db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DBHelper(this);

        if (getValidSession(this, db) != null) {
            openMain();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.login_root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        edtUsername = findViewById(R.id.login_edt_username);
        edtPassword = findViewById(R.id.login_edt_password);
        chkRemember = findViewById(R.id.login_chk_remember);
        Button btnLogin = findViewById(R.id.login_btn_submit);
        btnLogin.setOnClickListener(v -> attemptLogin());
    }

    @Override
    protected void onDestroy() {
        if (db != null) {
            db.close();
        }
        super.onDestroy();
    }

    private void attemptLogin() {
        edtUsername.setError(null);
        edtPassword.setError(null);

        String username = edtUsername.getText().toString().trim();
        String password = edtPassword.getText().toString();

        if (TextUtils.isEmpty(username)) {
            edtUsername.setError(getString(R.string.login_error_username_required));
            edtUsername.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(password)) {
            edtPassword.setError(getString(R.string.login_error_password_required));
            edtPassword.requestFocus();
            return;
        }

        Account account;
        try {
            account = db.login(username, password);
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.login_error_system, Toast.LENGTH_LONG).show();
            return;
        }

        if (account == null) {
            edtPassword.setText("");
            Toast.makeText(this, R.string.login_error_invalid, Toast.LENGTH_LONG).show();
            return;
        }
        if (!isAccountUsable(db, account)) {
            Toast.makeText(this, R.string.login_error_no_profile, Toast.LENGTH_LONG).show();
            return;
        }

        saveSession(this, account, chkRemember.isChecked());
        openMain();
    }

    private void openMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // Role phải hợp lệ; EMPLOYEE phải có hồ sơ Employee khớp accountId.
    private static boolean isAccountUsable(DBHelper db, Account account) {
        String role = account.getRole();
        if (Account.ROLE_ADMIN.equals(role)) {
            return true;
        }
        if (Account.ROLE_EMPLOYEE.equals(role)) {
            return db.getEmployeeByAccountId(account.getId()) != null;
        }
        return false;
    }

    public static void saveSession(Context context, Account account, boolean remember) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_ACCOUNT_ID, account.getId())
                .putString(KEY_USERNAME, account.getUsername())
                .putString(KEY_ROLE, account.getRole())
                .putBoolean(KEY_REMEMBER, remember)
                .apply();
        sessionStartedInProcess = true;
    }

    public static void clearSession(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        sessionStartedInProcess = false;
    }

    /**
     * Đọc phiên từ SharedPreferences rồi đối chiếu với Account thực tế trong DB.
     * Trả về Account hợp lệ, hoặc null (và xóa phiên) khi phiên thiếu, hỏng, hết hạn
     * hoặc role/hồ sơ không còn khớp.
     */
    public static Account getValidSession(Context context, DBHelper db) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int accountId;
        String username;
        String role;
        boolean remember;
        try {
            accountId = prefs.getInt(KEY_ACCOUNT_ID, -1);
            username = prefs.getString(KEY_USERNAME, null);
            role = prefs.getString(KEY_ROLE, null);
            remember = prefs.getBoolean(KEY_REMEMBER, false);
        } catch (ClassCastException e) {
            clearSession(context);
            return null;
        }

        if (accountId <= 0 || username == null || role == null || (!remember && !sessionStartedInProcess)) {
            clearSession(context);
            return null;
        }

        Account account;
        try {
            account = db.getAccountById(accountId);
            if (account == null
                    || !username.equals(account.getUsername())
                    || !role.equals(account.getRole())
                    || !isAccountUsable(db, account)) {
                clearSession(context);
                return null;
            }
        } catch (RuntimeException e) {
            clearSession(context);
            return null;
        }

        sessionStartedInProcess = true;
        return account;
    }

    // Đăng xuất hoặc phiên không hợp lệ: xóa phiên và dọn back stack về màn hình đăng nhập.
    public static void logout(Activity activity) {
        clearSession(activity);
        Intent intent = new Intent(activity, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }
}
