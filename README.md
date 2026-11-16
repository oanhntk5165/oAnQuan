# Ô ăn quan — Backend

Backend Java 17 dùng Spring Boot cho WebSocket, đồng thời giữ TCP JSON Lines cho client hiện có. JDBC kết nối MySQL. Mỗi WebSocket frame văn bản chứa một JSON object; phản hồi/sự kiện cũng là JSON object. Phòng và trận đang chạy ở trong bộ nhớ; tài khoản, điểm, rating, lịch sử, nước đi và chat được lưu trong MySQL.

## Cấu trúc dự án

Tại `E:\Downloads\oAnQuan`, backend và frontend được đặt ở hai thư mục ngang hàng:

```text
E:\Downloads\oAnQuan\
├── backend/
│   ├── database/
│   │   ├── schema.sql             # Tạo cơ sở dữ liệu và các bảng
│   │   └── migrations/            # Migration cho schema cũ
│   ├── src/main/java/             # Mã nguồn Java
│   ├── src/test/java/             # Unit test và kiểm thử TCP/WebSocket
│   ├── .mvn/wrapper/              # Cấu hình Maven Wrapper
│   ├── mvnw, mvnw.cmd             # Maven Wrapper
│   ├── pom.xml                    # Cấu hình Maven
│   └── README.md
└── frontend/
    ├── src/
    │   ├── components/            # Các phần giao diện: đăng nhập, sảnh, bàn cờ
    │   ├── hooks/                 # Kết nối WebSocket và trạng thái game
    │   ├── utils/                 # Hàm tiện ích dùng lại
    │   ├── App.jsx                # Ghép các màn hình chính
    │   ├── config.js              # Cấu hình kết nối WebSocket
    │   ├── main.jsx               # Điểm khởi chạy React
    │   ├── styles/                # Styles chia theo thành phần: theme, lobby, game, pages, responsive
    │   │   ├── theme.css          # Biến màu, reset, layout chung
    │   │   ├── lobby.css          # Chế độ lobby và lựa chọn phòng
    │   │   ├── game.css           # Bàn cờ, ô quan, tương tác trận đấu
    │   │   ├── pages.css          # Profile, history, leaderboard, guide
    │   │   └── responsive.css     # Media queries cho mobile
    │   └── styles.css             # File compatibility shim, không chứa style chính nữa
    ├── index.html
    └── package.json               # Scripts và dependency của Vite
```

Trong backend, `src/main/java/com/ltm/oanquan/` chia thành `common/` (lỗi nghiệp vụ, băm mật khẩu), `dao/` (MySQL/JDBC), `engine/` (luật chơi), `model/` (dữ liệu), và `server/` (TCP/WebSocket, client, trận đấu). `target/` được Maven tạo khi biên dịch/kiểm thử; đây là thư mục đầu ra, không phải mã nguồn.

Spring Boot khởi chạy tại `com.ltm.oanquan.OAnQuanApplication`. `SpringWebSocketHandler` và `TcpTransportServer` chuyển tiếp yêu cầu của hai giao thức đến `ClientHandler`; `AccountService` cùng các DAO xử lý tài khoản và dữ liệu lưu trữ; `GameService` điều phối phòng, ghép trận và đồng hồ; `engine` chứa luật chơi độc lập với kết nối mạng.

## Chạy backend trên Windows

### 1. Cài đặt yêu cầu

- **JDK 17 trở lên**: `java -version` phải hiển thị phiên bản 17+.
- **MySQL 8 trở lên** đang chạy. Cần có lệnh `mysql` trong `PATH` nếu muốn chạy các lệnh SQL bên dưới; nếu không, có thể mở file schema bằng MySQL Workbench.
- Kết nối Internet ở lần chạy Maven đầu tiên để Maven Wrapper tải Maven và các thư viện cần thiết.

Mở PowerShell và vào thư mục backend:

```powershell
Set-Location E:\Downloads\oAnQuan\backend
java -version
```

### 2. Tạo database và tài khoản MySQL

Chạy file schema để tạo database `oanquan` cùng các bảng:

```powershell
cmd /c "mysql -u root -p < database\schema.sql"
```

Nhập mật khẩu MySQL của tài khoản `root` khi được hỏi. Lệnh này chỉ cần chạy lần đầu; schema dùng `IF NOT EXISTS`, nên có thể chạy lại an toàn để bổ sung bảng còn thiếu.

Tạo tài khoản riêng cho ứng dụng và cấp quyền trên database:

