# Phân công công việc nhóm MBLab2

Dự án: **Ứng dụng Android quản lý giờ làm và tiền lương nhân viên**.

Thành viên: **Thọ, Hùng, Sơn**.

Ứng dụng sử dụng **Java, XML, SQLite và SharedPreferences**, có hai vai trò **Admin** và **Employee**. Tài liệu này tóm tắt phần việc của từng thành viên theo [README dự án](docs/README.md) và [README hướng dẫn AI](docs/README_AI.md). Chi tiết schema, API, phiên đăng nhập và công thức dùng chung được quy định trong README dự án.

Đây là bảng phân công và tiêu chí bàn giao, không phải xác nhận các công việc đã hoàn thành.

## 1. Bảng phân công tổng quát

| Thành viên | Phần phụ trách | Công việc chính | Branch theo phân công |
|---|---|---|---|
| **Thọ** | Dữ liệu và xử lý nghiệp vụ | Model, SQLite, dữ liệu mẫu, API dùng chung, tính giờ làm và tính lương | `feature/data` |
| **Hùng** | Chấm công và giao diện lương | Check-in/check-out, lịch sử công, tổng hợp tháng, xem và quản lý lương | `feature/work-salary` |
| **Sơn** | Đăng nhập và quản lý nhân viên | Đăng nhập, lưu phiên, phân quyền menu, đăng xuất, thêm/sửa/xóa/tìm kiếm nhân viên | `feature/login` |

## 2. Phần việc của Thọ

### Nhiệm vụ

- Xây dựng bốn model `Account`, `Employee`, `WorkLog`, `Salary` đúng thuộc tính và kiểu dữ liệu đã thống nhất.
- Xây dựng cơ sở dữ liệu SQLite, khóa ngoại và ràng buộc để tránh trùng tài khoản, mã nhân viên, công theo ngày và lương theo tháng.
- Cung cấp API qua `DBHelper` cho đăng nhập, truy vấn tài khoản và hồ sơ nhân viên.
- Xử lý thêm nhân viên kèm tài khoản trong cùng transaction; cập nhật, tìm kiếm và xóa theo điều kiện nghiệp vụ.
- Cung cấp API check-in/check-out, truy vấn lịch sử và tổng hợp giờ làm theo tháng.
- Xây dựng `PayrollCalculator`: tính giờ thường, giờ tăng ca và tiền lương; cung cấp API tạo/tính lại lương nháp và chốt lương.
- Kiểm tra quyền tại tầng dữ liệu; Employee chỉ thao tác với dữ liệu của mình.
- Chuẩn bị dữ liệu mẫu gồm Admin và ít nhất hai Employee, bàn giao tài khoản demo thực tế.
- Điều phối cấu hình nền và các thay đổi hợp đồng dùng chung khi cần.

### File phụ trách

Đường dẫn Java tính từ `app/src/main/java/com/example/mblab2/`.

| Nhóm file | Nội dung |
|---|---|
| `models/Account.java`, `models/Employee.java` | Model tài khoản và nhân viên |
| `models/WorkLog.java`, `models/Salary.java` | Model chấm công và lương |
| `dal/DBHelper.java` | Điểm truy cập API dữ liệu dùng chung |
| `dal/DatabaseSchema.java`, `dal/DatabaseSeeder.java` | Schema và dữ liệu mẫu |
| `dal/AccountDao.java`, `dal/EmployeeDao.java` | Truy cập dữ liệu tài khoản và nhân viên |
| `dal/WorkLogDao.java`, `dal/SalaryDao.java` | Truy cập dữ liệu chấm công và lương |
| `dal/DateTimeUtils.java`, `dal/PasswordHasher.java` | Xử lý ngày giờ và mật khẩu |
| `utils/PayrollCalculator.java` | Công thức tính giờ và tiền lương |

### Bàn giao và kiểm tra

