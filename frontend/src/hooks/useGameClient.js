import { useCallback, useEffect, useRef, useState } from "react";
import { WS_URL } from "../config.js";

export default function useGameClient(addNotice) {
  const socketRef = useRef(null);
  const pendingRef = useRef([]);
  const [connected, setConnected] = useState(false);
  const [connectionIssue, setConnectionIssue] = useState(false);
  const [auth, setAuth] = useState(null);
  const [roomCode, setRoomCode] = useState("");
  const [roomPlayers, setRoomPlayers] = useState([]);
  const [board, setBoard] = useState(null);
  const [messages, setMessages] = useState([]);
  const [communityMessages, setCommunityMessages] = useState([]);
  const [queueing, setQueueing] = useState(false);
  const [waitingRooms, setWaitingRooms] = useState([]);
  const [onlineCount, setOnlineCount] = useState(null);
  const [rematchStatus, setRematchStatus] = useState("none");

  const request = useCallback((action, fields = {}) => new Promise((resolve, reject) => {
    const socket = socketRef.current;
    if (!socket || socket.readyState !== WebSocket.OPEN) {
      reject(new Error("Chưa kết nối được tới máy chủ."));
      return;
    }
    pendingRef.current.push({ resolve, reject });
    socket.send(JSON.stringify({ action, ...fields }));
  }), []);

  useEffect(() => {
    let active = true;
    let retryTimer;
    let retryCount = 0;

    function connect() {
      if (!active) return;
      const socket = new WebSocket(WS_URL);
      socketRef.current = socket;
      socket.addEventListener("open", async () => {
        retryCount = 0;
        setConnected(true);
        setConnectionIssue(false);
        const saved = sessionStorage.getItem("oanquan.auth");
        if (!saved) return;
        let session;
        try {
          session = JSON.parse(saved);
          const profile = await request("profile", { token: session.token });
          setAuth({ ...profile, token: session.token });
        } catch {
          sessionStorage.removeItem("oanquan.auth");
          sessionStorage.removeItem("oanquan.room");
          return;
        }
        try {
          const resumed = await request("reconnect", { token: session.token });
          if (resumed.status === "NO_ACTIVE_MATCH") {
            setRoomCode("");
            sessionStorage.removeItem("oanquan.room");
          }
        } catch (error) {
          addNotice(error.message);
        }
      });
      socket.addEventListener("close", () => {
        setConnected(false);
        setOnlineCount(null);
        setBoard(null);
        pendingRef.current.splice(0).forEach(({ reject }) => reject(new Error("Mất kết nối tới máy chủ.")));
        if (active) {
          setConnectionIssue(true);
          const delay = Math.min(1000 * (2 ** retryCount), 15000);
          retryCount += 1;
          retryTimer = window.setTimeout(connect, delay);
        }
      });
      socket.addEventListener("error", () => {
        setConnected(false);
        setOnlineCount(null);
        setConnectionIssue(true);
      });
      socket.addEventListener("message", (event) => {
        let message;
        try {
          message = JSON.parse(event.data);
        } catch {
          addNotice("Máy chủ gửi dữ liệu không hợp lệ.");
          return;
        }
        if (message.type === "event") {
          if (message.event === "state" || message.event === "match_started"
            || message.event === "match_found") {
            if (message.event === "match_started") setRematchStatus("none");
            if (message.event === "match_started") setRematchStatus("none");
            if (message.event !== "state") setMessages([]);
            setBoard(message.data);
            if (message.data?.code) {
              setRoomCode(message.data.code);
              sessionStorage.setItem("oanquan.room", message.data.code);
            }
            setRoomPlayers([]);
            setQueueing(false);
          } else if (message.event === "room_expired") {
            setRoomCode("");
            setRoomPlayers([]);
            sessionStorage.removeItem("oanquan.room");
            addNotice("Phòng đã hết hạn sau 1 phút chờ.");
          } else if (message.event === "room_waiting") {
            setRoomCode(message.data.code);
            setRoomPlayers(message.data.playerNames || []);
            sessionStorage.setItem("oanquan.room", message.data.code);
            addNotice(`Đã tạo phòng ${message.data.code}. Gửi mã này cho bạn bè nhé!`);
          } else if (message.event === "room_player_joined") {
            setRoomPlayers(message.data.playerNames || []);
          } else if (message.event === "room_player_left") {
            setRoomPlayers(message.data.playerNames || []);
          } else if (message.event === "room_cancelled") {
            setRoomCode("");
            setRoomPlayers([]);
            sessionStorage.removeItem("oanquan.room");
            addNotice("Chủ phòng đã huỷ phòng.");
          } else if (message.event === "matchmaking_queued") {
            setQueueing(true);
            addNotice("Đang tìm đối thủ...");
          } else if (message.event === "community_message") {
            setCommunityMessages((current) => [...current.slice(-499), message.data]);
          } else if (message.event === "chat") {
            setMessages((current) => [...current.slice(-29), message.data]);
          } else if (message.event === "player_disconnected") {
            addNotice("Đối thủ mất kết nối. Đang chờ họ quay lại...");
          } else if (message.event === "player_reconnected") {
            if (message.data?.code) setRoomCode(message.data.code);
            addNotice("Đối thủ đã kết nối lại.");
          } else if (message.event === "rematch_requested") {
            setRematchStatus("requested");
          } else if (message.event === "rematch_unavailable") {
            setRematchStatus("unavailable");
          } else if (message.event === "match_ended") {
            if (message.data?.mode === "RANKED") {
              const saved = sessionStorage.getItem("oanquan.auth");
              if (saved) {
                try {
                  const session = JSON.parse(saved);
                  void request("profile", { token: session.token }).then((profile) => {
                    const updated = { ...profile, token: session.token };
                    sessionStorage.setItem("oanquan.auth", JSON.stringify(updated));
                    setAuth(updated);
                  }).catch((error) => addNotice(error.message));
                } catch {
                  addNotice("Không thể cập nhật hồ sơ xếp hạng.");
                }
              }
            }
            if (message.data?.mode !== "RANKED") {
              if (message.data?.mode !== "BOT") setBoard(null);
              setMessages([]);
              setRoomCode("");
              setRoomPlayers([]);
              sessionStorage.removeItem("oanquan.room");
            }
            addNotice("Ván đấu đã kết thúc.");
          }
          return;
        }
        const pending = pendingRef.current.shift();
        if (message.type === "error") {
          pending?.reject(new Error(message.message || "Đã có lỗi xảy ra."));
        } else if (message.type === "ok") {
          pending?.resolve(message.data || {});
        }
      });
    }

    retryTimer = window.setTimeout(connect, 0);
    return () => {
      active = false;
      window.clearTimeout(retryTimer);
      socketRef.current?.close();
      socketRef.current = null;
    };
  }, [addNotice, request]);

  const authenticate = useCallback(async (mode, credentials) => {
    try {
      const action = mode === "login" ? "login" : "register";
      const result = await request(action, credentials);
      if (action === "register") {
        addNotice("Tạo tài khoản thành công. Đăng nhập để bắt đầu nhé!");
        return true;
      }
      const session = { ...result, token: result.token };
      sessionStorage.setItem("oanquan.auth", JSON.stringify(session));
      setAuth(session);
      void request("reconnect", { token: session.token }).then((resumed) => {
        if (resumed.status === "NO_ACTIVE_MATCH") {
          setRoomCode("");
          sessionStorage.removeItem("oanquan.room");
        }
      }).catch((error) => addNotice(error.message));
      return true;
    } catch (error) {
      addNotice(error.message);
      return false;
    }
  }, [addNotice, request]);

  const act = useCallback(async (action, fields = {}) => {
    if (!auth) return;
    try {
      const result = await request(action, { ...fields, token: auth.token });
      if (result.code) {
        setRoomCode(result.code);
        sessionStorage.setItem("oanquan.room", result.code);
      }
      if (result.playerNames) setRoomPlayers(result.playerNames);
      if (action === "find_match" && result.status === "QUEUED") setQueueing(true);
      if (action === "cancel_matchmaking") setQueueing(false);
      if (action === "rematch") setRematchStatus(result.status === "WAITING" ? "waiting" : "none");
      if (action === "exit_match") {
        setBoard(null); setMessages([]); setRoomCode(""); setRoomPlayers([]); setRematchStatus("none");
        sessionStorage.removeItem("oanquan.room");
      }
      if (action === "state") setBoard(result);
      if (action === "list_rooms") setWaitingRooms(result.rooms || []);
      if (action === "create_bot" || action === "join_room") setMessages([]);
      return result;
    } catch (error) {
      addNotice(error.message);
    }
  }, [addNotice, auth, request]);

  useEffect(() => {
    if (!auth || !connected) return undefined;
    let active = true;
    const refresh = () => {
      request("online_count").then(({ count }) => {
        if (active) setOnlineCount(count);
      }).catch((error) => {
        if (active) addNotice(error.message);
      });
    };
    refresh();
    const timer = window.setInterval(refresh, 10000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [addNotice, auth, connected, request]);

  const signOut = useCallback(() => {
    request("logout").catch((error) => addNotice(error.message));
    sessionStorage.removeItem("oanquan.auth");
    sessionStorage.removeItem("oanquan.room");
    setAuth(null);
    setCommunityMessages([]);
    setOnlineCount(null);
    setBoard(null);
    setRoomCode("");
    setRoomPlayers([]);
    setQueueing(false);
  }, [addNotice, request]);

  const clearFinishedGame = useCallback(() => {
    setBoard(null);
    setMessages([]);
    setRoomCode("");
    setRoomPlayers([]);
    setRematchStatus("none");
    sessionStorage.removeItem("oanquan.room");
  }, []);

  const cancelRoom = useCallback(() => {
    void act("cancel_room", { code: roomCode });
    setRoomCode("");
    setRoomPlayers([]);
    sessionStorage.removeItem("oanquan.room");
  }, [act, roomCode]);

  const communityRequest = useCallback((action, fields = {}) => {
    if (!auth) return Promise.reject(new Error("Vui lòng đăng nhập để chat."));
    return request(action, { ...fields, token: auth.token });
  }, [auth, request]);

  return {
    connected,
    connectionIssue,
    auth,
    roomCode,
    roomPlayers,
    board,
    messages,
    communityMessages,
    communityRequest,
    queueing,
    waitingRooms,
    onlineCount,
    rematchStatus,
    authenticate,
    act,
    signOut,
    cancelRoom,
    clearFinishedGame,
  };
}
