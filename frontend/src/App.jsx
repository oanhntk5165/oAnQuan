import { useCallback, useEffect, useState } from "react";
import AuthScreen from "./components/AuthScreen.jsx";
import Icon from "./components/Icon.jsx";
import { AppHeader, PlayerDashboard, SubPageHeader, Toast } from "./components/AppChrome.jsx";
import GameScreen from "./components/GameScreen.jsx";
import Lobby from "./components/Lobby.jsx";
import CommunityChat from "./components/CommunityChat.jsx";
import { WS_URL } from "./config.js";
import useGameClient from "./hooks/useGameClient.js";
import { createLocalGame, passLocalTurn, playLocalMove, timeoutLocalTurn } from "./utils/localGame.js";

export default function App() {
  // =========================
  // 1) State chung của app
  // =========================
  const [notice, setNotice] = useState("");
  const [activePage, setActivePage] = useState("home");
  const [localBoard, setLocalBoard] = useState(null);
  const [localSeries, setLocalSeries] = useState([0, 0, 0]);
  const [leaderboard, setLeaderboard] = useState([]);
  const [matchHistory, setMatchHistory] = useState([]);
  const [modeStats, setModeStats] = useState({});
  const [lobbyPanelOpen, setLobbyPanelOpen] = useState(false);

  // =========================
  // 2) Toast / thông báo
  // =========================
  const addNotice = useCallback((message) => {
    setNotice(message);
    window.clearTimeout(addNotice.timeout);
    addNotice.timeout = window.setTimeout(() => setNotice(""), 3800);
  }, []);

  // =========================
  // 3) Game client + trạng thái màn hình đang chơi
  // =========================
  const game = useGameClient(addNotice);
  const hasActiveGame = Boolean(game.board || localBoard);

  // =========================
  // 4) Fetch dữ liệu từ server cho từng tab
  // =========================
  useEffect(() => {
    if (activePage !== "leaderboard" || !game.auth) return;
    game.act("leaderboard", { limit: 100 }).then((result) => {
      if (result?.players) setLeaderboard(result.players);
    });
  }, [activePage, game.auth, game.act]);

  useEffect(() => {
    if (activePage !== "profile" || !game.auth) return;
    game.act("match_stats").then((result) => {
      if (result?.modes) setModeStats(Object.fromEntries(result.modes.map((stats) => [stats.mode, stats])));
    });
  }, [activePage, game.auth, game.act]);

  // =========================
  // 5) Xử lý nước đi local / chơi thử trên máy
  // =========================
  const handleLocalAction = useCallback((action, fields = {}) => {
    if (!localBoard || localBoard.gameOver) return;
    const next = action === "move" ? playLocalMove(localBoard, Number(fields.position), fields.direction)
      : action === "pass" ? passLocalTurn(localBoard)
        : action === "local_timeout" ? timeoutLocalTurn(localBoard) : localBoard;
    if (next === localBoard) return;
    if (!localBoard.gameOver && next.gameOver) {
      const updated = [...localSeries];
      if (next.winner === 0 || next.winner === 1) updated[next.winner] += 1;
      else updated[2] += 1;
      next.series = updated;
      setLocalSeries(updated);
    }
    setLocalBoard(next);
  }, [localBoard, localSeries]);

  useEffect(() => {
    if (activePage !== "history" || !game.auth) return;
    game.act("history", { limit: 100 }).then((result) => {
      if (result?.matches) setMatchHistory(result.matches);
    });
  }, [activePage, game.auth, game.act]);

  // =========================
  // 6) Đăng xuất và render giao diện theo tab
  // =========================
  function signOut() {
    game.signOut();
    setActivePage("home");
  }

  return (
    <main className={`app-shell ${!game.auth ? "app-shell-auth" : ""} ${game.auth && !hasActiveGame ? "app-shell-menu" : ""} ${game.auth && activePage === "home" && !hasActiveGame ? "app-shell-fixed" : ""} ${game.auth && !hasActiveGame && ["history", "leaderboard", "guide"].includes(activePage) ? "app-shell-scrollable" : ""} ${hasActiveGame ? "app-shell-game" : ""}`}>
      {!game.auth && <header className="auth-brand"><img src="/images/logo.svg" alt="Ô ăn quan" /></header>}
      {game.auth && !hasActiveGame && <header className="menu-header">
        <button className="menu-brand" onClick={() => setActivePage("home")} aria-label="Ô ăn quan - Trang chủ">
          <img src="/images/logo.svg" alt="Ô ăn quan" />
        </button>
        <PlayerDashboard auth={game.auth} activePage={activePage} onlineCount={game.onlineCount}
          onNavigate={setActivePage} onSignOut={signOut} />
      </header>}
      {game.auth && !hasActiveGame && !["home", "history", "leaderboard", "guide", "profile"].includes(activePage) && (
        <AppHeader connected={game.connected} connectionIssue={game.connectionIssue}
          auth={game.auth} activePage={activePage} onlineCount={game.onlineCount}
          onNavigate={setActivePage} onSignOut={signOut} />
      )}
      {!game.auth ? (
        // =========================
        // 7) Màn hình đăng nhập / chưa đăng nhập
        // =========================
        <section className="page-content signed-out">
          <AuthScreen connected={game.connected} connectionIssue={game.connectionIssue}
            wsUrl={WS_URL} onAuthenticate={game.authenticate} />
        </section>
      ) : (
        <div className="authenticated-layout">
          <section className="page-content signed-in">
            {activePage === "home" && (game.board || localBoard ? (
              <GameScreen auth={game.auth} roomCode={game.roomCode}
                messages={game.messages}
                rematchStatus={game.rematchStatus}
                onExitRanked={() => game.act("exit_match")}
                onExitBot={game.clearFinishedGame}
                board={localBoard || game.board} onAction={localBoard ? handleLocalAction : game.act}
                onRematch={() => {
                  if (localBoard) {
                    setLocalBoard(createLocalGame(localBoard.playerNames, localBoard.startingPlayer, localSeries));
                  } else if (game.board?.mode === "BOT") {
                    void game.act("create_bot", { goFirst: true });
                  }
                }}
                onExitLocal={() => { setLocalBoard(null); setLocalSeries([0, 0, 0]); }} />
            ) : (
              <Lobby auth={game.auth} onPanelChange={setLobbyPanelOpen}
                queueing={game.queueing} roomPlayers={game.roomPlayers} onAction={game.act} onCancelRoom={game.cancelRoom}
                onStartLocal={(names, turn) => { setLocalSeries([0, 0, 0]); setLocalBoard(createLocalGame(names, turn)); }} />
            ))}
            {activePage === "profile" ? (
              <section className="profile-page">
                <div className="profile-identity-card">
                  <img className="profile-frame" src="/images/profile.svg" alt="" aria-hidden="true" />
                  <span className="profile-main-avatar">{game.auth.username?.slice(0, 1).toUpperCase()}</span>
                  <h1>{game.auth.username}</h1>
                  <strong className="profile-points">{game.auth.points ?? 0} điểm</strong>
                  <span className="profile-rank"><Icon name="user" size={13} />{game.auth.tier || "Tân thủ"}{game.auth.level ? ` - Cấp ${game.auth.level}` : ""}</span>
                </div>
                <div className="profile-stat-grid">
                  {[{ mode: "RANKED", title: "Thống kê đấu hạng" }].map(({ mode, title }) => {
                    const stats = modeStats[mode] || { games: 0, wins: 0, losses: 0, draws: 0 };
                    const winRate = stats.games ? Math.round((stats.wins / stats.games) * 100) : 0;
                    return <article className="profile-stat-card" key={mode}>
                      <div className="profile-stat-heading"><span className="profile-stat-icon">♜</span><h2>{title}</h2><img src="/images/may.svg" alt="" aria-hidden="true" /></div>
                      <div className="profile-stat-total"><strong>{stats.games}</strong><span>Trận đã đấu</span></div>
                      <div className="profile-stat-results">
                        <div className="profile-result profile-result-win"><i aria-hidden="true"><Icon name="trophy" size={20} /></i><span><b>{stats.wins}</b>Thắng</span></div>
                        <div className="profile-result profile-result-loss"><i aria-hidden="true"><Icon name="close" size={22} /></i><span><b>{stats.losses}</b>Thua</span></div>
                        <div className="profile-result profile-result-draw"><i aria-hidden="true">=</i><span><b>{stats.draws}</b>Hoà</span></div>
                      </div>
                      <div className="profile-win-rate"><span>Tỷ lệ thắng</span><div className="profile-win-track" role="progressbar" aria-label="Tỷ lệ thắng" aria-valuemin={0} aria-valuemax={100} aria-valuenow={winRate}><div style={{ width: `${winRate}%` }} /></div><strong>{winRate}%</strong></div>
                    </article>;
                  })}
                </div>
              </section>
            ) : activePage === "history" ? (
              <section className="history-page">
                <SubPageHeader title="Lịch sử trận đấu" onBack={() => setActivePage("home")} />
                <div className="history-list">
                  {matchHistory.map((match, index) => {
                    const won = match.myResult === "WIN";
                    const draw = match.myResult === "DRAW";
                    const resultLabel = won ? "Thắng" : draw ? "Hoà" : "Thua";
                    const myScore = match.playerSlot === 0 ? match.score1 : match.score2;
                    const opponentScore = match.playerSlot === 0 ? match.score2 : match.score1;
                    const pointsChange = match.playerSlot === 0 ? match.pointsChange1 : match.pointsChange2;
                    return <article className="history-match-card" key={match.matchId}>
                      <span className="history-order">{String(index + 1).padStart(2, "0")}</span>
                      <span className={`history-result ${won ? "result-win" : draw ? "result-draw" : "result-loss"}`}>{resultLabel}</span>
                      <div className="history-opponent"><span className="history-avatar">{match.opponent?.slice(0, 1).toUpperCase()}</span><div><small>ĐỐI THỦ</small><strong>{match.opponent || "Người chơi"}</strong></div></div>
                      <div className="history-score"><small>TỈ SỐ</small><strong>{myScore ?? "—"}<i>–</i>{opponentScore ?? "—"}</strong></div>
                      <div className={`history-rating ${pointsChange > 0 ? "rating-plus" : pointsChange < 0 ? "rating-minus" : ""}`}><small>ĐIỂM RANK</small><strong>{pointsChange > 0 ? "+" : ""}{pointsChange ?? 0}</strong></div>
                      <time>{match.endedAt ? new Date(match.endedAt).toLocaleString("vi-VN", { dateStyle: "medium", timeStyle: "short" }) : ""}</time>
                    </article>;
                  })}
                  {matchHistory.length === 0 && <div className="leaderboard-empty">Chưa có trận đấu nào được ghi nhận.</div>}
                </div>
              </section>
            ) : activePage === "leaderboard" ? (
              <section className="leaderboard-page">
                <SubPageHeader title="Bảng xếp hạng" onBack={() => setActivePage("home")} />
                <div className="leaderboard-card">
                  <div className="leaderboard-row leaderboard-head"><span>Hạng</span><span>Người chơi</span><span>Điểm</span><span>Rank</span></div>
                  {leaderboard.map((player, index) => <div className={`leaderboard-row ${player.username === game.auth.username ? "is-self" : ""}`} key={`${player.username}-${index}`}>
                    <span className={`leaderboard-position position-${index + 1}`}>{String(index + 1).padStart(2, "0")}</span>
                    <span className="leaderboard-player"><span className="leaderboard-avatar">{player.username?.slice(0, 1).toUpperCase()}</span><strong>{player.username}</strong></span>
                    <strong className="leaderboard-points">{player.points}</strong>
                    <span className="leaderboard-tier">{player.tier} · Cấp {player.level}</span>
                  </div>)}
                  {leaderboard.length === 0 && <div className="leaderboard-empty">Chưa có dữ liệu xếp hạng.</div>}
                </div>
              </section>
            ) : activePage === "guide" ? (
              <section className="guide-page">
                <SubPageHeader title="Hướng dẫn chơi" onBack={() => setActivePage("home")} />
                <div className="guide-intro-card"><div className="guide-board-symbol">✳</div><div><strong>Bàn cờ và cách tính điểm</strong><p>10 ô dân, mỗi ô có 5 quân; 2 ô Quan ở 2 đầu, mỗi ô có 1 quân. Quân quan tính 10 điểm, quân dân tính 1 điểm.</p></div></div>
                <div className="guide-steps">
                  <article className="guide-step-card"><span className="guide-step-number">01</span><h2>Chọn ô và hướng đi</h2><p>Đến lượt, chọn một ô dân bên phía mình đang có quân. Chọn rải theo chiều thuận hoặc ngược trên vòng bàn cờ.</p></article>
                  <article className="guide-step-card"><span className="guide-step-number">02</span><h2>Rải quân</h2><p>Bốc toàn bộ quân trong ô đã chọn, rải lần lượt mỗi ô một quân theo hướng đã chọn. Nếu ô kế tiếp có quân dân, bốc số quân đó lên và rải tiếp.</p></article>
                  <article className="guide-step-card"><span className="guide-step-number">03</span><h2>Ăn quân</h2><p>Khi quân cuối dừng trước một ô trống, bạn ăn quân ở ô có quân ngay sau ô trống đó. Có thể tiếp tục ăn theo từng cặp ô trống rồi ô có quân; dừng khi gặp hai ô trống liền nhau.</p></article>
                  <article className="guide-step-card"><span className="guide-step-number">04</span><h2>Ô quan và kết thúc</h2><p>Nếu ô kế tiếp là ô quan còn quân, lượt đi dừng lại. Khi cả hai ô quan đã hết, ván kết thúc. Quân dân còn trên phần bàn của mỗi bên được cộng cho bên đó; ai nhiều điểm hơn sẽ thắng.</p></article>
                </div>
                <div className="guide-notes">
                  <div className="guide-note"><strong>Hết quân để đi?</strong><p>Nếu 5 ô dân đều trống, dùng 5 điểm để rải bù mỗi ô 1 quân. Không đủ điểm thì thua.</p></div>
                  <div className="guide-note guide-online-note"><strong>Đấu hạng</strong><p>Hết thời gian rải quân hoặc bỏ lượt: hệ thống tự đi một ô. Bỏ lượt hai lần hoặc mất kết nối quá 45 giây thì thua.</p></div>
                </div>
              </section>
            ) : activePage !== "home" && (
              <section className="page-placeholder">
                <span className="eyebrow"><span className="eyebrow-line" /> KHÔNG GIAN CỦA BẠN</span>
                <h1>{{
                  history: "Lịch sử trận đấu",
                  leaderboard: "Bảng xếp hạng",
                  guide: "Hướng dẫn",
                  profile: "Hồ sơ",
                }[activePage]}</h1>
                <p>Nội dung trang này sẽ được hoàn thiện theo mô tả tiếp theo của bạn.</p>
              </section>
            )}
          </section>
        </div>
      )}
      {game.auth && <CommunityChat key={game.auth.userId} auth={game.auth} connected={game.connected}
        onlineCount={game.onlineCount} events={game.communityMessages} request={game.communityRequest}
        visible={activePage === "home" && !hasActiveGame && !lobbyPanelOpen} />}
      <Toast message={notice} onDismiss={() => setNotice("")} />
    </main>
  );
}