- Bàn giao model, API, trạng thái, cách xử lý lỗi và tài khoản demo cho Sơn, Hùng.
- Kiểm tra ràng buộc dữ liệu, transaction, quyền truy cập, check-in/check-out không hợp lệ và trạng thái lương.
- Kiểm tra mẫu: hai ca 8 giờ và 10 giờ, đơn giá 50.000 đồng/giờ, hệ số tăng ca 1,5 cho kết quả **18 giờ làm, 2 giờ tăng ca, 950.000 đồng**.

## 3. Phần việc của Hùng

### Nhiệm vụ

- Xây dựng màn hình chấm công dành cho Employee: check-in, check-out và hiển thị trạng thái trong ngày.
- Hiển thị lịch sử theo tháng/năm, ngày làm, giờ vào/ra và tổng giờ của từng ca.
- Hiển thị tổng giờ làm và giờ tăng ca theo tháng; thể hiện rõ ca chưa check-out.
- Xây dựng màn hình để Employee xem giờ làm, tăng ca, tiền lương và trạng thái lương của chính mình.
- Xây dựng chức năng để Admin chọn nhân viên/tháng/năm, xem lương, tạo/tính lại bản nháp, xóa nháp và chuyển sang đã thanh toán theo API chung.
- Dùng `DBHelper` và `PayrollCalculator` của Thọ; không tạo công thức lương riêng trong giao diện.
- Dùng phiên đăng nhập do Sơn cung cấp; lấy đúng hồ sơ từ `accountId`, kiểm tra quyền trước khi hiển thị hoặc thao tác.
- Sau khi nhận phần Sơn, đăng ký hai Activity và nối các nút chấm công/lương trong menu theo đúng vai trò.

### File phụ trách

| Vị trí | File |
|---|---|
| `app/src/main/java/com/example/mblab2/activities/` | `AttendanceActivity.java`, `SalaryActivity.java`, `WorkLogSummary.java` |
| `app/src/main/res/layout/` | `activity_attendance.xml`, `activity_salary.xml`, `item_attendance_worklog.xml`, `item_salary_record.xml` |
| `app/src/main/res/values/` | `attendance_strings.xml`, `salary_strings.xml` |

### Bàn giao và kiểm tra

- Bàn giao hai màn hình cùng hướng dẫn thử với Admin và Employee.
- Kiểm tra check-in trùng, check-out khi chưa check-in, thao tác lặp, tháng rỗng và tháng còn ca mở.
- Kiểm tra Employee chỉ thấy công/lương của mình; Admin quản lý được lương theo quyền.
- Kiểm tra lương `PAID` không cho sửa/xóa/tính lại; thông báo rõ khi dữ liệu chưa đủ để tính hoặc chốt lương.

## 4. Phần việc của Sơn

### Nhiệm vụ

- Xây dựng giao diện và xử lý đăng nhập cho Admin, Employee bằng API của Thọ.
- Kiểm tra thông tin trống, sai tài khoản/mật khẩu và tài khoản Employee thiếu hồ sơ.
- Lưu phiên bằng SharedPreferences theo key và kiểu dữ liệu chung; không lưu mật khẩu.
- Xử lý ghi nhớ đăng nhập, kiểm tra phiên hợp lệ và đăng xuất; xóa back stack để không quay lại màn hình đã đăng xuất.
- Xây dựng menu theo vai trò: Admin quản lý nhân viên/lương; Employee chấm công/lịch sử và xem lương của mình.
- Xây dựng danh sách, tìm kiếm, xem, thêm, sửa và xóa nhân viên cho Admin.
- Form thêm nhân viên thu thập cả tài khoản và hồ sơ, đơn giá giờ; gọi API tạo trong cùng transaction.
- Kiểm tra dữ liệu bắt buộc, định dạng email/điện thoại, mã/tài khoản trùng và đơn giá không hợp lệ.
- Đăng ký màn hình đăng nhập, nhân viên và menu; bảo đảm `LoginActivity` là launcher duy nhất.

### File phụ trách

