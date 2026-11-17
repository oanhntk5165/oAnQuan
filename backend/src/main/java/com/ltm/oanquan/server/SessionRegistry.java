package com.ltm.oanquan.server;

import com.ltm.oanquan.model.Users;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory bearer tokens used to authenticate TCP requests and reconnect players. */
final class SessionRegistry {

    private static final long SESSION_LIFETIME_SECONDS = 12 * 60 * 60;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    String tao(Users user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, new Session(user, Instant.now().plusSeconds(SESSION_LIFETIME_SECONDS)));
        return token;
    }

    Users tim(String token) {
        if (token == null) return null;
        Session session = sessions.get(token);
        if (session == null) return null;
        if (Instant.now().isAfter(session.expiresAt)) {
            sessions.remove(token, session);
            return null;
        }
        return session.user;
    }

    void xoa(String token) {
        if (token != null) sessions.remove(token);
    }

    private record Session(Users user, Instant expiresAt) { }
}
