package com.example.mblab2;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.example.mblab2.activities.AttendanceActivity;
import com.example.mblab2.activities.EmployeeActivity;
import com.example.mblab2.activities.LoginActivity;
import com.example.mblab2.activities.SalaryActivity;
import com.example.mblab2.dal.DBHelper;
import com.example.mblab2.models.Account;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Set;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE;
import static androidx.test.espresso.matcher.ViewMatchers.Visibility.VISIBLE;
import static androidx.test.espresso.matcher.ViewMatchers.hasErrorText;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Exercises the real Activities and seeded DB without changing employee/work/payroll records. */
@RunWith(AndroidJUnit4.class)
public class IntegrationFlowTest {
    private Context context;
    private DBHelper db;
    private SharedPreferences prefs;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        db = new DBHelper(context);
        prefs = context.getSharedPreferences(LoginActivity.PREFS, Context.MODE_PRIVATE);
        LoginActivity.clearSession(context);
    }

    @After
    public void tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Set<Activity> activities = new HashSet<>();
            for (Stage stage : Stage.values()) {
                activities.addAll(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage));
            }
            for (Activity activity : activities) {
                if (!activity.isFinishing()) {
                    activity.finish();
                }
            }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        LoginActivity.clearSession(context);
        db.close();
    }

    @Test
    public void adminLoginConnectsEmployeeManagementAndPayroll() {
        try (ActivityScenario<LoginActivity> ignored = ActivityScenario.launch(LoginActivity.class)) {
            login("admin", "admin123");
            onView(withId(R.id.main_group_admin)).check(matches(withEffectiveVisibility(VISIBLE)));
            onView(withId(R.id.main_group_employee)).check(matches(withEffectiveVisibility(GONE)));
            onView(withId(R.id.main_btn_employees)).perform(click());
            onView(withId(R.id.employee_edt_search)).check(matches(isDisplayed()));
            pressBack();
            onView(withId(R.id.main_btn_admin_salary)).check(matches(isEnabled())).perform(click());
            onView(withId(R.id.salary_tv_title)).check(matches(withText(R.string.salary_title_admin)));
            onView(withId(R.id.salary_section_admin)).check(matches(withEffectiveVisibility(VISIBLE)));
            onView(withId(R.id.salary_section_employee)).check(matches(withEffectiveVisibility(GONE)));
            pressBack();
            onView(withId(R.id.main_btn_logout)).perform(scrollTo(), click());
            assertLoginVisible();
            assertTrue(prefs.getAll().isEmpty());
        }
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            assertLoginVisible();
        }
    }

    @Test
    public void employeeLoginConnectsOwnAttendanceAndSalary() {
        try (ActivityScenario<LoginActivity> ignored = ActivityScenario.launch(LoginActivity.class)) {
            login("employee1", "employee123");
            onView(withId(R.id.main_group_admin)).check(matches(withEffectiveVisibility(GONE)));
            onView(withId(R.id.main_group_employee)).check(matches(withEffectiveVisibility(VISIBLE)));
            onView(withId(R.id.main_btn_attendance)).check(matches(isEnabled())).perform(click());
            Account employee = db.login("employee1", "employee123");
            String name = db.getEmployeeByAccountId(employee.getId()).getFullname();
            String code = db.getEmployeeByAccountId(employee.getId()).getCode();
            onView(withId(R.id.attendance_tv_employee)).check(matches(withText(
                    context.getString(R.string.attendance_employee_info, code, name))));
            onView(withId(R.id.attendance_btn_prev_month)).perform(scrollTo(), click());
            onView(withId(R.id.attendance_tv_summary_total)).check(matches(withText(
                    context.getString(R.string.attendance_summary_total,
                            context.getString(R.string.attendance_hours_value, 18.0)))));
            pressBack();
            onView(withId(R.id.main_btn_my_salary)).check(matches(isEnabled())).perform(click());
            onView(withId(R.id.salary_tv_title)).check(matches(withText(R.string.salary_title_employee)));
            onView(withId(R.id.salary_section_admin)).check(matches(withEffectiveVisibility(GONE)));
            onView(withId(R.id.salary_btn_prev_month)).perform(click());
            onView(withId(R.id.salary_tv_detail_amount)).check(matches(withText(
                    context.getString(R.string.salary_label_total_salary,
                            context.getString(R.string.salary_money_value, "950.000")))));
        }
    }

    @Test
    public void emptyAndWrongCredentialsStayOnLogin() {
        try (ActivityScenario<LoginActivity> ignored = ActivityScenario.launch(LoginActivity.class)) {
            onView(withId(R.id.login_btn_submit)).perform(scrollTo(), click());
            onView(withId(R.id.login_edt_username)).check(matches(hasErrorText(
                    context.getString(R.string.login_error_username_required))));
            login("admin", "incorrect-password");
            assertLoginVisible();
            assertTrue(prefs.getAll().isEmpty());
        }
    }

    @Test
    public void attendanceRejectsTamperedSessionRole() {
        rememberEmployee();
        prefs.edit().putString(LoginActivity.KEY_ROLE, Account.ROLE_ADMIN).commit();
        try (ActivityScenario<AttendanceActivity> ignored = ActivityScenario.launch(AttendanceActivity.class)) {
            assertLoginVisible();
            assertTrue(prefs.getAll().isEmpty());
        }
    }

    @Test
    public void salaryRejectsTamperedSessionUsername() {
        rememberEmployee();
        prefs.edit().putString(LoginActivity.KEY_USERNAME, "another-user").commit();
        try (ActivityScenario<SalaryActivity> ignored = ActivityScenario.launch(SalaryActivity.class)) {
            assertLoginVisible();
            assertTrue(prefs.getAll().isEmpty());
        }
    }

    @Test
    public void employeeCannotOpenEmployeeManagementDirectly() {
        rememberEmployee();
        try (ActivityScenario<EmployeeActivity> ignored = ActivityScenario.launch(EmployeeActivity.class)) {
            assertLoginVisible();
        }
    }

    @Test
    public void adminCannotOpenAttendanceDirectly() {
        LoginActivity.saveSession(context, db.login("admin", "admin123"), true);
        try (ActivityScenario<AttendanceActivity> ignored = ActivityScenario.launch(AttendanceActivity.class)) {
            assertLoginVisible();
        }
    }

    @Test
    public void malformedPreferenceTypesClearSessionInsteadOfCrashing() {
        for (String key : new String[]{LoginActivity.KEY_ACCOUNT_ID, LoginActivity.KEY_USERNAME,
                LoginActivity.KEY_ROLE, LoginActivity.KEY_REMEMBER}) {
            rememberEmployee();
            prefs.edit().putFloat(key, 1.0f).commit();
            assertNull(LoginActivity.getValidSession(context, db));
            assertTrue(prefs.getAll().isEmpty());
        }
    }

    @Test
    public void persistedUnrememberedSessionCannotRestoreIntoSalary() {
        writePersistedEmployeeSession(false);
        try (ActivityScenario<SalaryActivity> ignored = ActivityScenario.launch(SalaryActivity.class)) {
            assertLoginVisible();
            assertTrue(prefs.getAll().isEmpty());
        }
    }

    @Test
    public void persistedRememberedSessionRestoresAndSurvivesRecreation() {
        writePersistedEmployeeSession(true);
        try (ActivityScenario<SalaryActivity> scenario = ActivityScenario.launch(SalaryActivity.class)) {
            onView(withId(R.id.salary_tv_title)).check(matches(withText(R.string.salary_title_employee)));
            scenario.recreate();
            onView(withId(R.id.salary_tv_title)).check(matches(withText(R.string.salary_title_employee)));
            assertNotNull(LoginActivity.getValidSession(context, db));
        }
    }

    private void writePersistedEmployeeSession(boolean remember) {
        Account employee = db.login("employee1", "employee123");
        // clearSession resets the process flag, as it would be before a fresh app process.
        LoginActivity.clearSession(context);
        prefs.edit().putInt(LoginActivity.KEY_ACCOUNT_ID, employee.getId())
                .putString(LoginActivity.KEY_USERNAME, employee.getUsername())
                .putString(LoginActivity.KEY_ROLE, employee.getRole())
                .putBoolean(LoginActivity.KEY_REMEMBER, remember).commit();
    }

    private void rememberEmployee() {
        LoginActivity.saveSession(context, db.login("employee1", "employee123"), true);
    }

    private void login(String username, String password) {
        onView(withId(R.id.login_edt_username)).perform(replaceText(username));
        onView(withId(R.id.login_edt_password)).perform(replaceText(password), closeSoftKeyboard());
        onView(withId(R.id.login_btn_submit)).perform(scrollTo(), click());
    }

    private void assertLoginVisible() {
        onView(withId(R.id.login_edt_username)).check(matches(isDisplayed()));
    }
}