| Vị trí | File |
|---|---|
| `app/src/main/java/com/example/mblab2/activities/` | `LoginActivity.java`, `EmployeeActivity.java` |
| `app/src/main/java/com/example/mblab2/` | `MainActivity.java` |
| `app/src/main/res/layout/` | `activity_login.xml`, `activity_employee.xml`, `activity_main.xml`, `employee_item.xml`, `employee_dialog_form.xml` |

### Bàn giao và kiểm tra

- Bàn giao các hằng key, hàm kiểm tra phiên/đăng xuất và vị trí nút để Hùng nối điều hướng.
- Kiểm tra đăng nhập đúng/sai/trống, ghi nhớ đăng nhập, phiên hỏng, đăng xuất và nút Back.
- Kiểm tra thêm/sửa/xóa/tìm kiếm nhân viên bằng dữ liệu thật, chặn dữ liệu trùng và xóa nhân viên đã có lịch sử.
- Kiểm tra Employee không truy cập chức năng quản lý nhân viên.

## 5. Phối hợp và tích hợp

Cả ba làm song song trên branch riêng. **Thứ tự tích hợp là Thọ → Sơn → Hùng** vì giao diện cần API dữ liệu, còn phần chấm công/lương cần phiên và menu.

| Giai đoạn | Thọ | Hùng | Sơn |
|---|---|---|---|
| Làm độc lập | Model, schema, API, calculator, seed | Layout chấm công/lương, danh sách, bộ chọn tháng, validation | Layout đăng nhập/menu/nhân viên, validation, xử lý phiên |
| Nhận dữ liệu | Bàn giao API và dữ liệu mẫu | Nối API chấm công/lương | Nối API đăng nhập và nhân viên |
| Tích hợp | Hỗ trợ lỗi dữ liệu/nghiệp vụ | Nhận phần Sơn, nối điều hướng, thử công/lương | Bàn giao phiên, menu và quản lý nhân viên |
| Hoàn thiện | Kiểm tra dữ liệu và công thức | Kiểm tra luồng chấm công/lương | Kiểm tra đăng nhập, quyền và nhân viên |

Các file dùng chung cần phối hợp:

- `AndroidManifest.xml`: Sơn cấu hình launcher và các màn hình của mình; Hùng thêm màn hình chấm công/lương, giữ launcher đã thống nhất.
- `MainActivity.java`, `activity_main.xml`: Sơn phụ trách menu và phiên; Hùng chỉ bổ sung điều hướng chấm công/lương thuộc phần mình.
- Resource dùng chung: dùng tiền tố `login_`, `employee_`, `attendance_`, `salary_`; giữ các key của phần khác.
- Gradle, theme chung và tài liệu hợp đồng: Thọ điều phối thay đổi và thông báo phần ảnh hưởng.

Mỗi người tự commit/push phần việc thực tế bằng danh tính Git của mình. Khi thiếu API hoặc cần đổi hợp đồng, trao đổi với người phụ trách trước khi sửa. Nhận phần phụ thuộc sau khi đã tích hợp vào `main`, cập nhật branch cá nhân rồi kiểm tra lại.

## 6. Checklist bàn giao chung

- [ ] Thọ bàn giao model, SQLite/API, công thức và dữ liệu mẫu.
- [ ] Hùng bàn giao chấm công, lịch sử tháng, màn hình lương và điều hướng liên quan.
- [ ] Sơn bàn giao đăng nhập, phiên, menu, đăng xuất và quản lý nhân viên.
- [ ] Cả nhóm kiểm tra luồng Admin và Employee, dữ liệu sau khi mở lại ứng dụng và các trường hợp đầu vào sai.
- [ ] Bản tích hợp build được bằng `.\gradlew.bat assembleDebug` tại thư mục gốc dự án.
- [ ] Mỗi người ghi rõ file đã làm, kết quả kiểm tra thực tế, phần còn thiếu và nội dung cần người tiếp theo tiếp nhận.

Chỉ đánh dấu sau khi đã kiểm tra thực tế. Chi tiết nghiệm thu đầy đủ xem [README dự án](docs/README.md).
