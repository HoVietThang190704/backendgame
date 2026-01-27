# Quy tắc (rule.md) ✅

> Tệp này tóm tắt các quy tắc, hướng dẫn build và các lưu ý quan trọng cho dự án **backendgame**.

---

## Tổng quan 🔧
- Kiến trúc: Maven multi-module
- Modules: `domain`, `service`, `infrastructure`, `web`
- Parent: `spring-boot-starter-parent` 3.5.10
- **Java version:** 17

## Build & Chạy (Maven) ⚙️
- Build toàn bộ: `mvn clean install`
- Build module cụ thể: `mvn -pl web -am clean package`
- Chạy ứng dụng web: `cd web && mvn spring-boot:run` (hoặc `java -jar web\target\web-1.0.0.jar`)
- **Chú ý:** trước khi chạy `mvn clean` hoặc `mvn package`, hãy dừng mọi instance đang chạy (ví dụ `java -jar web\target\web-1.0.0.jar` hoặc `mvn spring-boot:run`) để tránh file bị khóa trên Windows.
- Bỏ qua tests khi cần: `mvn -DskipTests clean package`

```powershell
$env:SPRING_PROFILES_ACTIVE='dev'
java -jar web\target\web-1.0.0.jar
```

## Cấu hình môi trường (ENV) 🌐
- File mẫu `.env.example` đã được thêm vào root repo; **KHÔNG** chép mật khẩu thật vào repository.
- Tên biến env mặc định: `MONGODB_URI` (ví dụ trong `.env.example`).
- `web/src/main/resources/application-dev.yml` dùng biến env: `spring.data.mongodb.uri: ${MONGODB_URI:mongodb://localhost:27017/backendgame}`.
- Chạy local dev: đặt biến `MONGODB_URI` trong session hoặc file `.env` rồi chạy `cd web && mvn spring-boot:run`.

## Kiểm thử 🧪
- Chạy test: `mvn test` hoặc `mvn -DskipTests=false test`
- Viết unit tests cho mọi logic quan trọng trước khi tạo PR
- Yêu cầu CI phải pass (build + test) trước khi merge

## Phụ thuộc & Lưu ý kỹ thuật 📦
- `domain` sử dụng `jakarta.persistence` (JPA API)
- Dự án dùng `lombok` (module `domain`) → **Bật Annotation Processing** trong IDE (IntelliJ / Eclipse) để tránh lỗi biên dịch.

### Caching (Caffeine) ⚡️
- Cách dùng: project đã tích hợp **Caffeine** cho môi trường dev (in-memory, không cần infra). Mục đích: giảm truy vấn đọc users và tăng tốc các thao tác đọc lặp.
- Những thay đổi đã áp dụng:
  - `web/pom.xml`: thêm `spring-boot-starter-cache` và `com.github.ben-manes.caffeine:caffeine:3.1.8`
  - `service/pom.xml`: thêm `spring-boot-starter-cache`
  - `WebApplication` đã bật `@EnableCaching`
  - Cấu hình: `com.nhomgame.web.config.CaffeineCacheConfig` (cache tên: `users`, TTL 10 phút, max 1000)
- Cách sử dụng trong code:
  - `@Cacheable(value = "users", key = "#username")` cho `findByUsername`
  - `@Cacheable(value = "users", key = "#id")` cho `findById`
  - `@CachePut` / `@CacheEvict` dùng trong `register` / `authenticate` để đồng bộ cache khi cập nhật user
- Lưu ý vận hành:
  - Cache giúp giảm tải đọc nhưng phải invalidate khi dữ liệu thay đổi (sử dụng `@CacheEvict`/`@CachePut` phù hợp).
  - Đối với mật khẩu: nếu thấy đăng nhập chậm ở trường hợp sai mật khẩu, nguyên nhân thường do BCrypt cost; **chỉ** giảm cost cho profile **dev** (ví dụ `new BCryptPasswordEncoder(8)`) để tăng tốc local tests—không làm vậy ở production.
- Kiểm thử: thêm unit/integration test để verify cache hit/evict (ví dụ với `@SpringBootTest` hoặc Mockito + `CacheManager`).

## Style & Chất lượng mã ✨
- Hiện tại không có config Checkstyle/Spotless trong repo. Áp dụng quy ước sau:
  - Theo **Spring/Google Java Style** (tên biến rõ ràng, camelCase, phương thức ngắn, single responsibility)
  - Format code trước khi commit bằng IDE hoặc tool formatter
  - Viết JavaDoc cho các API public (nếu cần)

> Gợi ý: thêm `maven-checkstyle-plugin` hoặc `spotless` vào parent pom để chuẩn hóa style trong team.

