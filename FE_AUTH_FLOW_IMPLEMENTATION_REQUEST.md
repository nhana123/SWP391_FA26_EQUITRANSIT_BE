> UPDATE 2026-10-27: Account EMAIL VERIFICATION is now link-only.
> New verification emails contain only VERIFY NOW (no OTP). FE must not show
> an OTP input or code fallback. Use the captured strong token only after a
> user clicks Verify now; keep resend-link with cooldown and email prefill.
> Verification link lifetime and queued mail expiry are 24 hours. Resend
> invalidates the previous link. Password-reset OTP behavior is unchanged.
> This update supersedes verification-OTP requirements below.

# YÊU CẦU FE: HOÀN THIỆN LUỒNG XÁC THỰC EQUITRANSIT

Bạn là Senior Frontend Developer. Chỉ sửa repo FE EquiTransit. Backend đã có
API đăng ký, xác thực email bằng link/OTP, đăng nhập, refresh cookie, quên mật
khẩu và đổi mật khẩu. Hãy hoàn thành UI và tích hợp theo hợp đồng dưới đây;
không tạo API giả, không tự sinh/kiểm tra OTP và không sửa Backend để né guard.

## 1. Kết quả cần giao

Luồng A: Register -> INACTIVE -> kiểm tra email -> nhập email + OTP hoặc mở
link và bấm xác thực -> ACTIVE -> Login -> workspace đúng role.
Luồng B: Forgot password -> thông báo chung -> OTP + mật khẩu mới, hoặc link
+ mật khẩu mới -> reset thành công -> xóa phiên cũ -> Login bằng mật khẩu mới.
Luồng C: Login bằng mật khẩu đúng nhưng chưa xác thực -> hướng dẫn xác thực
và nút gửi lại mail. Không vào workspace và không tự tạo phiên giả.

Giữ branding xanh lá của EquiTransit. Chỉ thay phần auth, không sửa nghiệp vụ
booking, tài chính hoặc quyền staff. Tái sử dụng API client/session/refresh
hiện có để tránh hai cơ chế xác thực song song.

## 2. API thực tế

Base URL lấy từ VITE_API_BASE_URL. Demo hiện dùng http://localhost:8082.
Đọc Swagger /v3/api-docs khi cần kiểm tra DTO. Không hardcode host production.
Các response thành công dạng message không chứa OTP, token link hoặc mật khẩu.

### POST /api/auth/register
Request:
```json
{
  "email": "customer@example.invalid",
  "password": "ExamplePassword123!",
  "confirmPassword": "ExamplePassword123!",
  "fullName": "Nguyen Van A",
  "phone": null,
  "address": null
}
```
201: tài khoản được tạo INACTIVE và mail được xếp hàng trong cùng transaction.
Đi tới /verify-email, giữ email trong navigation state để điền sẵn. Không gửi
field role/userId/status. 409 EMAIL_ALREADY_REGISTERED: hiện Login và Gửi lại
xác thực, không tự thay mật khẩu hay tạo tài khoản khác.

Validation: email tối đa 255; fullName 1..150; password ít nhất 12 ký tự và tối
đa 72 byte UTF-8; confirm phải giống hệt. Dùng TextEncoder để kiểm tra byte,
không trim mật khẩu. Phone/address không dùng thì gửi null, không gửi chuỗi
rỗng. Không yêu cầu Customer nhập thêm trường không có trong DTO.

### POST /api/auth/resend-verification
```json
{"email":"customer@example.invalid"}
```
202: luôn thông báo chung, không xác nhận email có tồn tại. Cooldown tối thiểu
60 giây, do BE thực thi. Resend thay mã/link cũ nhưng KHÔNG reset số lần sai
hoặc cửa sổ OTP đang hoạt động. Không tự gửi khi page mount/rerender.

