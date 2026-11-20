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
  const unavailable = online && (rematchStatus === "unavailable" || board.rematchUnavailable);
  const waiting = online && rematchStatus === "waiting";
  const leftName = local ? board.playerNames?.[left] || "Người chơi 1" : "Bạn";
  const rightName = board.mode === "BOT" ? "Máy" : board.playerNames?.[right] || "Đối thủ";
  const outcomeLabel = draw ? "Hòa!" : local ? `${board.playerNames?.[board.winner] || "Người chơi"} chiến thắng!` : won ? "Chiến thắng!" : "Thất bại!";
  const notice = unavailable ? "Đối thủ đã rời phòng, không thể tái đấu."
    : waiting ? "Đang chờ đối thủ đồng ý tái đấu."
    : online && board.endReason === "PASS_LIMIT" ? "Ván đấu kết thúc do bỏ lượt hai lần."
    : online && board.endReason === "TIMEOUT" ? "Hết giờ và không còn nước đi hợp lệ." : "";

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
        <span className="result-player" title={leftName}>{leftName}</span>
        <strong>{board.scores?.[left] ?? 0}</strong>
        <span className="result-score-divider" aria-hidden="true">-</span>
        <strong>{board.scores?.[right] ?? 0}</strong>
        <span className="result-player" title={rightName}>{rightName}</span>
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
