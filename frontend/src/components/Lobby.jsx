import { useEffect, useState } from "react";
import Icon from "./Icon.jsx";

const MODES = [
  { id: "quick", title: "Chơi với máy", action: "Chơi ngay", desc: "Đấu với máy và luyện tập", icon: "play" },
  { id: "local", title: "Chơi 2 người", action: "Chơi 2 người", desc: "Cùng chơi trên một máy", icon: "people" },
  { id: "ranked", title: "Đấu hạng", desc: "Tìm đối thủ và tăng thứ hạng", icon: "trophy" },
];

export default function Lobby({
  auth, onPanelChange,
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
  const [copyNotice, setCopyNotice] = useState("");

  async function copyRoomCode() {
    try {
      await navigator.clipboard.writeText(roomCode);
      setCopyNotice("Đã sao chép mã phòng");
    } catch {
      setCopyNotice("Bạn có thể chọn mã phòng để sao chép.");
    }
  }

  useEffect(() => { onPanelChange?.(Boolean(panel)); return () => onPanelChange?.(false); }, [panel, onPanelChange]);

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
    <div className="mode-choice-grid">{MODES.map((mode) => <button key={mode.id}
      className={`mode-choice-card ${selected === mode.id ? "selected" : ""}`} onClick={() => setSelected(mode.id)}
      aria-pressed={selected === mode.id}>
      <span className="lobby-mode-icon"><Icon name={mode.icon} size={23} /></span>
      <strong>{mode.title}</strong><small>{mode.desc}</small>
    </button>)}</div>
    <div className="lobby-board-preview" aria-hidden="true">
      <svg viewBox="0 370 1448 390" preserveAspectRatio="none"><image href="/images/broad.svg" width="1448" height="1086" /></svg>
    </div>
    <button className="button button-green lobby-start" onClick={start}>
      <Icon name="play" size={32} />
      {MODES.find((mode) => mode.id === selected)?.action || "Đấu hạng"}
    </button>
    <aside className="lobby-tip"><Icon name="book" size={32} /><strong>Mẹo nhỏ:</strong><span>Chọn chế độ phù hợp để bắt đầu ván chơi theo cách bạn muốn.</span></aside>

    {panel === "local" && <div className="match-dialog-backdrop local-setup-backdrop"><section className="match-dialog setup-card local-setup-card has-back-button" role="dialog" aria-modal="true" aria-labelledby="local-setup-title">
      <button className="card-back-button" onClick={() => setPanel("")} aria-label="Quay lại" title="Quay lại">
        <Icon name="arrow-left" size={19} />
      </button>
      <h2 id="local-setup-title">Chơi với bạn<img src="/images/sen.svg" alt="" aria-hidden="true" /></h2>
      <div className="local-setup-player-grid">

        <label className="local-setup-player-card local-player-one">
          <strong><Icon name="people-solid" size={48} /><span>Người chơi 1 — hàng trên</span></strong>
          <input aria-label="Tên người chơi 1" value={firstName} onChange={(e) => setFirstName(e.target.value)}
            maxLength={24} placeholder="Nhập tên người chơi 1" />
        </label>
        <label className="local-setup-player-card local-player-two">
          <strong><Icon name="people-solid" size={48} /><span>Người chơi 2 — hàng dưới</span></strong>
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

    {panel === "ranked" && <div className="match-dialog-backdrop online-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) setPanel(""); }}><section className="ranked-overlay-card online-card has-back-button" role="dialog" aria-modal="true" aria-labelledby="online-title">
      <button className="card-back-button" onClick={() => setPanel("")} aria-label="Quay lại" title="Quay lại">
        <Icon name="arrow-left" size={19} />
      </button>
      <h2 className="ranked-overlay-title online-title" id="online-title">Chơi online<img className="online-title-lotus" src="/images/sen.svg" alt="" aria-hidden="true" /></h2>
      <div className="ranked-dashboard">
      <section className="rank-profile-card" aria-label="Thông tin người chơi">
        <span className="rank-avatar">{auth.username?.slice(0, 1).toUpperCase()}</span>
        <h2>{auth.username}</h2>
        <strong>{auth.points ?? 0} điểm</strong>
        <span className="rank-badge">{auth.tier || "Tân thủ"}{auth.level ? ` · Cấp ${auth.level}` : ""}</span>
      </section>
      <section className="rank-play-card"><h2><Icon name="people" size={37} />Đấu xếp hạng</h2>
        <button className="button button-green rank-find-opponent" onClick={() => void onAction(queueing ? "cancel_matchmaking" : "find_match")}><Icon name="search" size={29} />{queueing ? "Đang tìm đối thủ…" : "Tìm đối thủ"}</button>
        {queueing && <small className="queue-note">Đang tìm trong 30 giây. Hết thời gian sẽ quay lại sảnh đấu hạng.</small>}
        <div className="rank-room-actions"><button className="rank-create-room" onClick={createRoom}><Icon name="plus-circle" size={25} />Tạo phòng</button><button className="rank-join-room" onClick={() => setPanel("join")}><Icon name="key" size={25} />Nhập mã phòng</button></div>
        <small className="rank-rules"><Icon name="info" size={21} /><span>Luật đấu: 20 giây mỗi lượt · hết giờ 2 lần là thua · mất kết nối 45 giây là thua.</span></small>
      </section>
      </div>
    </section></div>}
    {(panel === "join" || panel === "create" || panel === "room_waiting") && <div className="match-dialog-backdrop online-backdrop"><section className="match-dialog setup-card online-card online-room-card has-back-button" role="dialog" aria-modal="true" aria-labelledby="online-room-title">
      <button className="card-back-button" onClick={() => { setPanel("ranked"); setRoomCode(""); }} aria-label="Quay lại" title="Quay lại">
        <Icon name="arrow-left" size={19} />
      </button>
      <h2 className="online-title" id="online-room-title">{panel === "join" ? "Vào phòng" : "Phòng chơi"}<img className="online-title-lotus" src="/images/sen.svg" alt="" aria-hidden="true" /></h2>
      {panel === "join" ? <label className="online-code-label" htmlFor="online-room-code">Mã phòng<span className="online-code-input"><Icon name="key" size={27} /><input id="online-room-code" autoFocus value={code} maxLength={6} onChange={(e) => setCode(e.target.value.toUpperCase())} placeholder="Nhập mã 6 ký tự" onKeyDown={(e) => { if (e.key === "Enter" && code.length === 6) joinRoom(); }} /></span></label> : <><p className="online-code-label">Mã phòng</p><div className="online-room-code"><strong className="room-code-display">{roomCode || "Đang tạo…"}</strong><button className="room-copy-button" onClick={copyRoomCode} aria-label="Sao chép mã phòng" title="Sao chép mã phòng"><Icon name="copy" size={28} /></button></div><div className="room-player-slots">{[0, 1].map((slot) => <span key={slot}>{roomPlayers[slot] || slot === 0 ? <Icon name="user" size={23} /> : <i className="room-wait-spinner" aria-hidden="true" />}{roomPlayers[slot] || (slot === 0 ? auth.username : "Đang chờ đối thủ…")}</span>)}</div><small className="online-room-expiry"><Icon name="clock" size={21} />Phòng tự huỷ sau {Math.floor(roomSeconds / 60)}:{String(roomSeconds % 60).padStart(2, "0")}.</small><span className="room-copy-status" role="status">{copyNotice}</span></>}
      <div className="match-dialog-actions">{panel === "join" ? <><button onClick={() => setPanel("ranked")}>Huỷ</button><button className="confirm-surrender" onClick={joinRoom} disabled={code.length !== 6}>Vào phòng</button></> : roomHost ? <><button onClick={() => { onCancelRoom(); setPanel("ranked"); setRoomCode(""); }}>Huỷ phòng</button><button className="confirm-surrender" onClick={() => void onAction("start_room", { code: roomCode })} disabled={roomPlayers.length < 2}>Bắt đầu</button></> : <button className="confirm-surrender" disabled>Chờ chủ phòng bắt đầu</button>}</div>
    </section></div>}
  </div>;
}