## Git & Quy trình làm việc 🌿
- Branch chính: `main` (bảo vệ, merge qua PR)
- Tạo branch cho feature/fix: `feature/<ticket>-short-desc` hoặc `fix/<ticket>-short-desc`
- Commit message theo cấu trúc ngắn: `type(scope): short description`
  - ví dụ: `feat(service): add matchmaking endpoint`
  - Các `type` phổ biến: `feat`, `fix`, `chore`, `docs`, `test`, `refactor`
- Pull Request: mô tả rõ thay đổi, cách test, liên kết ticket/issue, ít nhất một reviewer

## CI / Release (khuyến nghị) 🚀
- Trên CI: chạy `mvn clean install` và `mvn test` cho toàn bộ modules
- Semantic Versioning cho release: `MAJOR.MINOR.PATCH` (ví dụ `1.0.0`)

## Bảo mật & License 🔐
- Hiện tại (dev): nhóm tạm cho phép truy cập dễ dàng để phát triển (ví dụ: mở IP dev, chia sẻ user dev). Tuy nhiên, **KHÔNG** commit connection string/credentials vào Git.
- Khi chuyển sang giai đoạn production/release, sẽ áp dụng các biện pháp bảo mật cần thiết (kiểm tra dependency vulnerabilities, secrets manager/CI secrets, giới hạn IP access, và xoay credentials định kỳ).

## Ghi chú khác 📌
- Nếu thêm DB migration (Flyway/Liquibase), nên đặt scripts trong module `infrastructure` hoặc một module riêng `migration`.
- Nếu cần Docker, đặt `Dockerfile` ở `web/` và thêm profile `docker` cho spring-boot build.

## Mô tả dữ liệu: `User` (MongoDB) 🧾
Dưới đây là schema gợi ý cho collection `users` (sử dụng MongoDB `ObjectId` cho `_id`):

```json
{
  "_id": ObjectId,
  "username": String,        // unique, indexed
  "email": String,           // unique, indexed
  "passwordHash": String,
  "displayName": String,     // tên hiện thị trong game
  "avatar": String,          // URL hoặc base64 (ưu tiên URL)
  "stats": {
    "totalMatches": Number,
    "wins": Number,
    "losses": Number,
    "draws": Number,
    "winRate": Number,       // (wins/totalMatches)*100 - có thể tính động hoặc lưu trữ
    "totalBombsPlaced": Number,
    "totalBombsFound": Number,
    "totalFlagsPlaced": Number
  },
  "rank": Number,            // ELO rating
  "createdAt": Date,
  "lastLogin": Date,
  "isOnline": Boolean,
  "currentMatchId": ObjectId // null nếu không đang trong trận
}
```

Lưu ý triển khai & vận hành 🔍
- **Chỉ lưu `passwordHash`** (bcrypt/argon2), KHÔNG lưu mật khẩu plain.
- **Index**: tạo index unique cho `username` và `email` (`@Indexed(unique = true)` trong Spring Data).
- **Stats updates**: dùng các phép toán nguyên tử Mongo (`$inc`, `$set`) hoặc cập nhật bằng aggregation pipeline để tránh race condition.
- **Win rate**: có thể tính động khi đọc, hoặc giữ giá trị và cập nhật cùng lúc với `$inc`/tính toán trên server (đảm bảo chính xác bằng cách cập nhật có điều kiện hoặc transaction nếu cần).
- **isOnline / presence**: trạng thái online thường phù hợp lưu ở Redis (presence) hơn là Mongo nếu cần realtime & nhiều instance; nếu lưu trên Mongo, cân nhắc sử dụng TTL cho session hoặc cập nhật định kỳ.
- **currentMatchId**: dùng để biết người chơi đang trong trận; cẩn thận với consistency khi match bị hủy — đảm bảo cập nhật `null` khi kết thúc.
- **ELO/ranking**: cập nhật theo quy tắc ELO tại server-side (đảm bảo atomic hoặc thông qua job serial để tránh tranh chấp đồng thời).
- **Bảo mật & privacy**: tránh lưu dữ liệu nhạy cảm không cần thiết (ví dụ không lưu email nếu không bắt buộc hiển thị), và hạn chế quyền truy cập vào collection trong production.
- **Index & performance**: ngoài index unique, thêm index cho `lastLogin`, `rank` hoặc các trường truy vấn phổ biến để tối ưu đọc.

---

Nếu bạn muốn, tôi sẽ:
1) Thêm một ví dụ `Player`/`User` `@Document` và `UserRepository` trong module `infrastructure`, và
2) Tạo migration script mẫu hoặc index creation script cho MongoDB.
Chọn 1, 2, hoặc cả 2 và tôi sẽ triển khai ngay.