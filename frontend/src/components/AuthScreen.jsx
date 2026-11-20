import { useState } from "react";
import Icon from "./Icon.jsx";

export default function AuthScreen({ connected, connectionIssue, onAuthenticate }) {
  const [formMode, setFormMode] = useState("login");
  const [form, setForm] = useState({ username: "", password: "" });
  const [busy, setBusy] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  async function submit(event) {
    event.preventDefault();
    if (busy || !connected) return;
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
      <section className="auth-card auth-card-full" aria-label={formMode === "login" ? "Đăng nhập" : "Đăng ký"}>
        <svg className="auth-frame" viewBox="270 90 910 910" preserveAspectRatio="none" aria-hidden="true"><image href="/images/khung-dang-nhap.svg" width="1448" height="1086" /></svg>
        <div className="auth-card-top"><span className="tiny-label">Sẵn sàng chưa?</span></div>
        <h2>{formMode === "login" ? "Chào mừng bạn quay lại!" : "Mời bạn vào hội!"}</h2>
        <svg className="auth-title-flourish" viewBox="0 0 320 28" aria-hidden="true"><path d="M2 15C84-15 164 8 218 15c25 3 39 1 44-7-1 20-39 20-72 12C121 3 62 1 2 15Zm258-5c20-8 42-2 58 5-22-4-35-7-58-5Z" fill="currentColor" /></svg>
        <p className="auth-subtitle">{formMode === "login" ? "Đăng nhập để tiếp tục cuộc vui." : "Tạo tài khoản mới, chỉ mất một phút."}</p>
        {!connected && <p className="auth-connection-status" role="status">{connectionIssue ? "Mất kết nối máy chủ. Đang thử kết nối lại…" : "Đang kết nối máy chủ…"}</p>}
        <form className="auth-form" onSubmit={submit}>
          <label>Tên người chơi
            <span className="auth-input"><Icon name="user" size={25} /><input autoComplete="username" required minLength={3} maxLength={20} value={form.username} disabled={busy}
              onChange={(event) => setForm({ ...form, username: event.target.value })} placeholder="Ví dụ: banbe_01" /></span>
          </label>
          <label>Mật khẩu
            <span className="auth-input"><Icon name="lock" size={25} /><input autoComplete={formMode === "login" ? "current-password" : "new-password"} required minLength={6} maxLength={64} disabled={busy}
              type={showPassword ? "text" : "password"} value={form.password} onChange={(event) => setForm({ ...form, password: event.target.value })}
              placeholder="Ít nhất 6 ký tự" />
              <button type="button" className="auth-password-toggle" onClick={() => setShowPassword(!showPassword)} aria-label={showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"} aria-pressed={showPassword}><Icon name={showPassword ? "eye" : "eye-off"} size={25} /></button>
            </span>
          </label>
          <button className="button button-primary auth-submit" disabled={!connected || busy}>
            {busy ? "Đang xử lý..." : formMode === "login" ? "Vào chơi thôi" : "Tạo tài khoản"}
            <Icon name="arrow-right" size={28} />
          </button>
        </form>
        <div className="auth-switch">
          {formMode === "login" ? "Chưa có tài khoản?" : "Đã có tài khoản?"}
          <button disabled={busy} onClick={() => { setFormMode(formMode === "login" ? "register" : "login"); setShowPassword(false); }}>
            {formMode === "login" ? "Đăng ký nhé" : "Đăng nhập"}
          </button>
        </div>
        <div className="auth-foot"><Icon name={formMode === "login" ? "book" : "leaf"} size={26} />{formMode === "login" ? "Cùng vui, chơi đẹp, chơi hết mình." : "Chơi vui, chơi đẹp, chơi hết mình."}</div>
      </section>
    </div>
  );
}