```powershell
mysql -u root -p -e "CREATE USER IF NOT EXISTS 'oanquan_app'@'localhost' IDENTIFIED BY 'CHANGE_THIS_PASSWORD'; GRANT ALL PRIVILEGES ON oanquan.* TO 'oanquan_app'@'localhost';"
```

Thay `CHANGE_THIS_PASSWORD` bằng mật khẩu của bạn và dùng chính mật khẩu đó ở bước kế tiếp. Không dùng tài khoản `root` để chạy ứng dụng.

Nếu `oanquan_app` đã tồn tại và bạn muốn đặt lại mật khẩu, chạy thêm:

```powershell
mysql -u root -p -e "ALTER USER 'oanquan_app'@'localhost' IDENTIFIED BY 'CHANGE_THIS_PASSWORD';"
```

> Nếu đã có database từ phiên bản schema cũ, sao lưu database trước rồi mới chạy migration:
>
> ```powershell
> cmd /c "mysql -u root -p oanquan < database\migrations\001_tcp_backend.sql"
> ```
>
> Nếu database đã chạy migration `001_tcp_backend.sql`, chạy tiếp migration để lưu nước tự đi do bỏ lượt/hết giờ:
>
> ```powershell
> cmd /c "mysql -u root -p oanquan < database\migrations\002_auto_move_actions.sql"
> ```
>
> Migration `001` chỉ chạy một lần trên mỗi database cũ. Database mới tạo từ `schema.sql` đã có schema mới và không cần chạy migration `002`.

### 3. Cấu hình kết nối và khởi động server

Đặt URL (`url`), username (`user`) và mật khẩu (`password`) MySQL trong `src/main/resources/application.properties`. Mật khẩu phải khớp với tài khoản MySQL. Khởi động ứng dụng Spring Boot:

```cmd
cd /d E:\Downloads\oAnQuan\backend
mvnw.cmd spring-boot:run
```

Hoặc đóng gói và chạy file JAR:

```cmd
mvnw.cmd package
java -jar target\oanquan-1.0.jar
```

Lần chạy đầu Maven có thể mất thêm thời gian để tải dependency. Backend sẵn sàng khi Spring Boot và hai listener TCP/WebSocket khởi động; **giữ cửa sổ này mở**. Nhấn `Ctrl+C` để dừng.

> Chỉ nên dùng `root` cho phát triển trên máy cá nhân. Môi trường dùng chung hoặc triển khai thật nên dùng tài khoản ứng dụng riêng `oanquan_app` với quyền giới hạn trên database `oanquan`.

Mặc định server mở hai cổng:

| Kết nối | Mặc định | Biến môi trường đổi cổng |
|---|---:|---|
| TCP JSON Lines | `5050` | `OANQUAN_PORT` |
| WebSocket cho React (Spring Boot) | `8080` | `OANQUAN_WS_PORT` |

WebSocket tiếp tục dùng URL gốc `ws://localhost:8080`. Ví dụ đổi cổng trong PowerShell trước khi khởi chạy: `$env:OANQUAN_WS_PORT = "8081"`. Có thể cấu hình các biến `OANQUAN_TIME_MS` và `OANQUAN_RECONNECT_GRACE_MS` cho thời gian lượt và thời gian kết nối lại.

### 4. Chạy frontend (tùy chọn)

Backend có thể chạy riêng. Để mở giao diện React, giữ backend chạy và mở **một cửa sổ PowerShell khác**:

```powershell
Set-Location E:\Downloads\oAnQuan\frontend
npm install
npm run dev
```

Mở URL được Vite hiển thị, thường là `http://localhost:5173`. Frontend mặc định kết nối WebSocket tới cùng hostname với trang web ở cổng `8080` (ví dụ mở giao diện tại `http://192.168.1.13:5173` thì kết nối `ws://192.168.1.13:8080`). Nếu truy cập từ máy khác, bảo đảm máy chạy backend cho phép kết nối vào cổng WebSocket trong Windows Firewall. Có thể chỉ định URL cụ thể bằng `VITE_WS_URL` trong file `E:\Downloads\oAnQuan\frontend\.env.local`.

### Kiểm thử backend

Từ `E:\Downloads\oAnQuan\backend`, chạy:

```powershell
.\mvnw.cmd test
```

Lệnh này chạy test và không cần kết nối MySQL để kiểm tra các thành phần được test. Để đóng gói JAR:

```powershell
.\mvnw.cmd package
```

### Cấu hình MySQL

