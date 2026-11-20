package com.ltm.oanquan.server;

import com.ltm.oanquan.dao.CommunityChatDao;
import com.ltm.oanquan.model.Users;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

final class CommunityChatService implements AutoCloseable {
    private final CommunityChatDao dao;
    private final Consumer<Map<String, Object>> broadcast;
    private final LongSupplier clock;
    private final Map<Long, Long> lastSent = new HashMap<>();
    // Never acquire other ClientHandler locks while processing a sender's request.
    private final ExecutorService delivery = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "community-chat-delivery");
        thread.setDaemon(true);
        return thread;
    });

    CommunityChatService(CommunityChatDao dao, Consumer<Map<String, Object>> broadcast) {
        this(dao, broadcast, System::currentTimeMillis);
    }

    CommunityChatService(CommunityChatDao dao, Consumer<Map<String, Object>> broadcast, LongSupplier clock) {
        this.dao = dao; this.broadcast = broadcast; this.clock = clock;
    }

    synchronized Map<String, Object> send(Users user, String raw) {
        if (user == null) throw new IllegalArgumentException("Vui lòng đăng nhập để gửi tin nhắn.");
        if (raw == null || raw.replace('\u00a0', ' ').isBlank())
            throw new IllegalArgumentException("Tin nhắn không được để trống.");
        if (raw.length() > 500) throw new IllegalArgumentException("Tin nhắn tối đa 500 ký tự.");
        long now = clock.getAsLong();
        lastSent.entrySet().removeIf(entry -> now - entry.getValue() >= 1000);
        if (lastSent.containsKey(user.getId()))
            throw new IllegalArgumentException("Bạn gửi quá nhanh. Vui lòng chờ 1 giây.");
        Map<String, Object> message = dao.insert(user.getId(), user.getTenDangNhap(), raw.strip());
        lastSent.put(user.getId(), now);
        delivery.execute(() -> broadcast.accept(message));
        return message;
    }

    Map<String, Object> history(long before) {
        if (before <= 0) throw new IllegalArgumentException("Mốc lịch sử không hợp lệ.");
        List<Map<String, Object>> rows = new ArrayList<>(dao.history(before, 51));
        boolean hasMore = rows.size() > 50;
        if (hasMore) rows.remove(rows.size() - 1);
        Collections.reverse(rows);
        return Map.of("messages", rows, "hasMore", hasMore);
    }

    public void close() { delivery.shutdownNow(); }
}
