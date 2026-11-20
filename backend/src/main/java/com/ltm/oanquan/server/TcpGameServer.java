package com.ltm.oanquan.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ltm.oanquan.dao.MatchDao;
import com.ltm.oanquan.dao.CommunityChatDao;
import com.ltm.oanquan.dao.UserDao;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shared account, session, and game services used by TCP and WebSocket clients. */
public final class TcpGameServer implements AutoCloseable {

    private final AccountService taiKhoan = new AccountService();
    private final UserDao userDao = new UserDao();
    private final MatchDao matchDao = new MatchDao();
    private final SessionRegistry sessions = new SessionRegistry();
    private final Set<ClientHandler> activeClients = ConcurrentHashMap.newKeySet();
    private final GameService gameService;
    private final CommunityChatService communityChat = new CommunityChatService(new CommunityChatDao(), message -> {
        for (ClientHandler client : activeClients) {
            if (client.currentUser() == null) continue;
            try { client.sendEvent("community_message", message); }
            catch (RuntimeException ignored) { /* A disconnected recipient must not block others. */ }
        }
    });
    private final AtomicBoolean closed = new AtomicBoolean();

    public TcpGameServer() {
        this(new ObjectMapper(), 20_000, 45_000);
    }

    public TcpGameServer(ObjectMapper json, int timeControlMs, int disconnectGraceMs) {
        this.gameService = new GameService(matchDao, json, timeControlMs, disconnectGraceMs);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) { gameService.close(); communityChat.close(); }
    }

    AccountService taiKhoan() { return taiKhoan; }
    UserDao userDao() { return userDao; }
    MatchDao matchDao() { return matchDao; }
    SessionRegistry sessions() { return sessions; }
    GameService gameService() { return gameService; }
    CommunityChatService communityChat() { return communityChat; }
    void registerClient(ClientHandler client) { activeClients.add(client); }
    void unregisterClient(ClientHandler client) { activeClients.remove(client); }

    int onlineUserCount() {
        return (int) activeClients.stream()
                .map(ClientHandler::currentUser)
                .filter(java.util.Objects::nonNull)
                .map(user -> user.getId())
                .distinct()
                .count();
    }
}