Backend chỉ đọc cấu hình kết nối từ `src/main/resources/application.properties`. Cập nhật `url`, `user` và `password` tại file này nếu thông tin MySQL thay đổi; không cần tạo file cấu hình CSDL khác. Không commit mật khẩu thật lên kho mã nguồn.

### Lỗi thường gặp

- **`Access denied for user`**: kiểm tra `user` và `password` trong `src/main/resources/application.properties` có khớp tài khoản MySQL không, và tài khoản đó có quyền trên database `oanquan` không.
- **`Database request failed: Loi khi them nguoi dung`**: xem phần sau `SQLState=...`, `errorCode=...` trong console backend để biết lỗi MySQL cụ thể. `errorCode=1142` thường là thiếu quyền; `1048` là thiếu giá trị bắt buộc; `1054`/`1146` thường là sai hoặc thiếu cột/bảng do schema chưa đúng phiên bản. Tạo/sửa tài khoản và cấp quyền như bước 2; nếu schema cũ thì sao lưu rồi chạy migration như hướng dẫn ở bước đó.
- **`Communications link failure` / không kết nối được MySQL**: đảm bảo dịch vụ MySQL đang chạy và URL dùng đúng host/cổng (mặc định `localhost:3306`).
- **Không thể bind cổng `5050` hoặc `8080`**: cổng có thể đang được ứng dụng khác sử dụng; đổi tương ứng bằng `OANQUAN_PORT` hoặc `OANQUAN_WS_PORT`.
- **Frontend báo không kết nối được WebSocket**: kiểm tra backend đã khởi động và frontend đang dùng đúng URL/cổng WebSocket.

## Giao thức client

### React / WebSocket

Kết nối tới `ws://localhost:8080` (hoặc hostname của máy chạy backend khi truy cập từ thiết bị khác). Gửi mỗi yêu cầu dưới dạng một frame văn bản JSON, theo cùng schema `action` và trường như bảng dưới; server trả frame JSON `ok` hoặc `error` và gửi sự kiện bất đồng bộ trên chính kết nối đó.

```js
const socket = new WebSocket("ws://localhost:8080");
socket.onmessage = ({ data }) => {
  const message = JSON.parse(data);
  if (message.type === "event") {
    console.log(message.event, message.data);
  }
};
socket.onopen = () => {
  socket.send(JSON.stringify({ action: "login", username: "player1", password: "password123" }));
};
```

Sau khi kết nối lại, đăng nhập lại hoặc gửi `token` trong các yêu cầu cần xác thực. Mỗi frame yêu cầu có giới hạn 16.384 ký tự; chỉ hỗ trợ frame văn bản JSON. Khi triển khai web công khai, đặt WebSocket sau TLS (`wss://`) và chỉ mở cổng cần thiết.

## Chạy giao diện React

Chạy backend trong một terminal như hướng dẫn ở trên. Trong terminal khác, chạy frontend tại `E:\Downloads\oAnQuan\frontend`:

```powershell
Set-Location E:\Downloads\oAnQuan\frontend
npm install
npm run dev
```

Mở URL Vite hiển thị trong terminal (thường là `http://localhost:5173`). Mặc định frontend kết nối `ws://localhost:8080`; nếu backend ở địa chỉ khác, tạo file `E:\Downloads\oAnQuan\frontend\.env.local` với nội dung `VITE_WS_URL=ws://localhost:8080` rồi khởi động lại Vite. Giao diện ban đầu dùng bảng màu kem, xanh lá, vàng, cam, đỏ, kiwi và teal từ mẫu màu đã cung cấp.

### TCP

Client kết nối TCP tới `<host>:5050`, gửi từng dòng JSON, và đọc phản hồi từng dòng. Mỗi yêu cầu phải có `action`. Phản hồi đồng bộ có dạng:

```json
{"type":"ok","data":{"status":"..."}}
{"type":"error","message":"..."}
```

Sự kiện bất đồng bộ có dạng `{"type":"event","event":"...","data":{...}}`. Sau khi đăng nhập, có thể tiếp tục dùng kết nối đó hoặc gửi `token` trong mỗi yêu cầu cần đăng nhập. Token tồn tại 12 giờ trong bộ nhớ server.

### Tài khoản và dữ liệu người chơi

