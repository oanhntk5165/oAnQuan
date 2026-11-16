import { useState } from "react";
import Icon from "./Icon.jsx";

export default function AuthScreen({ connected, connectionIssue, wsUrl, onAuthenticate }) {
  const [formMode, setFormMode] = useState("login");
  const [form, setForm] = useState({ username: "", password: "" });
  const [busy, setBusy] = useState(false);

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    try {
      const succeeded = await onAuthenticate(formMode, form);
      if (formMode === "register" && succeeded) {
        setFormMode("login");
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="welcome-layout auth-only">
      <section className="auth-card auth-card-full">
        <div className="auth-card-top"><span className="sun-icon">✳</span><span className="tiny-label">SẴN SÀNG CHƯA?</span></div>
        <h2>{formMode === "login" ? "Chào bạn quay lại!" : "Mời bạn vào hội!"}</h2>
        <p className="auth-subtitle">{formMode === "login" ? "Đăng nhập để tiếp tục cuộc vui." : "Tạo tài khoản mới, chỉ mất một phút."}</p>
        {!connected && <div className="connection-help" role="status">
          <strong>Chưa kết nối được tới backend.</strong>
          <span>Đăng ký cần backend Java và MySQL đang chạy.</span>
          <code>{wsUrl}</code>
          <span>Chạy `.\mvnw.cmd exec:java "-Dexec.mainClass=com.ltm.oanquan.server.TcpGameServer"` trong thư mục backend.</span>
        </div>}
        <form className="auth-form" onSubmit={submit}>
          <label>Tên người chơi
            <input autoComplete="username" required minLength={3} maxLength={20} value={form.username}
              onChange={(event) => setForm({ ...form, username: event.target.value })} placeholder="Ví dụ: banbe_01" />
          </label>
          <label>Mật khẩu
            <input autoComplete={formMode === "login" ? "current-password" : "new-password"} required minLength={6} maxLength={64}
              type="password" value={form.password} onChange={(event) => setForm({ ...form, password: event.target.value })}
              placeholder="Ít nhất 6 ký tự" />
          </label>
          <button className="button button-primary auth-submit" disabled={!connected || busy}>
            {busy ? "Đang xử lý..." : formMode === "login" ? "Vào chơi thôi" : "Tạo tài khoản"}
            <Icon name="arrow" size={17} />
          </button>
        </form>
        <div className="auth-switch">
          {formMode === "login" ? "Chưa có tài khoản?" : "Đã có tài khoản?"}
          <button onClick={() => setFormMode(formMode === "login" ? "register" : "login")}>
            {formMode === "login" ? "Đăng ký nhé" : "Đăng nhập"}
          </button>
        </div>
        <div className="auth-foot"><span>✦</span> Chơi vui, chơi đẹp, chơi hết mình.</div>
      </section>
    </div>
  );
}
