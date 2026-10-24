package com.example.mblab2.activities;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.mblab2.R;
import com.example.mblab2.dal.DBHelper;
import com.example.mblab2.models.Account;
import com.example.mblab2.models.Employee;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class EmployeeActivity extends AppCompatActivity {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{2,20}$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_.]{3,30}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^(0|\\+84)[0-9]{9,10}$");
    private static final int MIN_PASSWORD_LENGTH = 6;

    private DBHelper db;
    private EditText edtSearch;
    private TextView txtEmpty;
    private final List<Employee> employees = new ArrayList<>();
    private EmployeeAdapter adapter;
    private final NumberFormat moneyFormat = NumberFormat.getInstance(Locale.forLanguageTag("vi-VN"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DBHelper(this);
        if (requireAdmin() == null) {
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_employee);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.employee_root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        edtSearch = findViewById(R.id.employee_edt_search);
        txtEmpty = findViewById(R.id.employee_txt_empty);
        ListView listView = findViewById(R.id.employee_list);
        Button btnAdd = findViewById(R.id.employee_btn_add);

        adapter = new EmployeeAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> showForm(employees.get(position)));
        btnAdd.setOnClickListener(v -> showForm(null));
        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                loadEmployees();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) {
            loadEmployees();
        }
    }

    @Override
    protected void onDestroy() {
        if (db != null) {
            db.close();
        }
        super.onDestroy();
    }

    // Kiểm tra lại phiên và role ADMIN trước mỗi thao tác; không hợp lệ thì về đăng nhập.
    private Account requireAdmin() {
        Account account = LoginActivity.getValidSession(this, db);
        if (account == null || !Account.ROLE_ADMIN.equals(account.getRole())) {
            Toast.makeText(this, R.string.employee_error_permission, Toast.LENGTH_LONG).show();
            LoginActivity.logout(this);
            return null;
        }
        return account;
    }

    private void loadEmployees() {
        Account admin = requireAdmin();
        if (admin == null) {
            return;
        }
        employees.clear();
        try {
            employees.addAll(db.getEmployees(admin.getId(), edtSearch.getText().toString().trim()));
        } catch (SecurityException e) {
            LoginActivity.logout(this);
            return;
        } catch (RuntimeException e) {
            showError(e);
        }
        adapter.notifyDataSetChanged();
        boolean hasKeyword = edtSearch.getText().toString().trim().length() > 0;
        txtEmpty.setText(hasKeyword ? R.string.employee_empty_search : R.string.employee_empty);
        txtEmpty.setVisibility(employees.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showForm(Employee original) {
        boolean isNew = original == null;
        View form = LayoutInflater.from(this).inflate(R.layout.employee_dialog_form, null, false);
        EditText edtCode = form.findViewById(R.id.employee_form_code);
        EditText edtFullname = form.findViewById(R.id.employee_form_fullname);
        EditText edtEmail = form.findViewById(R.id.employee_form_email);
        EditText edtPhone = form.findViewById(R.id.employee_form_phone);
        EditText edtRate = form.findViewById(R.id.employee_form_rate);
        EditText edtUsername = form.findViewById(R.id.employee_form_username);
        EditText edtPassword = form.findViewById(R.id.employee_form_password);
        View groupAccount = form.findViewById(R.id.employee_form_group_account);

        // Sửa hồ sơ không đổi username/password/accountId.
        groupAccount.setVisibility(isNew ? View.VISIBLE : View.GONE);
        if (!isNew) {
            edtCode.setText(original.getCode());
            edtFullname.setText(original.getFullname());
            edtEmail.setText(original.getEmail());
            edtPhone.setText(original.getPhone());
            edtRate.setText(formatRateInput(original.getHourlyRate()));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(isNew ? R.string.employee_form_title_add : R.string.employee_form_title_edit)
                .setView(form)
                .setNegativeButton(R.string.employee_cancel, null)
                .setPositiveButton(R.string.employee_save, null);
        if (!isNew) {
            builder.setNeutralButton(R.string.employee_delete, null);
        }
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            // Ghi đè listener để dialog không tự đóng khi dữ liệu sai.
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                Employee input = validateForm(original, edtCode, edtFullname, edtEmail, edtPhone, edtRate);
                if (input == null) {
                    return;
                }
                boolean saved;
                if (isNew) {
                    Account account = validateAccount(edtUsername, edtPassword);
                    if (account == null) {
                        return;
                    }
                    saved = insertEmployee(input, account);
                } else {
                    saved = updateEmployee(input);
                }
                if (saved) {
                    dialog.dismiss();
                    loadEmployees();
                }
            });
            if (!isNew) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> confirmDelete(original, dialog));
            }
        });
        dialog.show();
    }

    private Employee validateForm(Employee original, EditText edtCode, EditText edtFullname,
                                  EditText edtEmail, EditText edtPhone, EditText edtRate) {
        String code = edtCode.getText().toString().trim();
        String fullname = edtFullname.getText().toString().trim();
        String email = edtEmail.getText().toString().trim();
        String phone = edtPhone.getText().toString().trim();
        String rateText = edtRate.getText().toString().trim();

        boolean valid = true;
        if (!CODE_PATTERN.matcher(code).matches()) {
            edtCode.setError(getString(R.string.employee_error_code));
            valid = false;
        }
        if (fullname.isEmpty() || fullname.length() > 100) {
            edtFullname.setError(getString(R.string.employee_error_fullname));
            valid = false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            edtEmail.setError(getString(R.string.employee_error_email));
            valid = false;
        }
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            edtPhone.setError(getString(R.string.employee_error_phone));
            valid = false;
        }
        double rate = parseRate(rateText);
        if (rate <= 0) {
            edtRate.setError(getString(R.string.employee_error_rate));
            valid = false;
        }
        if (!valid) {
            return null;
        }

        int id = original == null ? 0 : original.getId();
        int accountId = original == null ? 0 : original.getAccountId();
        return new Employee(id, code, fullname, email, phone, rate, accountId);
    }

    private Account validateAccount(EditText edtUsername, EditText edtPassword) {
        String username = edtUsername.getText().toString().trim();
        String password = edtPassword.getText().toString();
        boolean valid = true;
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            edtUsername.setError(getString(R.string.employee_error_username));
            valid = false;
        }
        if (password.length() < MIN_PASSWORD_LENGTH || !password.trim().equals(password)) {
            edtPassword.setError(getString(R.string.employee_error_password, MIN_PASSWORD_LENGTH));
            valid = false;
        }
        return valid ? new Account(0, username, password, Account.ROLE_EMPLOYEE) : null;
    }

    // Trả về -1 nếu không phải số hữu hạn dương.
    private static double parseRate(String text) {
        if (TextUtils.isEmpty(text)) {
            return -1;
        }
        try {
            double value = Double.parseDouble(text.replace(",", ""));
            return Double.isFinite(value) && value > 0 ? value : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String formatRateInput(double rate) {
        if (rate == Math.rint(rate)) {
            return String.valueOf((long) rate);
        }
        return String.valueOf(rate);
    }

    private boolean insertEmployee(Employee employee, Account account) {
        Account admin = requireAdmin();
        if (admin == null) {
            return false;
        }
        try {
            long id = db.insertEmployee(employee, account, admin.getId());
            if (id <= 0) {
                Toast.makeText(this, R.string.employee_error_save, Toast.LENGTH_LONG).show();
                return false;
            }
            Toast.makeText(this, R.string.employee_added, Toast.LENGTH_SHORT).show();
            return true;
        } catch (SecurityException e) {
            LoginActivity.logout(this);
        } catch (RuntimeException e) {
            showError(e);
        }
        return false;
    }

    private boolean updateEmployee(Employee employee) {
        Account admin = requireAdmin();
        if (admin == null) {
            return false;
        }
        try {
            int rows = db.updateEmployee(employee, admin.getId());
            if (rows <= 0) {
                Toast.makeText(this, R.string.employee_error_save, Toast.LENGTH_LONG).show();
                return false;
            }
            Toast.makeText(this, R.string.employee_updated, Toast.LENGTH_SHORT).show();
            return true;
        } catch (SecurityException e) {
            LoginActivity.logout(this);
        } catch (RuntimeException e) {
            showError(e);
        }
        return false;
    }

    private void confirmDelete(Employee employee, AlertDialog formDialog) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.employee_delete_title)
                .setMessage(getString(R.string.employee_delete_message, employee.getFullname(), employee.getCode()))
                .setNegativeButton(R.string.employee_cancel, null)
                .setPositiveButton(R.string.employee_delete, (d, which) -> {
                    if (deleteEmployee(employee)) {
                        formDialog.dismiss();
                        loadEmployees();
                    }
                })
                .show();
    }

    private boolean deleteEmployee(Employee employee) {
        Account admin = requireAdmin();
        if (admin == null) {
            return false;
        }
        try {
            int rows = db.deleteEmployee(employee.getId(), admin.getId());
            if (rows <= 0) {
                Toast.makeText(this, R.string.employee_error_delete, Toast.LENGTH_LONG).show();
                return false;
            }
            Toast.makeText(this, R.string.employee_deleted, Toast.LENGTH_SHORT).show();
            return true;
        } catch (SecurityException e) {
            LoginActivity.logout(this);
        } catch (IllegalStateException e) {
            Toast.makeText(this, R.string.employee_error_has_history, Toast.LENGTH_LONG).show();
        } catch (RuntimeException e) {
            showError(e);
        }
        return false;
    }

    // IllegalArgumentException/IllegalStateException từ DBHelper mang thông báo nghiệp vụ (trùng mã, trùng username...).
    private void showError(RuntimeException e) {
        String message = e.getMessage();
        if ((e instanceof IllegalArgumentException || e instanceof IllegalStateException)
                && !TextUtils.isEmpty(message)) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, R.string.employee_error_save, Toast.LENGTH_LONG).show();
        }
    }

    private class EmployeeAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return employees.size();
        }

        @Override
        public Employee getItem(int position) {
            return employees.get(position);
        }

        @Override
        public long getItemId(int position) {
            return employees.get(position).getId();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView != null ? convertView
                    : LayoutInflater.from(parent.getContext()).inflate(R.layout.employee_item, parent, false);
            Employee employee = getItem(position);
            TextView txtName = view.findViewById(R.id.employee_item_name);
            TextView txtContact = view.findViewById(R.id.employee_item_contact);
            TextView txtRate = view.findViewById(R.id.employee_item_rate);
            txtName.setText(getString(R.string.employee_item_name, employee.getCode(), employee.getFullname()));
            txtContact.setText(getString(R.string.employee_item_contact, employee.getEmail(), employee.getPhone()));
            txtRate.setText(getString(R.string.employee_item_rate, moneyFormat.format(employee.getHourlyRate())));
            return view;
        }
    }
}
