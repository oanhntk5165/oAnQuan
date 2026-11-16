import Icon from "./Icon.jsx";

const NAV_ITEMS = [
  { id: "history", label: "Lịch sử", icon: "history" },
  { id: "leaderboard", label: "Xếp hạng", icon: "chart" },
  { id: "guide", label: "Hướng dẫn", icon: "book" },
  { id: "profile", label: "Hồ sơ", icon: "user" },
];

export function AppHeader({
  connected, connectionIssue, auth, activePage, onlineCount, onNavigate, onSignOut,
}) {
  return (
    <header className={`topbar ${auth ? "topbar-authenticated" : ""}`}>
      <button className="brand" onClick={() => onNavigate?.("home")} aria-label="Ô ăn quan - Trang chủ">
        <span className="brand-mark"><Icon name="leaf" size={25} /></span>
        <span className="brand-name">ô ăn quan<span className="brand-dot">.</span></span>
      </button>
      <div className="topbar-right">
        <span className={`connection-indicator ${connected ? "is-online" : connectionIssue ? "has-issue" : ""}`}
          role="status" title={connected ? "Đã kết nối máy chủ" : connectionIssue ? "Mất kết nối máy chủ" : "Đang kết nối máy chủ"}
          aria-label={connected ? "Đã kết nối máy chủ" : connectionIssue ? "Mất kết nối máy chủ" : "Đang kết nối máy chủ"}>
          <i />
        </span>
        {auth && (
          <div className="player-nav">
            <div className="player-nav-identity">
              <span className="player-nav-avatar">{auth.username?.slice(0, 1).toUpperCase()}</span>
              <div className="player-nav-user-info">
                <strong>{auth.username}</strong>
                <small>{auth.points ?? 0} điểm · {auth.tier || "Tân thủ"}</small>
              </div>
            </div>
            <nav className="player-nav-links" aria-label="Điều hướng chính">
              {NAV_ITEMS.map((item) => (
                <button key={item.id} className={`player-nav-link ${activePage === item.id ? "is-active" : ""}`}
                  onClick={() => onNavigate(item.id)}
                  aria-label={item.label} title={item.label}
                  aria-current={activePage === item.id ? "page" : undefined}>
                  <Icon name={item.icon} size={17} /><span>{item.label}</span>
                </button>
              ))}
              <button className="player-nav-link player-nav-exit" onClick={onSignOut}
                aria-label="Thoát" title="Thoát">
                <Icon name="logout" size={17} /><span>Thoát</span>
              </button>
            </nav>
          </div>
        )}
        {auth && (
          <span className="online-count" title="Số tài khoản đang trực tuyến">
            <Icon name="people" size={16} /><strong>{onlineCount ?? "–"}</strong>
          </span>
        )}
      </div>
    </header>
  );
}

export function PlayerDashboard({ auth, activePage, onlineCount, onNavigate, onSignOut }) {
  return (
    <div className="home-dashboard">
      <section className="home-player-card" aria-label="Thông tin người chơi">
        <span className="home-player-avatar">{auth.username?.slice(0, 1).toUpperCase()}</span>
        <div className="home-player-details">
          <small>NGƯỜI CHƠI</small>
          <strong>{auth.username}</strong>
          <span>{auth.points ?? 0} điểm · {auth.tier || "Tân thủ"}</span>
        </div>
        <span className="home-online-count"><i />{onlineCount ?? "–"} online</span>
      </section>
      <nav className="home-nav-card" aria-label="Điều hướng chính">
        {NAV_ITEMS.map((item) => (
          <button key={item.id} className={`home-nav-link ${activePage === item.id ? "is-active" : ""}`}
            onClick={() => onNavigate(item.id)} aria-current={activePage === item.id ? "page" : undefined}>
            <Icon name={item.icon} size={18} /><span>{item.label}</span>
          </button>
        ))}
        <button className="home-nav-link home-nav-exit" onClick={onSignOut}>
          <Icon name="logout" size={18} /><span>Đăng xuất</span>
        </button>
      </nav>
    </div>
  );
}

export function SubPageHeader({ title, onBack }) {
  return (
    <header className="subpage-header">
      <button className="subpage-back" onClick={onBack} aria-label="Quay về trang chủ" title="Quay về trang chủ">
        <Icon name="arrow-left" size={20} />
      </button>
      <h1>{title}</h1>
    </header>
  );
}

export function Toast({ message, onDismiss }) {
  if (!message) return null;
  return (
    <div className="toast" role="status">
      <span>✳</span>{message}
      <button onClick={onDismiss} aria-label="Đóng"><Icon name="close" size={16} /></button>
    </div>
  );
}