### POST /api/auth/verify-email
Cách OTP:
```json
{"email":"customer@example.invalid","token":"123456"}
```
Cách link: {"token":"<43-character token from URL>"}; email có thể bỏ qua.
200: xác thực thành công. Xóa token tạm, hiển thị thành công rồi cho Login.
Không tự đăng nhập hoặc giả định API này trả access token.
400 AUTH_CHALLENGE_INVALID: mã/link sai, hết hạn, đã dùng hoặc hết ngân sách
thử. Hiển thị thông báo chung và lựa chọn yêu cầu mail mới, không đoán số lần
thử còn lại từ client. Không báo tài khoản/email không tồn tại.

### POST /api/auth/login
Request: {"email":"...","password":"..."}.
200 body gồm accessToken, tokenType, expiresIn, role; server đặt refresh cookie
HttpOnly. Dùng credentials: include và session client hiện tại.
401 EMAIL_VERIFICATION_REQUIRED: chỉ xuất hiện sau khi mật khẩu đúng nhưng
Customer chưa xác thực. Đi tới màn xác thực với email và nút Gửi lại.
401 LOGIN_FAILED: thông báo chung sai thông tin hoặc tài khoản không khả dụng.
Không gọi refresh để xử lý lỗi login public; không lưu mật khẩu để thử lại.

### POST /api/auth/forgot-password
Request: {"email":"..."}.
202: thông báo "Nếu tài khoản phù hợp tồn tại, hướng dẫn sẽ được gửi".
Đi tới /reset-password với email trong state để nhập OTP và mật khẩu mới.
Không hiển thị "email không tồn tại" hoặc nói chắc thư đã vào Inbox.

### POST /api/auth/reset-password
Cách OTP:
```json
{
  "email":"customer@example.invalid",
  "token":"123456",
  "password":"ChangedPassword456!",
  "confirmPassword":"ChangedPassword456!"
}
```
Cách link: token=<43-character token> + password + confirmPassword; email không
bắt buộc. 200: mật khẩu đổi thành công, các phiên cũ bị thu hồi, thông báo đổi
mật khẩu được xếp hàng gửi mail. Xóa session/access state và dữ liệu riêng tư
trong query cache, thông báo các tab khác bằng cơ chế session hiện có, về Login.
400 AUTH_CHALLENGE_INVALID: hiển thị lỗi mã/link chung và đường yêu cầu mã mới.
Không gọi /verify-email để kiểm tra OTP reset; hai mã có mục đích khác nhau.
Nếu UI chia step, OTP chỉ thực sự được BE kiểm tra khi gửi reset-password với
mật khẩu mới; không tự báo "mã đã xác thực" trước khi BE trả thành công.

### POST /api/auth/refresh và POST /api/auth/logout
Refresh: body rỗng, credentials: include; tái sử dụng cơ chế refresh đồng thời
hiện có, không tạo nhiều refresh request song song và không retry vô hạn.
Logout: credentials: include; 204. Xóa state và cache phía client phù hợp.
Không đọc/lưu refresh token trong JS hoặc đưa token lên URL.

## 3. Giao diện và hành vi

- /register: form gọn, submit một lần; thành công chuyển sang xác thực email.
- /verify-email không có token link: email + một input OTP sáu số, nút Xác thực,
  gửi lại có cooldown và đổi email quay lại bước phù hợp.
- /verify-email có token link: nút xác nhận rõ ràng; không tự POST lúc mở link,
  tránh email scanner/preview vô tình tiêu thụ link.
- /forgot-password: email, loading, response chung, đường quay lại Login.
- /reset-password không có token link: email + OTP + password/confirm.
- /reset-password có token link: password/confirm, không bắt nhập OTP lần nữa.
- OTP dùng type=text, inputMode=numeric, autoComplete=one-time-code; hỗ trợ paste
  "123 456" thành "123456", bàn phím và trình đọc màn hình. Không dùng number
  input để tránh spinner/mất chữ số. Không tự gửi request theo từng phím.
