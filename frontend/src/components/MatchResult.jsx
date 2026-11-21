import { useState } from "react";
import Icon from "./Icon.jsx";

export default function MatchResult({ board, playerSlot, onExit, onReplay, rematchStatus = "none" }) {
  const [missingArt, setMissingArt] = useState("");
  const local = board.mode === "LOCAL";
  const online = board.mode === "RANKED" || board.mode === "FRIENDS";
  const left = local ? 0 : playerSlot;
  const right = 1 - left;
  const draw = board.result === "DRAW" || (local && board.winner === -1);
  const won = local || board.result === (left === 0 ? "P0_WIN" : "P1_WIN");
  const outcome = draw ? "draw" : won ? "win" : "lose";
  const winner = draw ? null : local ? board.winner : board.result === "P0_WIN" ? 0 : board.result === "P1_WIN" ? 1 : null;
  const unavailable = online && (rematchStatus === "unavailable" || board.rematchUnavailable);
  const waiting = online && rematchStatus === "waiting";
  const leftName = local ? board.playerNames?.[left] || "Người chơi 1" : "Bạn";
  const rightName = board.mode === "BOT" ? "Máy" : board.playerNames?.[right] || "Đối thủ";
  const outcomeLabel = draw ? "Hòa!" : local ? `${board.playerNames?.[board.winner] || "Người chơi"} chiến thắng!` : won ? "Chiến thắng!" : "Thất bại!";
  const notice = unavailable ? "Đối thủ đã rời phòng, không thể tái đấu."
    : waiting ? "Đang chờ đối thủ đồng ý tái đấu."
    : online && board.endReason === "PASS_LIMIT" ? "Ván đấu kết thúc do bỏ lượt hai lần."
    : online && board.endReason === "TIMEOUT" ? "Hết giờ hai lần hoặc không còn nước đi hợp lệ." : "";

  if (local) return <div className="match-dialog-backdrop result-backdrop local-result-backdrop">
    <section className="match-result result-local" role="dialog" aria-modal="true" aria-labelledby="local-result-title" aria-describedby="local-result-outcome">
      <div className="result-art" aria-hidden="true"><img src={draw ? "/images/hoa.svg" : "/images/chuc_mung.svg"} alt="" /></div>
      <button className="result-back" onClick={onExit} aria-label="Thoát"><Icon name="arrow-left" size={32} /></button>
      <h2 id="local-result-title">Kết quả trận đấu</h2>
      <p id="local-result-outcome" className="result-sr-only">{outcomeLabel}</p>
      <div className={`local-winner-card${draw ? " is-draw" : ""}`}>
        {["left", "right"].map((side) => <svg key={side} className={`local-result-laurel ${side}`} viewBox="0 0 100 150" aria-hidden="true">
          <path d="M85 140Q20 115 30 15" fill="none" stroke="#e9a313" strokeWidth="4" />
          {[0, 1, 2, 3, 4].map((leaf) => <g key={leaf} transform={`translate(${30 + leaf * leaf * 2}, ${22 + leaf * 24}) rotate(${-20 + leaf * 13})`}>
            <ellipse cx="-12" cy="-5" rx="8" ry="20" transform="rotate(-38)" fill="#ffc72b" stroke="#eaa113" />
            <ellipse cx="12" cy="-13" rx="7" ry="18" transform="rotate(35)" fill="#ffda45" stroke="#eaa113" />
          </g>)}
        </svg>)}
        {!draw && <img className="local-winner-crown" src="/images/crown.svg" alt="" />}
        <strong className="local-winner-name">{draw ? "Cả hai người chơi" : board.playerNames?.[winner] || `Người chơi ${winner + 1}`}</strong>
        <span className="local-winner-score">{draw ? `${board.scores?.[0] ?? 0} – ${board.scores?.[1] ?? 0} điểm` : `${board.scores?.[winner] ?? 0} điểm`}</span>
      </div>
      <div className="result-actions">
        <button className="result-exit" onClick={onExit}>Thoát</button>
        <button className="result-replay" onClick={onReplay}>Chơi lại</button>
      </div>
    </section>
  </div>;

  return <div className="match-dialog-backdrop result-backdrop">
    <section className={`match-result result-${outcome}`} role="dialog" aria-modal="true" aria-labelledby="match-result-title" aria-describedby="match-result-outcome">
      <div className="result-art" aria-hidden="true">
        {missingArt !== outcome && <img src={`/images/${outcome}.svg`} alt="" onError={() => setMissingArt(outcome)} />}
      </div>
      {missingArt === outcome && <div className="result-fallback-outcome" aria-hidden="true">{outcomeLabel}</div>}
      <button className="result-back" onClick={onExit} aria-label="Thoát" title="Thoát"><Icon name="arrow-left" size={32} /></button>
      <h2 id="match-result-title">Kết quả trận đấu</h2>
      <p id="match-result-outcome" className="result-sr-only">{outcomeLabel}</p>
      <div className="result-scoreboard" aria-label="Điểm số">
        <span className={`result-player-wrap${winner === left ? " is-winner" : ""}`}>
          <span className="result-player" title={leftName}>{leftName}</span>
          {winner === left && <img className="result-crown" src="/images/crown.svg" alt="Người chiến thắng" />}
        </span>
        <strong>{board.scores?.[left] ?? 0}</strong>
        <span className="result-score-divider" aria-hidden="true">-</span>
        <strong>{board.scores?.[right] ?? 0}</strong>
        <span className={`result-player-wrap${winner === right ? " is-winner" : ""}`}>
          <span className="result-player" title={rightName}>{rightName}</span>
          {winner === right && <img className="result-crown" src="/images/crown.svg" alt="Người chiến thắng" />}
        </span>
      </div>
      <div className="result-actions">
        <button className="result-exit" onClick={onExit}>Thoát</button>
        <button className="result-replay" onClick={onReplay} disabled={unavailable || waiting}>
          {waiting ? "Đang chờ…" : online && rematchStatus === "requested" ? "Đồng ý tái đấu" : "Chơi lại"}
        </button>
      </div>
      {(notice || local && !draw) && <p className="result-notice" role="status">{notice || outcomeLabel}</p>}
    </section>
  </div>;
}
