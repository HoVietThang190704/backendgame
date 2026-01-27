# Luồng Authentication (Đăng ký, Đăng nhập, JWT)

## Tổng quan ✨
Tài liệu này tóm tắt luồng **đăng ký**, **đăng nhập**, **refresh token** và **JWT authentication** được hiện thực trong mã nguồn.
Các lớp chính liên quan:
- Controller: `com.nhomgame.web.auth.AuthController`
- Service: `com.nhomgame.service.auth.AuthService`
- JWT helper: `com.nhomgame.service.auth.JwtService`
- HTTP filter: `com.nhomgame.web.auth.JwtAuthFilter`
- Refresh token entity: `com.nhomgame.domain.auth.RefreshToken`
- Exception handler: `com.nhomgame.web.auth.GlobalExceptionHandler`

---

## 1) Đăng ký (Register) ✅
- Endpoint: `POST /api/auth/register`
- DTO: `SignupRequest` (username, email, password, roles...)

Luồng:
1. `AuthController.register` nhận `SignupRequest` (validated via `@Valid`).
2. Gọi `AuthService.register(req)`:
   - Kiểm tra username/email đã tồn tại -> ném `IllegalArgumentException` nếu có.
   - Chuyển các role (nếu có) sang enum `Role`; nếu không có thì mặc định `ROLE_USER`.
   - Mã hóa mật khẩu bằng `PasswordEncoder` và tạo `User`.
   - Lưu `User` bằng `UserRepository`.
3. Trả về `UserResponse` chứa thông tin user (không trả mật khẩu).

Cache:
- `@Caching` trong `register` cập nhật cache `users` dựa trên `#result.id` và `#result.username`.

---

## 2) Đăng nhập (Login) 🔐
- Endpoint: `POST /api/auth/login`
- DTO: `LoginRequest` (username, password)
- Response: `JwtResponse` (accessToken, refreshToken)

Luồng:
1. `AuthController.login` lấy username từ `LoginRequest` và kiểm tra null.
2. Gọi `AuthService.findByUsername(username)` (method có `@Cacheable`).
3. Kiểm tra mật khẩu bằng `AuthService.checkPassword` (so sánh bằng `PasswordEncoder.matches`).
4. Nếu hợp lệ:
   - Tạo access token: `JwtService.generateAccessToken(user)`
     - JWT được ký bằng HMAC256 (secret cấu hình `jwt.secret`) và có:
       - subject = `user.getUsername()`
       - claim `roles` chứa danh sách role
       - `issuedAt` và `expiresAt` (từ `jwt.accessExpirationMs`, mặc định 900000 ms = 15 phút)
   - Tạo refresh token lưu trong DB: `AuthService.createRefreshToken(user.getId(), refreshExpirationDays)`
     - Refresh token ở đây là một UUID ngẫu nhiên, kèm `expiryDate`.
   - Trả `JwtResponse(accessToken, refreshToken)`.

Cache / audit:
- `authenticate` dùng `@CacheEvict(value = "users", key = "#req.username")` để invalidate cache cho user khi login (cập nhật lastLogin), và log thời gian thực hiện.

---

## 3) Refresh token (Làm mới access token) 🔁
- Endpoint: `POST /api/auth/refresh`
- DTO: `TokenRefreshRequest` (refreshToken)
- Response: `JwtResponse` (newAccessToken, newRefreshToken)

Luồng:
1. Từ `AuthController.refresh`, gọi `AuthService.verifyRefreshToken(req.getRefreshToken())`:
   - Tìm refresh token trong DB, ném `IllegalArgumentException` nếu không tìm.
   - Kiểm tra `expiryDate` so với `Instant.now()`, xóa token & ném exception nếu đã hết hạn.
2. Lấy `userId` từ refresh token; nếu `null` trả `400 Bad Request`.
3. Xoay vòng (rotation): `authService.deleteRefreshTokensForUser(userId)` (xóa refresh token cũ), và tạo refresh token mới `createRefreshToken(user.getId(), refreshExpirationDays)`.
4. Tạo access token mới `jwtService.generateAccessToken(user)`.
5. Trả `JwtResponse(newAccess, newRefresh.getToken())`.

Lưu ý bảo mật: hiện tại refresh token là UUID lưu thẳng trong DB. Có thể cân nhắc hash token khi lưu để tăng an toàn.

---

## 4) Logout (Đăng xuất) ⛔
- Endpoint: `POST /api/auth/logout`
- DTO: `TokenRefreshRequest` (refreshToken)

Luồng:
1. `AuthController.logout` gọi `AuthService.verifyRefreshToken(req.getRefreshToken())`.
2. Lấy `userId` từ token (nếu null trả `400`).
3. Xóa tất cả refresh tokens của user: `authService.deleteRefreshTokensForUser(userId)`.
4. Trả `200 OK`.

---

## 5) JWT filter và bảo vệ API 🚦
- `JwtAuthFilter` chạy mỗi request (extends `OncePerRequestFilter`):
  - Lấy header `Authorization`, kiểm tra `Bearer ` prefix.
  - Validate access token với `JwtService.validateToken(token)` (JWT verification).
  - Lấy username từ token `JwtService.getUsernameFromToken(token)`.
  - Nếu username hợp lệ và `SecurityContext` chưa có authentication:
    - Load `User` bằng `authService.findByUsername(username)`.
    - Tạo `UsernamePasswordAuthenticationToken` với `authorities` lấy từ `user.getRoles()` và set vào SecurityContext.
- Kết quả: sau khi filter, `SecurityContext` có `Authentication` chứa username + granted authorities để Spring Security sử dụng.

---

## 6) Xử lý lỗi và validation ⚠️
- Validation input dùng `@Valid` trên DTO (ví dụ `LoginRequest`, `SignupRequest`).
- `GlobalExceptionHandler` (extends `ResponseEntityExceptionHandler`) xử lý `MethodArgumentNotValidException` và `IllegalArgumentException` trả `400` kèm thông tin lỗi.

---

## 7) Cấu hình & tham số quan trọng 🔧
- `jwt.secret` — secret để ký HMAC256
- `jwt.accessExpirationMs` — thời gian sống access token (ms)
- `jwt.refreshExpirationDays` — số ngày tồn tại của refresh token (đọc trong `AuthController` via `@Value`)

---

## 8) Gợi ý bảo mật / cải tiến 💡
- Hash refresh tokens trước khi lưu vào DB để tránh lộ token nếu DB bị truy cập.
- Thêm recycling/blacklist cho refresh token đã bị rotate.
- Xem xét làm refresh token cũng là JWT (với revocation list) nếu cần tích hợp thêm claims.
- Thêm logging/audit cho các sự kiện đăng nhập/refresh/logout.

---

Nếu bạn muốn, tôi có thể:
- Thêm sơ đồ sequence (PlantUML) mô tả flow 🖼️
- Tạo test cases unit/integration cho các luồng trên ✅

---

_Đã đọc mã nguồn: `AuthController`, `AuthService`, `JwtService`, `JwtAuthFilter`, `RefreshToken`, `GlobalExceptionHandler`_