- Có show/hide password, aria-live cho lỗi, focus trường lỗi đầu tiên, submit
  disabled khi pending. Giữ dữ liệu hợp lý khi lỗi mạng, không tự gọi lại lệnh
  reset/register khi chưa biết request trước đã thành công hay chưa.
- OTP tối đa 10 phút. Resend không đảm bảo thêm trọn 10 phút; không tạo countdown
  giả báo mã vẫn còn hạn. Hiển thị "có hiệu lực tối đa 10 phút"; BE là nguồn
  quyết định. Cooldown client 60 giây chỉ hỗ trợ UX, không thay guard của BE.
- Hiện gợi ý kiểm tra Spam và Gửi lại; 201/202 là tạo tài khoản/xếp hàng, không
  phải cam kết thư đã vào hộp thư.
- Link token được capture rồi xóa ngay khỏi URL/history bằng helper hiện có.
  Không log token, OTP, password hoặc gửi chúng vào analytics. Giới hạn lưu token
  tạm theo vòng đời trang; xóa khi thành công/rời luồng. Đặt Referrer-Policy phù
  hợp (no-referrer) ở trang chứa link nhạy cảm.

## 4. Lỗi và giới hạn

Response lỗi có message, có thể có code và fields.
- 400: validation/mã không hợp lệ; không hiển thị nội dung kỹ thuật SMTP/SQL.
- 401 EMAIL_VERIFICATION_REQUIRED: dẫn về xác thực, không coi là login thành công.
- 401 LOGIN_FAILED: lỗi chung.
- 409 EMAIL_ALREADY_REGISTERED: Login/Gửi lại xác thực.
- 429 AUTH_RATE_LIMITED hoặc 429 không có code: đọc Retry-After (giây), khóa nút
  trong khoảng đó. Không spam retry hoặc reset countdown khi rerender.
- 5xx/lỗi mạng: thông báo tạm thời, cho người dùng chủ động thử lại.

BE giới hạn mặc định 10 request / tài khoản / action / 15 phút cho REGISTER,
LOGIN và RECOVERY (forgot + resend dùng chung action). Counter lưu DB, không
reset bởi reload hay đổi IP. OTP có ngân sách riêng 5 lần sai mỗi cửa sổ.
IP limiter vẫn tồn tại, nên luôn xử lý HTTP 429 kể cả không có code.

Không đưa SMTP password/API key vào FE. Endpoint quản trị
GET /api/manager/account-mail/status chỉ dành Manager, không gọi từ auth public.
SMTP_ACCEPTED không có nghĩa Inbox đã nhận hoặc người dùng đã đọc thư.

## 5. Kiểm thử bắt buộc

1. Register -> OTP với email -> verified -> Login đúng workspace.
2. Register -> link -> user bấm xác nhận -> Login; link dùng lại bị từ chối.
3. Login chưa xác thực đúng password nhận EMAIL_VERIFICATION_REQUIRED.
4. Forgot -> OTP + mật khẩu mới -> Login mới thành công, mật khẩu/phiên cũ bị từ
   chối. Luồng reset bằng link cũng phải hoạt động.
5. Mã sai/hết hạn/đã dùng, mã mục đích khác và thiếu email không thể đi tiếp.
6. Resend cooldown và 429 Retry-After không bị reset khi rerender; không có
   request gửi mail tự động khi mở trang. Không làm lộ sự tồn tại tài khoản.
7. Paste OTP, show/hide password, lỗi UTF-8 >72 byte, loading, keyboard/focus.
8. Token không còn trong URL, không xuất hiện trong log/analytics sau capture;
   cookie refresh được gửi bằng credentials include và không đọc bằng JS.
9. Chạy typecheck, lint, unit test và một E2E với outbox local của BE. Test SMTP
   thật tới hộp thư do người kiểm thử sở hữu chỉ sau khi đã cấu hình SMTP hợp lệ.

Bàn giao source FE hoàn chỉnh, kết quả test và hướng dẫn thử. Không tự merge,
không sửa BE hoặc vô hiệu hóa OTP/rate limit để làm test qua.
