import { useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import Icon from "./Icon.jsx";
import MatchResult from "./MatchResult.jsx";
import { Pit, StorePit } from "./GameBoard.jsx";
import formatTime from "../utils/formatTime.js";

const ANIMATION_STEP_DELAY_MS = 250;
const ANIMATION_END_DELAY_MS = 120;

const PLAYER_PITS = {
  0: [1, 2, 3, 4, 5],
  1: [7, 8, 9, 10, 11],
};

function copyBoardForAnimation(source, target) {
  return {
    ...target,
    dan: [...source.dan],
    quan: [...source.quan],
    scores: [...source.scores],
  };
}

function applyMoveEvent(board, event, player) {
  if (event.type === "PICKUP") {
    board.dan[event.pit] = 0;
  } else if (event.type === "SOW") {
    board.dan[event.pit] += event.value;
  } else if (event.type === "CAPTURE") {
    board.dan[event.pit] = 0;
    board.quan[event.pit] = false;
    board.scores[player] += event.value;
  } else if (event.type === "REFILL") {
    const firstPit = event.value === 0 ? 1 : 7;
    board.scores[event.value] -= 5;
    for (let pit = firstPit; pit < firstPit + 5; pit += 1) board.dan[pit] = 1;
  }
}

export default function GameScreen({
  auth,
  board,
  messages,
  onAction,
  onRematch,
  onExitLocal,
  onExitBot,
  onExitRanked,
  rematchStatus = "none",
  muted = false,
  onToggleMute,
  onPlayStoneSound,
}) {
  // =========================
  // 1) Trạng thái bàn cờ / lượt / chat
  // =========================
  const [chatText, setChatText] = useState("");
  const [showGuide, setShowGuide] = useState(false);
  const [showPause, setShowPause] = useState(false);
  const [confirmExit, setConfirmExit] = useState(false);
  const [confirmAction, setConfirmAction] = useState("");
  const [localSeconds, setLocalSeconds] = useState(20);
  const [botElapsedSeconds, setBotElapsedSeconds] = useState(0);
  const [botPaused, setBotPaused] = useState(false);
  const [botActionPending, setBotActionPending] = useState(false);
  const [exitPending, setExitPending] = useState(false);
  const [animationBoard, setAnimationBoard] = useState(null);
  const [animationTurn, setAnimationTurn] = useState(null);
  const [isAnimating, setIsAnimating] = useState(false);
  const [turnNotice, setTurnNotice] = useState(null);
  const [selectedPit, setSelectedPit] = useState(null);
  const timeoutSent = useRef(false);
  const boardRef = useRef(null);
  const animationQueue = useRef([]);
  const animationTimer = useRef(null);
  const animationRunning = useRef(false);
  const turnNoticeTimer = useRef(null);
  const announcedTurn = useRef(null);
  const botActionLock = useRef(false);
  const exitActionLock = useRef(false);
  const playerSlot =
    board.mode === "BOT"
      ? 0
      : board.mode === "LOCAL"
        ? board.turn
        : Math.max(0, board.playerNames?.indexOf(auth.username) ?? 0);
  const opponentSlot = 1 - playerSlot;
  const isSoloGame = board.mode === "BOT" || board.mode === "LOCAL";
  const isOnlineGame = board.mode === "RANKED" || board.mode === "FRIENDS";
  const usesTopbarLayout = isSoloGame || isOnlineGame;
  const isTurnNoticeVisible = turnNotice !== null;
  const gamePaused =
    board.mode === "BOT"
      ? botPaused
      : board.mode === "LOCAL"
        ? showGuide || showPause || confirmExit
        : false;
  const legalPits = useMemo(() => {
    if (
      !board ||
      (board.mode !== "LOCAL" && gamePaused) ||
      board.gameOver ||
      isAnimating ||
      isTurnNoticeVisible ||
      board.turn !== playerSlot
    )
      return [];
    return PLAYER_PITS[playerSlot].filter((pit) => (board.dan?.[pit] ?? 0) > 0);
  }, [board, gamePaused, isAnimating, isTurnNoticeVisible, playerSlot]);
  const visualBoard = animationBoard ?? board;

  // =========================
  // 2) Định nghĩa vị trí ô trên bàn cờ
  // =========================
  const topSlot = board.mode === "LOCAL" ? 0 : opponentSlot;
  const bottomSlot = board.mode === "LOCAL" ? 1 : playerSlot;
  const topPits = topSlot === 0 ? [5, 4, 3, 2, 1] : [7, 8, 9, 10, 11];
  const bottomPits = bottomSlot === 0 ? [5, 4, 3, 2, 1] : [7, 8, 9, 10, 11];

  const rightQuanPit = 0;
  const leftQuanPit = 6;

  const visibleTurn =
    isAnimating && animationTurn !== null ? animationTurn : board.turn;
  const gameStatus = board.gameOver
    ? "Ván đấu đã kết thúc"
    : gamePaused
      ? "Ván đấu đang tạm dừng"
      : isAnimating
        ? board.mode === "BOT"
          ? animationTurn === playerSlot
            ? "Bạn đang rải quân..."
            : "Máy đang rải quân..."
          : board.mode === "LOCAL"
            ? `${board.playerNames?.[animationTurn] || `Người chơi ${(animationTurn ?? board.turn) + 1}`} đang rải quân...`
            : `${board.playerNames?.[animationTurn] || "Đối thủ"} đang rải quân...`
        : board.mode === "LOCAL"
          ? `Lượt của ${board.playerNames?.[board.turn] || `Người chơi ${board.turn + 1}`}`
          : board.turn === playerSlot
            ? "Lượt của bạn"
            : board.mode === "BOT"
              ? "Đến lượt máy"
              : "Đối thủ đang đi...";

  useLayoutEffect(() => {
    const previousBoard = boardRef.current;
    boardRef.current = board;
    if (!previousBoard) return;

    const events = Array.isArray(board.moveEvents) ? board.moveEvents : [];
    if (events.length === 0) {
      if (animationTimer.current !== null)
        window.clearTimeout(animationTimer.current);
      animationTimer.current = null;
      animationQueue.current = [];
      animationRunning.current = false;
      setAnimationBoard(null);
      setAnimationTurn(null);
      setIsAnimating(false);
      return;
    }

    animationQueue.current.push({
      from: copyBoardForAnimation(previousBoard, previousBoard),
      to: copyBoardForAnimation(board, board),
      events,
    });
    if (animationRunning.current) return;

    animationRunning.current = true;
    setIsAnimating(true);
    const playNext = () => {
      const move = animationQueue.current.shift();
      if (!move) {
        animationRunning.current = false;
        animationTimer.current = null;
        setAnimationBoard(null);
        setAnimationTurn(null);
        setIsAnimating(false);
        return;
      }

      const visual = copyBoardForAnimation(move.from, move.to);
      setAnimationTurn(move.from.turn);
      setAnimationBoard(visual);

      let eventIndex = 0;
      const playStep = () => {
        if (eventIndex >= move.events.length) {
          setAnimationBoard(copyBoardForAnimation(move.to, move.to));
          animationTimer.current = window.setTimeout(() => {
            animationTimer.current = null;
            playNext();
          }, ANIMATION_END_DELAY_MS);
          return;
        }

        const currentEvent = move.events[eventIndex];
        applyMoveEvent(visual, currentEvent, move.from.turn);
        if (currentEvent.type === "SOW" && onPlayStoneSound) onPlayStoneSound();
        eventIndex += 1;
        setAnimationBoard({
          ...visual,
          dan: [...visual.dan],
          quan: [...visual.quan],
          scores: [...visual.scores],
        });

        animationTimer.current = window.setTimeout(
          playStep,
          ANIMATION_STEP_DELAY_MS,
        );
      };
      playStep();
    };
    playNext();
  }, [board]);

  useLayoutEffect(() => {
    if (turnNoticeTimer.current !== null) {
      window.clearTimeout(turnNoticeTimer.current);
      turnNoticeTimer.current = null;
    }

    if (
      board.mode !== "BOT" ||
      board.gameOver ||
      isAnimating ||
      animationRunning.current
    ) {
      setTurnNotice(null);
      if (board.mode !== "BOT" || board.gameOver) announcedTurn.current = null;
      return undefined;
    }

    const turnKey = `${board.matchId ?? board.code}:${board.turn}`;
    if (announcedTurn.current === turnKey) return undefined;

    announcedTurn.current = turnKey;
    setTurnNotice(board.turn);
    turnNoticeTimer.current = window.setTimeout(() => {
      setTurnNotice(null);
      turnNoticeTimer.current = null;
    }, 2500);
    return () => {
      if (turnNoticeTimer.current !== null) {
        window.clearTimeout(turnNoticeTimer.current);
        turnNoticeTimer.current = null;
      }
      if (announcedTurn.current === turnKey) announcedTurn.current = null;
    };
  }, [
    board.mode,
    board.matchId,
    board.code,
    board.turn,
    board.gameOver,
    isAnimating,
  ]);

  useEffect(() => {
    if (!board || board.gameOver) {
      setSelectedPit(null);
      return;
    }
    if (board.turn !== playerSlot) {
      setSelectedPit(null);
      return;
    }
    if (selectedPit != null && !legalPits.includes(selectedPit)) {
      setSelectedPit(null);
    }
  }, [board, legalPits, playerSlot, selectedPit]);

  // =========================
  // 3) Đồng hồ lượt / timeout
  // =========================
  useEffect(() => {
    setLocalSeconds(20);
    timeoutSent.current = false;
    if (board.mode === "BOT" || board.gameOver) return undefined;
    const initialSeconds =
      board.mode === "LOCAL"
        ? 20
        : Math.max(
          0,
          Math.ceil((board.remainingMs?.[board.turn] ?? 20000) / 1000),
        );
    setLocalSeconds(initialSeconds);
  }, [board.mode, board.turn, board.gameOver, board.matchId]);

  useEffect(() => {
    if (board.mode === "BOT" || board.gameOver || gamePaused) return undefined;
    const timer = window.setInterval(
      () =>
        setLocalSeconds((seconds) => {
          if (seconds <= 1) {
            window.clearInterval(timer);
            if (!timeoutSent.current) {
              timeoutSent.current = true;
              if (board.mode === "LOCAL") onAction("local_timeout");
            }
            return 0;
          }
          return seconds - 1;
        }),
      1000,
    );
    return () => window.clearInterval(timer);
  }, [
    board.mode,
    board.turn,
    board.gameOver,
    board.matchId,
    gamePaused,
    onAction,
  ]);

  useEffect(() => {
    setBotElapsedSeconds(0);
    setBotPaused(false);
  }, [board.mode, board.matchId]);

  useEffect(() => {
    if (board.mode !== "BOT" || board.gameOver || gamePaused) return undefined;
    const timer = window.setInterval(
      () => setBotElapsedSeconds((seconds) => seconds + 1),
      1000,
    );
    return () => window.clearInterval(timer);
  }, [board.mode, board.gameOver, gamePaused]);

  useEffect(
    () => () => {
      if (animationTimer.current !== null)
        window.clearTimeout(animationTimer.current);
      if (turnNoticeTimer.current !== null)
        window.clearTimeout(turnNoticeTimer.current);
      animationQueue.current = [];
    },
    [],
  );

  // =========================
  // 4) Hành động người chơi
  // =========================
  function sendChat(event) {
    event.preventDefault();
    const content = chatText.trim();
    if (!content) return;
    void onAction("chat", { content });
    setChatText("");
  }

  function handlePitSelection(pit) {
    if (
      isAnimating ||
      isTurnNoticeVisible ||
      board.turn !== playerSlot ||
      board.gameOver ||
      !legalPits.includes(pit)
    )
      return;
    setSelectedPit(pit);
  }

  function sendMove(direction) {
    if (
      isAnimating ||
      isTurnNoticeVisible ||
      selectedPit == null ||
      (board.mode !== "LOCAL" && !legalPits.includes(selectedPit)) ||
      board.turn !== playerSlot
    )
      return;
    const pit = selectedPit;
    const sowDirection = pit <= 5 ? -direction : direction;
    setSelectedPit(null);
    void onAction("move", { position: pit, direction: sowDirection });
  }

  async function setBotPauseState(paused) {
    if (board.mode !== "BOT" || botPaused === paused) return true;
    if (botActionLock.current) return false;
    botActionLock.current = true;
    setBotActionPending(true);
    if (paused) setBotPaused(true);
    let result;
    try {
      result = await onAction(paused ? "pause_bot" : "resume_bot");
    } catch (error) {
      if (paused) setBotPaused(false);
      throw error;
    } finally {
      botActionLock.current = false;
      setBotActionPending(false);
    }
    if (!result) {
      if (paused) setBotPaused(false);
      return false;
    }
    setBotPaused(paused);
    return true;
  }

  function pauseBot() {
    return setBotPauseState(true);
  }

  function resumeBot() {
    return setBotPauseState(false);
  }

  async function openGuide() {
    if (!(await pauseBot())) return;
    setShowGuide(true);
  }

  async function closeGuide() {
    if (!(await resumeBot())) return;
    setShowGuide(false);
  }

  async function openPause() {
    if (!(await pauseBot())) return;
    setShowPause(true);
  }

  async function continueGame() {
    if (!(await resumeBot())) return;
    setShowPause(false);
  }

  function showGuideFromPause() {
    setShowPause(false);
    setShowGuide(true);
  }

  async function openExitConfirmation() {
    if (!(await pauseBot())) return;
    setConfirmExit(true);
  }

  async function cancelExitConfirmation() {
    if (!(await resumeBot())) return;
    setConfirmExit(false);
  }

  function surrender() {
    setConfirmAction("resign");
  }

  function confirmTurnAction() {
    const action = confirmAction;
    setConfirmAction("");
    if (action && !isAnimating && !isTurnNoticeVisible) void onAction(action);
  }

  async function exitMatch() {
    setConfirmExit(false);
    if (board.mode === "LOCAL") {
      onExitLocal?.();
      return;
    }
    if (board.mode !== "BOT") {
      void onAction("resign");
      return;
    }
    if (exitActionLock.current) return;
    exitActionLock.current = true;
    setExitPending(true);
    try {
      const result = await onAction("resign");
      if (!result) setConfirmExit(true);
    } catch (error) {
      setConfirmExit(true);
      throw error;
    } finally {
      exitActionLock.current = false;
      setExitPending(false);
    }
  }

  return (
    <div className={`game-layout ${usesTopbarLayout ? "bot-game-layout" : ""}`}>
      {/* =========================
          5) Header trên cùng của ván đấu
          ========================= */}
      <header
        className={`match-topbar ${usesTopbarLayout ? "bot-topbar" : ""}`}
      >
        {usesTopbarLayout ? (
          <>
            <button
              className="bot-top-action"
              onClick={openExitConfirmation}
              disabled={botActionPending || exitPending}
              aria-label="Thoát ván"
              title="Thoát ván"
            >
              <Icon name="arrow-left" size={20} />
            </button>
            <div
              className={`bot-player-card opponent-row ${visibleTurn === topSlot && !board.gameOver ? "is-current-player" : ""}`}
            >
              <span className="match-player-emblem"><Icon name={board.mode === "BOT" ? "monitor" : "user"} /></span>
              <div className="bot-player-info">
                <small>
                  {board.mode === "LOCAL"
                    ? "NGƯỜI CHƠI 1 · HÀNG TRÊN"
                    : board.mode === "BOT"
                      ? "MÁY"
                      : "ĐỐI THỦ"}
                </small>
                <strong>
                  {board.mode === "LOCAL"
                    ? board.playerNames?.[topSlot] || "Người chơi 1"
                    : board.mode === "BOT"
                      ? board.playerNames?.[topSlot] || "Máy"
                      : board.playerNames?.[topSlot] || "Đối thủ"}
                </strong>
              </div>
              <div className="bot-player-score">
                <small>Điểm</small>
                <strong>{visualBoard.scores?.[topSlot] ?? 0}</strong>
              </div>
            </div>
            <div
              className="bot-clock"
              aria-label={`Thời gian chơi ${formatTime((board.mode === "BOT" ? botElapsedSeconds : localSeconds) * 1000)}`}
            >
              <small>Thời gian</small>
              <strong>
                {formatTime(
                  (board.mode === "BOT" ? botElapsedSeconds : localSeconds) *
                  1000,
                )}
              </strong>
            </div>
            <div
              className={`bot-player-card you-row ${visibleTurn === bottomSlot && !board.gameOver ? "is-current-player" : ""}`}
            >
              <span className="match-player-emblem"><Icon name="user" /></span>
              <div className="bot-player-info">
                <small>
                  {board.mode === "LOCAL" ? "NGƯỜI CHƠI 2 · HÀNG DƯỚI" : "BẠN"}
                </small>
                <strong>
                  {board.mode === "LOCAL"
                    ? board.playerNames?.[bottomSlot] || "Người chơi 2"
                    : auth.username}
                </strong>
              </div>
              <div className="bot-player-score">
                <small>Điểm</small>
                <strong>{visualBoard.scores?.[bottomSlot] ?? 0}</strong>
              </div>
            </div>
            <button
              className="bot-top-action bot-help-action"
              onClick={openGuide}
              disabled={botActionPending || exitPending}
              aria-label="Hướng dẫn"
              title="Hướng dẫn"
            >
              <span>?</span>
            </button>
            {isSoloGame ? (
              <button
                className="bot-top-action"
                onClick={openPause}
                disabled={botActionPending || exitPending}
                aria-label="Tạm dừng"
                title="Tạm dừng"
              >
                <Icon name="pause" size={19} />
              </button>
            ) : (
              <button
                className="bot-top-action"
                onClick={surrender}
                disabled={board.gameOver}
                aria-label="Đầu hàng"
                title="Đầu hàng"
              >
                <Icon name="flag" size={19} />
              </button>
            )}
          </>
        ) : (
          <>
            <div className="match-score-strip">
              <div
                className={`match-player-card opponent-row ${visibleTurn === opponentSlot && !board.gameOver ? "is-current-player" : ""}`}
              >
                <div className="player-meta">
                  <strong>
                    {board.playerNames?.[opponentSlot] || "Đối thủ"}
                  </strong>
                  <small>ĐỐI THỦ</small>
                </div>
                <div className="player-score">
                  <small>ĐÁ ĂN ĐƯỢC</small>
                  <strong>{visualBoard.scores?.[opponentSlot] ?? 0}</strong>
                </div>
              </div>
              <div
                className={`match-turn ${board.gameOver ? "" : "is-active"}`}
              >
                <small>
                  {board.gameOver ? "VÁN ĐẤU ĐÃ KẾT THÚC" : "LƯỢT CHƠI"}
                </small>
                <strong>
                  {board.playerNames?.[board.turn] ||
                    (board.turn === playerSlot ? auth.username : "Đối thủ")}
                </strong>
                {!board.gameOver && (
                  <span
                    className={`local-turn-clock ${localSeconds <= 5 ? "clock-warning" : ""}`}
                  >
                    {localSeconds}s
                  </span>
                )}
              </div>
              <div
                className={`match-player-card you-row ${visibleTurn === playerSlot && !board.gameOver ? "is-current-player" : ""}`}
              >
                <div className="player-meta">
                  <strong>
                    {board.mode === "LOCAL"
                      ? board.playerNames?.[playerSlot]
                      : auth.username}
                  </strong>
                  <small>
                    {board.mode === "LOCAL"
                      ? `NGƯỜI CHƠI ${playerSlot + 1}`
                      : "BẠN"}
                  </small>
                </div>
                <div className="player-score">
                  <small>ĐÁ ĂN ĐƯỢC</small>
                  <strong>{visualBoard.scores?.[playerSlot] ?? 0}</strong>
                </div>
              </div>
            </div>
            <nav className="match-actions" aria-label="Thao tác ván đấu">
              <button
                onClick={() => setShowGuide(true)}
                aria-label="Hướng dẫn"
                title="Hướng dẫn"
              >
                <Icon name="book" size={19} />
                <span>Hướng dẫn</span>
              </button>
              {board.mode !== "LOCAL" && (
                <button
                  onClick={surrender}
                  disabled={isAnimating || board.gameOver}
                  aria-label="Xin đầu hàng"
                  title="Xin đầu hàng"
                >
                  <Icon name="flag" size={19} />
                  <span>Đầu hàng</span>
                </button>
              )}
              {board.mode !== "LOCAL" &&
                board.turn === playerSlot &&
                !board.gameOver && (
                  <button
                    onClick={() => setConfirmAction("pass")}
                    disabled={isAnimating || isTurnNoticeVisible}
                    aria-label="Bỏ lượt"
                    title="Bỏ lượt, hệ thống tự đi một ô"
                  >
                    <Icon name="refresh" size={19} />
                    <span>Bỏ lượt</span>
                  </button>
                )}
              {board.mode === "LOCAL" && !board.gameOver && (
                <button
                  onClick={() => setConfirmAction("pass")}
                  disabled={isAnimating || isTurnNoticeVisible}
                  aria-label="Bỏ lượt"
                  title="Mỗi người bỏ lượt hai lần sẽ thua"
                >
                  <Icon name="refresh" size={19} />
                  <span>Bỏ lượt</span>
                </button>
              )}
              <button
                onClick={() => setConfirmExit(true)}
                aria-label="Thoát ván"
                title="Thoát ván"
              >
                <Icon name="logout" size={19} />
                <span>Thoát</span>
              </button>
            </nav>
          </>
        )}
      </header>
      {/* =========================
          6) Bàn cờ chính
          ========================= */}
      <section
        className={`match-main ${usesTopbarLayout ? "bot-match-main" : ""}`}
      >
        <div className="board-wrap">
          <div className="board-frame">
            <StorePit board={visualBoard} index={rightQuanPit} />

            <div className="pit-row pit-row-top">
              {topPits.map((pit) => {
                const isSelected = selectedPit === pit;
                const isLegal =
                  legalPits.includes(pit) && board.turn === playerSlot;
                return (
                  <Pit
                    key={pit}
                    index={pit}
                    board={visualBoard}
                    disabled={board.mode === "LOCAL"
                      ? isAnimating || isTurnNoticeVisible || board.turn !== topSlot || board.gameOver || !board.dan?.[pit]
                      : !isLegal}
                    legal={isLegal}
                    selected={isSelected}
                    onClick={() => handlePitSelection(pit)}
                    onChooseDirection={(direction) => sendMove(direction)}
                  />
                );
              })}
            </div>
            <div className="pit-row pit-row-bottom">
              {bottomPits.map((pit) => {
                const isSelected = selectedPit === pit;
                const isLegal =
                  legalPits.includes(pit) && board.turn === playerSlot;
                return (
                  <Pit
                    key={pit}
                    index={pit}
                    board={visualBoard}
                    disabled={board.mode === "LOCAL"
                      ? isAnimating || isTurnNoticeVisible || board.turn !== playerSlot || board.gameOver || !board.dan?.[pit]
                      : !isLegal}
                    legal={isLegal}
                    selected={isSelected}
                    onClick={() => handlePitSelection(pit)}
                    onChooseDirection={(direction) => sendMove(direction)}
                  />
                );
              })}
            </div>

            <StorePit board={visualBoard} index={leftQuanPit} />
          </div>
        </div>
      </section>
      {usesTopbarLayout && board.mode !== "BOT" && (
        <div className="bot-game-status" role="status" aria-live="polite">
          {gameStatus}
        </div>
      )}
      {board.mode === "BOT" && isTurnNoticeVisible && (
        <div
          className="bot-turn-announcement"
          role="status"
          aria-live="assertive"
        >
          <span>
            {turnNotice === playerSlot ? "Lượt của bạn" : "Lượt của máy"}
          </span>
        </div>
      )}
      {/* =========================
          7) Chat trong trận
          ========================= */}

      {!usesTopbarLayout && board.mode !== "BOT" && board.mode !== "LOCAL" && (
        <aside className="match-chat" aria-label="Chat trong trận">
          <div className="chat-title">
            <strong>Chat trong trận</strong>
            <span>✳</span>
          </div>
          <div className="chat-messages">
            {messages.length === 0 ? (
              <div className="chat-empty">
                <span>☀</span>
                <p>
                  Chưa có tin nhắn.
                  <br />
                  Hãy gửi lời chào nhé!
                </p>
              </div>
            ) : (
              messages.map((message, index) => (
                <div className="chat-message" key={index}>
                  <strong>{message.username || "Người chơi"}</strong>
                  <p>{message.content}</p>
                </div>
              ))
            )}
          </div>
          <form className="chat-form" onSubmit={sendChat}>
            <input
              value={chatText}
              maxLength={500}
              onChange={(event) => setChatText(event.target.value)}
              placeholder="Nhập tin nhắn..."
            />
            <button disabled={!chatText.trim()} aria-label="Gửi tin nhắn">
              <Icon name="send" size={18} />
            </button>
          </form>
        </aside>
      )}
      {!usesTopbarLayout && (
        <div className="corner-player you-corner">
          <span className="player-avatar you-avatar">
            {(board.mode === "LOCAL"
              ? board.playerNames?.[playerSlot]
              : auth.username
            )
              ?.slice(0, 1)
              .toUpperCase()}
          </span>
          <strong>
            {board.mode === "LOCAL"
              ? board.playerNames?.[playerSlot]
              : auth.username}
          </strong>
        </div>
      )}
      {/* =========================
          8) Dialog hướng dẫn / xác nhận / kết thúc ván
          ========================= */}
      {showGuide && (
        <div
          className="match-dialog-backdrop"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) void closeGuide();
          }}
        >
          <section
            className="match-dialog ingame-dialog ingame-guide-dialog has-back-button"
            role="dialog"
            aria-modal="true"
            aria-labelledby="match-guide-title"
          >
            <button
              className="card-back-button"
              onClick={() => void closeGuide()}
              disabled={botActionPending}
              aria-label="Quay lại"
              title="Quay lại"
            >
              <Icon name="arrow-left" size={19} />
            </button>
            <h2 id="match-guide-title">Hướng dẫn chơi</h2>
            <p>
              Chọn một ô dân có quân ở hàng của bạn, sau đó bấm ô để đi và chọn hướng rải bạn muốn.
            </p>
            <p>
              Quân được rải lần lượt theo hướng đã chọn. Nếu gặp một ô trống
              và ngay sau đó là ô có quân, người chơi được ăn số quân trong ô đó theo luật Ô ăn quan.
            </p>
            <p>
              Nếu tiếp tục xuất hiện các cặp ô trống – ô có quân hợp lệ, người chơi được ăn liên tiếp.
            </p>
            <p>
              Mỗi quân dân được tính 1 điểm còn quân quan được tính 10 điểm. Khi ván đấu kết thúc,
              người có tổng điểm cao hơn sẽ chiến thắng.
            </p>
            <div className="match-dialog-actions">
              <button
                className="ingame-primary-action"
                onClick={() => void closeGuide()}
                disabled={botActionPending}
              >
                {botActionPending ? "Đang tiếp tục..." : "Tiếp tục chơi"}
              </button>
            </div>
          </section>
        </div>
      )}
      {showPause && (
        <div className="match-dialog-backdrop">
          <section
            className="match-dialog bot-pause-dialog ingame-dialog has-back-button"
            role="dialog"
            aria-modal="true"
            aria-labelledby="bot-pause-title"
          >
            <button
              className="card-back-button"
              onClick={() => void continueGame()}
              disabled={botActionPending}
              aria-label="Quay lại"
              title="Quay lại"
            >
              <Icon name="arrow-left" size={19} />
            </button>
            <h2 id="bot-pause-title">Tạm dừng</h2>
            <button
              className="button button-primary bot-continue-button"
              onClick={() => void continueGame()}
              disabled={botActionPending}
            >
              {botActionPending ? "Đang tiếp tục..." : "Tiếp tục"}
            </button>
            <div className="bot-pause-actions">
              <button onClick={showGuideFromPause} disabled={botActionPending}>
                Hướng dẫn
              </button>
              <button
                className="confirm-surrender"
                onClick={() => {
                  setShowPause(false);
                  setConfirmExit(true);
                }}
                disabled={botActionPending}
              >
                Thoát
              </button>
            </div>
          </section>
        </div>
      )}
      {confirmExit && (
        <div className="match-dialog-backdrop">
          <section
            className="match-dialog ingame-dialog has-back-button"
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="exit-match-title"
          >
            <button
              className="card-back-button"
              onClick={() => void cancelExitConfirmation()}
              disabled={botActionPending || exitPending}
              aria-label="Quay lại"
              title="Quay lại"
            >
              <Icon name="arrow-left" size={19} />
            </button>
            <h2 id="exit-match-title">
              {isSoloGame ? "Xác nhận thoát" : "Thoát ván đấu?"}
            </h2>
            <p>
              {board.mode === "BOT"
                ? "Bạn có chắc muốn thoát ván đấu với máy?"
                : board.mode === "LOCAL"
                  ? "Bạn có chắc muốn thoát ván đấu với bạn? Tỉ số tái đấu trong phiên này sẽ được xoá."
                  : "Thoát lúc này được tính là xin đầu hàng và kết thúc ván."}
            </p>
            <div className="match-dialog-actions">
              {isSoloGame ? (
                <>
                  <button
                    onClick={() => void cancelExitConfirmation()}
                    disabled={botActionPending || exitPending}
                  >
                    Không
                  </button>
                  <button
                    className="confirm-surrender"
                    onClick={() => void exitMatch()}
                    disabled={botActionPending || exitPending}
                  >
                    {exitPending ? "Đang thoát..." : "Có"}
                  </button>
                </>
              ) : (
                <>
                  <button onClick={() => setConfirmExit(false)}>Ở lại</button>
                  <button className="confirm-surrender" onClick={exitMatch}>
                    {board.mode === "LOCAL"
                      ? "Thoát chế độ"
                      : "Đầu hàng và thoát"}
                  </button>
                </>
              )}
            </div>
          </section>
        </div>
      )}
      {confirmAction && (
        <div className="match-dialog-backdrop">
          <section
            className="match-dialog ingame-dialog has-back-button"
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="turn-action-title"
            aria-describedby="turn-action-description"
          >
            <button
              className="card-back-button"
              onClick={() => setConfirmAction("")}
              aria-label="Quay lại"
              title="Quay lại"
            >
              <Icon name="arrow-left" size={19} />
            </button>
            <h2 id="turn-action-title">
              {confirmAction === "pass"
                ? "Xác nhận bỏ lượt?"
                : "Xác nhận đầu hàng?"}
            </h2>
            <p id="turn-action-description">
              {confirmAction === "pass"
                ? "Hệ thống sẽ tự chọn một ô và đi thay bạn. Bỏ lượt hai lần trong ván sẽ bị xử thua."
                : "Đầu hàng sẽ kết thúc ván đấu và tính kết quả thua cho bạn."}
            </p>
            <div className="match-dialog-actions">
              <button onClick={() => setConfirmAction("")}>Quay lại</button>
              <button className="confirm-surrender" onClick={confirmTurnAction}>
                Xác nhận
              </button>
            </div>
          </section>
        </div>
      )}
      {board.gameOver && !isAnimating && (isSoloGame || isOnlineGame) && (
        <MatchResult
          board={board}
          playerSlot={playerSlot}
          rematchStatus={rematchStatus}
          onExit={board.mode === "LOCAL" ? onExitLocal : board.mode === "BOT" ? onExitBot : onExitRanked}
          onReplay={isSoloGame ? onRematch : () => onAction("rematch")}
        />
      )}
    </div>
  );
}