| `action` | Các trường | Quyền |
|---|---|---|
| `register` | `username`, `password` | Công khai |
| `login` | `username`, `password` | Công khai; trả `token` |
| `profile` | `token` tùy chọn | Đăng nhập |
| `leaderboard` | `limit` tùy chọn (mặc định 20, tối đa 100) | Công khai |
| `history` | `token` tùy chọn, `limit` tùy chọn (mặc định 50, tối đa 100) | Đăng nhập |
| `match_detail` | `token` tùy chọn, `matchId` | Người tham gia trận |
| `online_count` | Không | Công khai; đếm tài khoản đăng nhập đang kết nối |
| `logout` | Không | Đăng xuất kết nối hiện tại |

Tên đăng nhập dài 3–20 ký tự, gồm chữ ASCII, chữ số hoặc `_`; mật khẩu dài 6–64 ký tự. Mật khẩu lưu bằng PBKDF2. `points` là tổng điểm trận tích lũy; `rating` là Elo riêng, bắt đầu ở 1000 và cập nhật sau trận xếp hạng (K=32). Bảng xếp hạng sắp theo rating, rồi điểm.

Ví dụ đăng ký/đăng nhập:

```json
{"action":"register","username":"player1","password":"password123"}
{"action":"login","username":"player1","password":"password123"}
```

### Chế độ và điều khiển trận

| `action` | Các trường | Chức năng |
|---|---|---|
| `create_bot` | `token` tùy chọn | Tạo ván luyện với bot mức ngẫu nhiên/người mới |
| `create_room` | `token` tùy chọn | Tạo phòng bạn bè; phản hồi có mã 6 ký tự |
| `join_room` | `code`, `token` tùy chọn | Tham gia phòng đang chờ |
| `find_match` | `token` tùy chọn | Vào hàng chờ xếp hạng |
| `cancel_matchmaking` | `token` tùy chọn | Rời hàng chờ |
| `move` | `position` (0–11), `direction` (`1` hoặc `-1`), `token` tùy chọn | Thực hiện nước rải hợp lệ |
| `pass` | `token` tùy chọn | Hệ thống tự đi một nước; người chơi thua khi chủ động bỏ lượt lần thứ hai |
| `resign` | `token` tùy chọn | Đầu hàng |
| `state` | `code`, `token` tùy chọn | Yêu cầu gửi lại trạng thái |
| `reconnect` | `code`, `token` tùy chọn | Gắn lại kết nối với trận đang diễn ra |
| `chat` | `content` (1–500 ký tự), `token` tùy chọn | Gửi chat trong trận |
| `cancel_room` | `code`, `token` tùy chọn | Chủ phòng hủy phòng đang chờ |

Các ô được đánh số theo luật engine hiện có: `0` và `6` là ô quan; ô dân người 0 là `1–5`, người 1 là `7–11`. Server áp dụng luật rải/ăn/bù dân trong `engine.Rules`. Đấu hạng có 20 giây mỗi lượt; khi người chơi bỏ lượt hoặc hết giờ, hệ thống tự chọn một nước đi hợp lệ. Bỏ lượt chủ động lần thứ hai sẽ xử thua; hết giờ tự đi không tính là bỏ lượt. Khi mất kết nối, đồng hồ tạm dừng và người chơi có 45 giây để gửi `reconnect` với token đăng nhập và mã trận.

Sự kiện chính: `room_waiting`, `matchmaking_queued`, `match_found`, `match_started`, `state`, `chat`, `player_disconnected`, `player_reconnected`, `match_ended`. Payload `state` có bàn 12 ô (`dan`, `quan`), điểm, người đến lượt, thời gian còn lại, số lần bỏ lượt, mã trận và kết quả nếu đã kết thúc. `match_detail` trả lại metadata cùng danh sách nước đi và trạng thái bàn sau mỗi nước.

## Ghi chú triển khai

- Phòng, token và trận đang chạy chỉ lưu trong bộ nhớ; khởi động lại server sẽ kết thúc các phiên này. Lịch sử trận đã hoàn tất vẫn lưu trong MySQL.
- Ghép trận hiện ghép hai người đang chờ theo thứ tự vào hàng; rating dùng để cập nhật kết quả và xếp hạng, chưa giới hạn khoảng rating khi ghép.
- Số người trực tuyến đếm tài khoản đã đăng nhập có kết nối TCP/WebSocket đang mở; nhiều kết nối của cùng tài khoản chỉ tính một người.
- Chỉ người tham gia trận xem được chi tiết và chat của trận đó.
- TCP và WebSocket không tự mã hóa dữ liệu; chỉ triển khai qua mạng tin cậy hoặc đặt sau lớp TLS khi truy cập qua Internet.
- Backend cung cấp WebSocket qua Spring Boot cho frontend React; TCP JSON Lines vẫn được giữ cho client cũ.
