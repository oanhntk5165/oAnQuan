import { useEffect, useLayoutEffect, useRef, useState } from "react";
import Icon from "./Icon.jsx";

function mergeMessages(current, incoming) {
  const byId = new Map(current.map((message) => [message.id, message]));
  incoming.forEach((message) => byId.set(message.id, message));
  return [...byId.values()].sort((a, b) => BigInt(a.id) < BigInt(b.id) ? -1 : BigInt(a.id) > BigInt(b.id) ? 1 : 0);
}

export default function CommunityChat({ auth, connected, onlineCount, events, request, visible }) {
  const [open, setOpen] = useState(false);
  const [unread, setUnread] = useState(false);
  const [messages, setMessages] = useState([]);
  const [draft, setDraft] = useState("");
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);
  const list = useRef(null);
  const input = useRef(null);
  const launcher = useRef(null);
  const atBottom = useRef(true);
  const prepend = useRef(null);
  const seenEvent = useRef(null);
  const generation = useRef(0);
  const busy = useRef(false);
  const loaded = useRef(false);
  const latest = useRef(null);
  const viewing = useRef(false);
  viewing.current = open && visible;

  useLayoutEffect(() => {
    if (open && visible) { setUnread(false); atBottom.current = true; input.current?.focus(); }
  }, [open, visible]);

  useEffect(() => {
    if (!events.length) return;
    const fresh = events.filter((message) => seenEvent.current === null || BigInt(message.id) > BigInt(seenEvent.current));
    if (!fresh.length) return;
    seenEvent.current = fresh[fresh.length - 1].id;
    setMessages((current) => mergeMessages(current, fresh));
    if (!viewing.current && fresh.some((message) => message.userId !== auth.userId)) setUnread(true);
  }, [events, auth.userId]);

  useEffect(() => {
    const version = ++generation.current;
    busy.current = false;
    setLoading(false);
    if (!connected) return;
    busy.current = true;
    setLoading(true);
    setError("");
    request("community_history").then((result) => {
      if (version !== generation.current) return;
      if (loaded.current && latest.current && !viewing.current && result.messages.some(
        (message) => BigInt(message.id) > BigInt(latest.current) && message.userId !== auth.userId)) setUnread(true);
      // A reconnect starts a contiguous recent page; older history can be fetched again.
      setMessages((current) => mergeMessages(result.messages, current.filter((message) =>
        result.messages.length && BigInt(message.id) > BigInt(result.messages[result.messages.length - 1].id))));
      setHasMore(result.hasMore);
      loaded.current = true;
      atBottom.current = true;
    }).catch((failure) => { if (version === generation.current) setError(failure.message); })
      .finally(() => { if (version === generation.current) { busy.current = false; setLoading(false); } });
    return () => { generation.current++; };
  }, [connected, request, auth.userId, reload]);

  useLayoutEffect(() => {
    latest.current = messages.at(-1)?.id || latest.current;
    if (!open || !visible || !list.current) return;
    if (prepend.current) {
      list.current.scrollTop = prepend.current.top + list.current.scrollHeight - prepend.current.height;
      prepend.current = null;
    } else if (atBottom.current) list.current.scrollTop = list.current.scrollHeight;
  }, [messages, open, visible]);

  async function loadOlder() {
    if (busy.current || !connected || !hasMore || !messages.length) return;
    busy.current = true;
    setLoading(true);
    setError("");
    const version = generation.current;
    try {
      const result = await request("community_history", { before: messages[0].id });
      if (version !== generation.current) return;
      if (list.current) prepend.current = { top: list.current.scrollTop, height: list.current.scrollHeight };
      setMessages((current) => mergeMessages(current, result.messages));
      setHasMore(result.hasMore);
    } catch (failure) { if (version === generation.current) setError(failure.message); }
    finally { if (version === generation.current) { busy.current = false; setLoading(false); } }
  }

  async function send(event) {
    event.preventDefault();
    if (!draft.trim() || draft.length > 500 || sending || !connected) return;
    setSending(true);
    setError("");
    try {
      const message = await request("community_send", { content: draft });
      atBottom.current = true;
      setMessages((current) => mergeMessages(current, [message]));
      setDraft("");
      input.current?.focus();
    } catch (failure) { setError(failure.message); }
    finally { setSending(false); }
  }

  function close() { setOpen(false); requestAnimationFrame(() => launcher.current?.focus()); }
  if (!visible) return null;
  return <div className="community-chat">
    {!open ? <button ref={launcher} className="community-launcher" onClick={() => setOpen(true)}
      aria-expanded="false" aria-controls="community-window">
      <Icon name="chat" size={30} /><span>Chat cộng đồng</span>
      {unread && <span className="community-unread" role="status" aria-label="Có tin nhắn mới" />}
    </button> : <section id="community-window" className="community-window" role="dialog" aria-label="Chat cộng đồng"
      onKeyDown={(event) => { if (event.key === "Escape") close(); }}>
      <header className="community-heading"><Icon name="chat" size={25} /><h2>Chat cộng đồng</h2>
        <span className="community-online"><Icon name="people" size={17} />{connected ? `${onlineCount ?? "–"} online` : "Offline"}</span>
        <button onClick={close} aria-label="Đóng chat cộng đồng"><Icon name="close" size={27} /></button>
      </header>
      <div className="community-paper">
        {!connected && <p className="community-status" role="status">Mất kết nối. Đang kết nối lại…</p>}
        <div ref={list} className="community-messages" role="log" aria-label="Tin nhắn cộng đồng" aria-live="polite"
          onScroll={() => {
            const node = list.current;
            atBottom.current = node.scrollHeight - node.scrollTop - node.clientHeight < 60;
            if (node.scrollTop < 24) void loadOlder();
          }}>
          {hasMore && <button className="community-older" onClick={loadOlder} disabled={loading || !connected}>Tin nhắn trước đó</button>}
          {loading && <p className="community-empty" role="status">Đang tải tin nhắn…</p>}
          {!messages.length && !loading && <p className="community-empty">Chào bạn! Hãy bắt đầu câu chuyện với mọi người.</p>}
          {messages.map((message) => <article key={message.id} className={`community-message ${message.userId === auth.userId ? "is-own" : ""}`}>
            <strong>{message.username}:</strong>{" "}<span>{message.content}</span>
            <time dateTime={message.sentAt}>{new Date(message.sentAt).toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" })}</time>
          </article>)}
        </div>
        {error && <p className="community-error" role="alert">{error}{!messages.length && connected &&
          <button className="community-older" onClick={() => setReload((value) => value + 1)} disabled={loading}>Thử tải lại</button>}</p>}
        <form className="community-compose" onSubmit={send}>
          <label className="community-input-wrap"><span className="sr-only">Nội dung tin nhắn</span>
            <input ref={input} value={draft} maxLength={500} placeholder="Nhập tin nhắn..." disabled={!connected || sending}
              onChange={(event) => setDraft(event.target.value)}
              onKeyDown={(event) => { if (event.key === "Enter" && event.nativeEvent.isComposing) event.preventDefault(); }} />
            <Icon name="leaf" size={24} />
          </label>
          <button className="community-send" type="submit" disabled={!connected || sending || !draft.trim()} aria-label="Gửi tin nhắn"><Icon name="send" size={25} /></button>
          <small className="community-counter">{draft.length}/500</small>
        </form>
      </div>
    </section>}
  </div>;
}
