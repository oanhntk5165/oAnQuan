import { useEffect, useState } from "react";
import Icon from "./Icon.jsx";
import { PlayerDashboard } from "./AppChrome.jsx";

const MODES = [
  { id: "quick", title: "Chơi ngay", desc: "Đấu với máy", icon: "play" },
  { id: "local", title: "Hai người", desc: "Cùng chơi trên một máy", icon: "people" },
  { id: "ranked", title: "Đấu hạng", desc: "Tìm đối thủ và tăng thứ hạng", icon: "trophy" },
];

export default function Lobby({
  auth, activePage, onlineCount, onNavigate, onSignOut,
  queueing, roomPlayers = [], onAction, onStartLocal, onCancelRoom,
}) {
  const [selected, setSelected] = useState("quick");
  const [firstName, setFirstName] = useState("Người chơi 1");
  const [secondName, setSecondName] = useState("Người chơi 2");
  const [firstTurn, setFirstTurn] = useState("0");
  const [panel, setPanel] = useState("");
  const [code, setCode] = useState("");
  const [roomCode, setRoomCode] = useState("");
  const [roomSeconds, setRoomSeconds] = useState(60);
  const [roomHost, setRoomHost] = useState(true);

  useEffect(() => {
    if (!queueing) return undefined;
    const timer = window.setTimeout(() => { void onAction("cancel_matchmaking"); }, 30000);
    return () => window.clearTimeout(timer);
  }, [queueing, onAction]);

  useEffect(() => {
    if (panel !== "create" && panel !== "room_waiting") return undefined;
    const timer = window.setInterval(() => setRoomSeconds((seconds) => Math.max(0, seconds - 1)), 1000);
    return () => window.clearInterval(timer);
  }, [panel, roomCode]);

  useEffect(() => {
    if ((panel === "create" || panel === "room_waiting") && roomSeconds === 0) {
      setPanel("ranked");
      setRoomCode("");
    }
  }, [panel, roomSeconds]);

  function start() {
    if (selected === "quick") void onAction("create_bot", { goFirst: true });
    if (selected === "local") setPanel("local");
    if (selected === "ranked") setPanel("ranked");
  }
  function createRoom() {
    onAction("create_room")?.then((result) => {
      if (!result?.code) return;
      setRoomCode(result.code);
      setRoomSeconds(Math.ceil((result.expiresInMs ?? 60_000) / 1000));
      setRoomHost(true);
      setPanel("create");
    });
  }
  function joinRoom() {
    if (!code.trim()) return;
    void onAction("join_room", { code: code.trim().toUpperCase() }).then((result) => {
      if (!result?.code) return;
      setRoomCode(result.code);
      setRoomSeconds(Math.ceil((result.expiresInMs ?? 60_000) / 1000));
      setRoomHost(false);
      setPanel("room_waiting");
    });
  }

  return <div className="lobby redesigned-lobby">
    <PlayerDashboard auth={auth} activePage={activePage} onlineCount={onlineCount}
      onNavigate={onNavigate} onSignOut={onSignOut} />
    <div className="mode-choice-grid">{MODES.map((mode) => <button key={mode.id}
      className={`mode-choice-card ${selected === mode.id ? "selected" : ""}`} onClick={() => setSelected(mode.id)}
      aria-pressed={selected === mode.id}>
      <span className="lobby-mode-icon"><Icon name={mode.icon} size={23} /></span>
      <strong>{mode.title}</strong><small>{mode.desc}</small>
    </button>)}</div>
    <button className="button button-green lobby-start" onClick={start}>{MODES.find((mode) => mode.id === selected)?.title}</button>

    {panel === "local" && <div className="match-dialog-backdrop"><section className="match-dialog setup-card local-setup-card has-back-button">
      <button className="card-back-button" onClick={() => setPanel("")} aria-label="Quay lại" title="Quay lại">
        <Icon name="arrow-left" size={19} />
      </button>
      <h2>Chơi với bạn</h2>
      <div className="local-setup-player-grid">

        <label className="local-setup-player-card local-player-one">
          <strong>Người chơi 1 — hàng trên</strong>
          <input aria-label="Tên người chơi 1" value={firstName} onChange={(e) => setFirstName(e.target.value)}
            maxLength={24} placeholder="Nhập tên người chơi 1" />
        </label>
        <label className="local-setup-player-card local-player-two">
          <strong>Người chơi 2 — hàng dưới</strong>
          <input aria-label="Tên người chơi 2" value={secondName} onChange={(e) => setSecondName(e.target.value)}
                 maxLength={24} placeholder="Nhập tên người chơi 2" />
        </label>
      </div>
      <section className="local-first-turn" aria-labelledby="local-first-turn-title">
        <h3 id="local-first-turn-title">Ai đi trước?</h3>
        <div className="local-first-turn-options" role="group" aria-labelledby="local-first-turn-title">
          <button type="button" className={firstTurn === "0" ? "is-selected" : ""}
            aria-pressed={firstTurn === "0"} onClick={() => setFirstTurn("0")}>Người chơi 1</button>
          <button type="button" className={firstTurn === "1" ? "is-selected" : ""}
            aria-pressed={firstTurn === "1"} onClick={() => setFirstTurn("1")}>Người chơi 2</button>
        </div>
      </section>
      <button className="button button-primary local-start-game"
        onClick={() => { onStartLocal([firstName || "Người chơi 1", secondName || "Người chơi 2"], Number(firstTurn)); setPanel(""); }}>
        Bắt đầu
      </button>
    </section></div>}

    {panel === "ranked" && <div className="match-dialog-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) setPanel(""); }}><section className="ranked-overlay-card has-back-button">
      <button className="card-back-button" onClick={() => setPanel("")} aria-label="Quay lại" title="Quay lại">
        <Icon name="arrow-left" size={19} />
      </button>
      <h2 className="ranked-overlay-title">Chơi online</h2>
      <div className="ranked-dashboard">
      <section className="rank-profile-card" aria-label="Thông tin người chơi">
        <span className="rank-avatar">{auth.username?.slice(0, 1).toUpperCase()}</span>
        <span className="eyebrow">NGƯỜI CHƠI</span>
        <h2>{auth.username}</h2>
        <strong>{auth.points ?? 0} điểm</strong>
        <span className="rank-badge">{auth.tier || "Tân thủ"}{auth.level ? ` · Cấp ${auth.level}` : ""}</span>
      </section>
      <section className="rank-play-card"><h2>Đấu xếp hạng</h2>
        <button className="button button-green rank-find-opponent" onClick={() => void onAction(queueing ? "cancel_matchmaking" : "find_match")}>{queueing ? "Đang tìm đối thủ…" : "Tìm đối thủ"}</button>
        {queueing && <small className="queue-note">Đang tìm trong 30 giây. Hết thời gian sẽ quay lại sảnh đấu hạng.</small>}
        <div className="rank-room-actions"><button className="rank-create-room" onClick={createRoom}>Tạo phòng</button><button className="rank-join-room" onClick={() => setPanel("join")}>Nhập mã phòng</button></div>
        <small className="rank-rules">Luật đấu: 20 giây mỗi lượt · hết giờ 2 lần là thua · mất kết nối 45 giây là thua.</small>
      </section>
      </div>
    </section></div>}
    {(panel === "join" || panel === "create" || panel === "room_waiting") && <div className="match-dialog-backdrop"><section className="match-dialog setup-card has-back-button">
      <button className="card-back-button" onClick={() => { setPanel("ranked"); setRoomCode(""); }} aria-label="Quay lại" title="Quay lại">
        <Icon name="arrow-left" size={19} />
      </button>
      <h2>{panel === "join" ? "Vào phòng" : "Phòng chơi"}</h2>
      {panel === "join" ? <label>Mã phòng<input autoFocus value={code} maxLength={6} onChange={(e) => setCode(e.target.value.toUpperCase())} placeholder="Nhập mã 6 ký tự" /></label> : <><p>Mã phòng</p><strong className="room-code-display">{roomCode || "Đang tạo…"}</strong><div className="room-player-slots">{[0, 1].map((slot) => <span key={slot}>{roomPlayers[slot] || (slot === 0 ? auth.username : "Đang chờ đối thủ…")}</span>)}</div><small>Phòng tự huỷ sau {Math.floor(roomSeconds / 60)}:{String(roomSeconds % 60).padStart(2, "0")}.</small></>}
      <div className="match-dialog-actions">{panel === "join" ? <><button onClick={() => setPanel("ranked")}>Huỷ</button><button className="confirm-surrender" onClick={joinRoom} disabled={code.length !== 6}>Vào phòng</button></> : roomHost ? <><button onClick={() => { onCancelRoom(); setPanel("ranked"); setRoomCode(""); }}>Huỷ phòng</button><button className="confirm-surrender" onClick={() => void onAction("start_room", { code: roomCode })} disabled={roomPlayers.length < 2}>Bắt đầu</button></> : <button className="confirm-surrender" disabled>Chờ chủ phòng bắt đầu</button>}</div>
    </section></div>}
  </div>;
}
