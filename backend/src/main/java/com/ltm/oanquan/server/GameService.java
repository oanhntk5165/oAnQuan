package com.ltm.oanquan.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ltm.oanquan.dao.MatchDao;
import com.ltm.oanquan.engine.GameState;
import com.ltm.oanquan.engine.Move;
import com.ltm.oanquan.engine.MoveEvent;
import com.ltm.oanquan.engine.MoveResult;
import com.ltm.oanquan.engine.Rules;
import com.ltm.oanquan.model.Users;
import com.ltm.oanquan.model.Ranking;

import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * GameService:
 * - Quản lý phòng chờ, tìm đối thủ xếp hạng, game bot.
 * - Điều phối lượt chơi, thời gian, reconnect, rematch.
 * - Gọi Rules + MatchDao để xử lý bàn cờ và lưu lịch sử.
 *
 * Đây là core engine của game ở tầng server.
 */
final class GameService implements AutoCloseable {

    private static final String ROOM_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ELO_K_FACTOR = 32;
    private static final int RANKED_TIME_CONTROL_MS = 20_000;

    private final MatchDao matchDao;
    private final ObjectMapper json;
    private final int timeControlMs;
    private final int disconnectGraceMs;
    private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> playerRooms = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Object matchmakingLock = new Object();
    private final Queue<QueueEntry> rankedQueue = new ArrayDeque<>();
    private final Map<Long, QueueEntry> queuedPlayers = new ConcurrentHashMap<>();

    GameService(MatchDao matchDao, ObjectMapper json, int timeControlMs, int disconnectGraceMs) {
        this.matchDao = matchDao;
        this.json = json;
        this.timeControlMs = timeControlMs;
        this.disconnectGraceMs = disconnectGraceMs;
    }

    // =========================================================
    // 1) PHÒNG CHỜ / ROOM LIFECYCLE
    // - Tạo phòng, tham gia phòng, bắt đầu phòng, hủy phòng.
    // =========================================================
    Map<String, Object> taoPhong(Users user, ClientHandler client) {
        synchronized (matchmakingLock) {
            kiemTraChuaTrongTran(user);
            String code = taoMaPhong();
            Room room = new Room(code, Mode.RANKED, RANKED_TIME_CONTROL_MS);
            room.playerIds[0] = user.getId();
            room.playerNames[0] = user.getTenDangNhap();
            room.ratings[0] = user.getRating();
            room.points[0] = user.getDiem();
            room.connections[0] = client;
            rooms.put(code, room);
            playerRooms.put(user.getId(), code);
            room.roomExpiryTask = scheduler.schedule(() -> hetHanPhongCho(room), 1, TimeUnit.MINUTES);
            emit(client, "room_waiting", Map.of("code", code, "mode", "RANKED",
                    "playerNames", List.of(user.getTenDangNhap())));
            return Map.of("code", code, "mode", "RANKED", "status", "WAITING",
                    "playerNames", List.of(user.getTenDangNhap()), "expiresInMs", 60_000);
        }
    }

    Map<String, Object> phongDangCho() {
        List<Map<String, Object>> waitingRooms = new ArrayList<>();
        for (Room room : rooms.values()) {
            synchronized (room) {
                if ((room.mode == Mode.FRIENDS || room.mode == Mode.RANKED)
                        && room.state == null && room.playerIds[0] != 0) {
                    waitingRooms.add(Map.of(
                            "code", room.code,
                            "host", room.playerNames[0]));
                }
            }
        }
        waitingRooms.sort((first, second) ->
                ((String) first.get("code")).compareTo((String) second.get("code")));
        return Map.of("rooms", waitingRooms);
    }

    Map<String, Object> thamGiaPhong(Users user, ClientHandler client, String rawCode) {
        String code = chuanHoaMa(rawCode);
        Room room = rooms.get(code);
        if (room == null) throw new IllegalArgumentException("Không tìm thấy phòng");
        synchronized (matchmakingLock) {
            kiemTraChuaTrongTran(user);
            synchronized (room) {
                if (rooms.get(code) != room) throw new IllegalArgumentException("Phòng không còn tồn tại");
                if (room.state != null) throw new IllegalStateException("Phòng đã bắt đầu trận đấu");
                if (room.playerIds[0] == user.getId()) {
                    throw new IllegalArgumentException("Không thể tham gia phòng của chính mình");
                }
                if (room.playerIds[1] != 0) throw new IllegalStateException("Phòng đã đủ người chơi");
                room.playerIds[1] = user.getId();
                room.playerNames[1] = user.getTenDangNhap();
                room.ratings[1] = user.getRating();
                room.points[1] = user.getDiem();
                room.connections[1] = client;
                playerRooms.put(user.getId(), code);
                Map<String, Object> waiting = Map.of("code", code, "mode", room.mode.name(),
                        "playerNames", List.of(room.playerNames[0], room.playerNames[1]));
                emit(room.connections[0], "room_player_joined", waiting);
                emit(room.connections[1], "room_player_joined", waiting);
                return Map.of("code", code, "status", "WAITING",
                        "playerNames", List.of(room.playerNames[0], room.playerNames[1]),
                        "expiresInMs", Math.max(0, 60_000 - (System.currentTimeMillis() - room.createdAtMillis)));
            }
        }
    }

    Map<String, Object> batDauPhong(Users user, ClientHandler client, String rawCode) {
        String code = chuanHoaMa(rawCode);
        Room room = rooms.get(code);
        if (room == null) throw new IllegalArgumentException("Phòng không còn tồn tại");
        synchronized (room) {
            if (rooms.get(code) != room || room.state != null) {
                throw new IllegalStateException("Phòng không ở trạng thái chờ");
            }
            if (room.playerIds[0] != user.getId() || room.connections[0] != client) {
                throw new IllegalStateException("Chỉ chủ phòng mới có thể bắt đầu");
            }
            if (room.playerIds[1] == 0) throw new IllegalStateException("Cần đủ hai người chơi để bắt đầu");
            if (room.mode == Mode.RANKED) {
                room.matchId = matchDao.taoVan(room.playerIds[0], room.playerIds[1],
                        room.mode.name(), room.timeControlMs);
            }
            room.state = GameState.initial();
            room.remainingMs[0] = room.timeControlMs;
            room.remainingMs[1] = room.timeControlMs;
            if (room.roomExpiryTask != null) room.roomExpiryTask.cancel(false);
            batDauDongHo(room);
            phatTrangThai(room, "match_started");
            return Map.of("code", code, "matchId", room.matchId, "status", "STARTED");
        }
    }

    Map<String, Object> taoTranBot(Users user, ClientHandler client, boolean userGoesFirst) {
        synchronized (matchmakingLock) {
            kiemTraChuaTrongTran(user);
            String code = taoMaPhong();
            Room room = new Room(code, Mode.BOT, timeControlMs);
            room.playerIds[0] = user.getId();
            room.playerNames[0] = user.getTenDangNhap();
            room.ratings[0] = user.getRating();
            room.points[0] = user.getDiem();
            room.connections[0] = client;
            room.playerNames[1] = "Máy";
            room.state = GameState.initial(userGoesFirst ? 0 : 1);
            room.remainingMs[0] = timeControlMs;
            room.remainingMs[1] = timeControlMs;
            rooms.put(code, room);
            playerRooms.put(user.getId(), code);
            synchronized (room) {
                batDauDongHo(room);
                phatTrangThai(room, "match_started");
            }
            return Map.of("code", code, "matchId", room.matchId, "mode", "BOT", "status", "STARTED");
        }
    }

    Map<String, Object> tamDungTranBot(Users user, ClientHandler client) {
        Room room = roomCuaNguoiChoi(user);
        synchronized (room) {
            int slot = kiemTraThanhVien(room, user, client);
            if (room.mode != Mode.BOT || slot != 0) {
                throw new IllegalStateException("Chỉ có thể tạm dừng ván đấu với máy");
            }
            if (room.state == null || room.state.isOver()) {
                throw new IllegalStateException("Ván đấu đã kết thúc");
            }
            room.paused = true;
            dungDongHo(room);
            return Map.of("status", "PAUSED");
        }
    }

    Map<String, Object> tiepTucTranBot(Users user, ClientHandler client) {
        Room room = roomCuaNguoiChoi(user);
        synchronized (room) {
            int slot = kiemTraThanhVien(room, user, client);
            if (room.mode != Mode.BOT || slot != 0) {
                throw new IllegalStateException("Chỉ có thể tiếp tục ván đấu với máy");
            }
            if (room.state == null || room.state.isOver()) {
                throw new IllegalStateException("Ván đấu đã kết thúc");
            }
            room.paused = false;
            batDauDongHo(room);
            return Map.of("status", "RUNNING");
        }
    }

    // =========================================================
    // 2) MATCHMAKING XẾP HẠNG
    // - Tìm người chơi có rating gần nhất trong hàng chờ.
    // - Khởi tạo room và đánh dấu trận bắt đầu ngay khi ghép xong.
    // =========================================================
    Map<String, Object> timTran(Users user, ClientHandler client) {
        synchronized (matchmakingLock) {
            kiemTraChuaTrongTran(user);
            if (queuedPlayers.containsKey(user.getId())) {
                throw new IllegalStateException("Bạn đang ở hàng chờ ghép trận");
            }
            QueueEntry opponent = null;
            long closestRatingGap = Long.MAX_VALUE;
            for (var iterator = rankedQueue.iterator(); iterator.hasNext();) {
                QueueEntry next = iterator.next();
                if (playerRooms.get(next.user.getId()) != null) {
                    iterator.remove();
                    queuedPlayers.remove(next.user.getId(), next);
                    continue;
                }
                long gap = Math.abs((long) next.user.getRating() - user.getRating());
                if (next.user.getId() != user.getId()
                        && Ranking.tenBac(Ranking.bac(next.user.getDiem()))
                                .equals(Ranking.tenBac(Ranking.bac(user.getDiem())))
                        && gap < closestRatingGap) {
                    opponent = next;
                    closestRatingGap = gap;
                }
            }
            if (opponent != null) {
                rankedQueue.remove(opponent);
                queuedPlayers.remove(opponent.user.getId(), opponent);
            }
            if (opponent == null) {
                QueueEntry entry = new QueueEntry(user, client);
                rankedQueue.add(entry);
                queuedPlayers.put(user.getId(), entry);
                emit(client, "matchmaking_queued", Map.of("mode", "RANKED"));
                return Map.of("status", "QUEUED", "mode", "RANKED");
            }
            try {
                String code = taoMaPhong();
                Room room = new Room(code, Mode.RANKED, RANKED_TIME_CONTROL_MS);
                room.playerIds[0] = opponent.user.getId();
                room.playerIds[1] = user.getId();
                room.playerNames[0] = opponent.user.getTenDangNhap();
                room.playerNames[1] = user.getTenDangNhap();
                room.ratings[0] = opponent.user.getRating();
                room.ratings[1] = user.getRating();
                room.points[0] = opponent.user.getDiem();
                room.points[1] = user.getDiem();
                room.connections[0] = opponent.client;
                room.connections[1] = client;
                room.matchId = matchDao.taoVan(room.playerIds[0], room.playerIds[1],
                        "RANKED", RANKED_TIME_CONTROL_MS);
                room.state = GameState.initial();
                room.remainingMs[0] = room.timeControlMs;
                room.remainingMs[1] = room.timeControlMs;
                rooms.put(code, room);
                playerRooms.put(room.playerIds[0], code);
                playerRooms.put(room.playerIds[1], code);
                synchronized (room) {
                    batDauDongHo(room);
                    phatTrangThai(room, "match_found");
                }
                return Map.of("status", "MATCHED", "code", code, "matchId", room.matchId);
            } catch (RuntimeException e) {
                rankedQueue.add(opponent);
                queuedPlayers.put(opponent.user.getId(), opponent);
                throw e;
            }
        }
    }

    Map<String, Object> huyTimTran(Users user) {
        synchronized (matchmakingLock) {
            QueueEntry entry = queuedPlayers.remove(user.getId());
            if (entry == null) return Map.of("status", "NOT_QUEUED");
            rankedQueue.remove(entry);
            return Map.of("status", "CANCELLED");
        }
    }

    // =========================================================
    // 3) NƯỚC ĐI / TÍNH ĐIỂM / KẾT THÚC TRẬN
    // - Áp dụng Rules.play(), cập nhật bàn cờ, đồng hồ, điểm.
    // - Ghi move vào MatchDao và kết thúc ván nếu cần.
    // =========================================================
    Map<String, Object> diChuyen(Users user, ClientHandler client, int position, int direction) {
        Room room = roomCuaNguoiChoi(user);
        synchronized (room) {
            int slot = kiemTraNguoiChoi(room, user, client);
            kiemTraChuaTamDung(room);
            if (kiemTraHetGio(room)) return Map.of("status",
                    room.state.isOver() ? "FINISHED" : "TURN_SKIPPED", "reason", "TIMEOUT");
            if (room.state.turn() != slot) throw new IllegalStateException("Chưa đến lượt của bạn");

            long now = System.nanoTime();
            int spent = room.mode == Mode.BOT ? 0 : thoiGianDaDung(room, slot, now);
            GameState nextState = room.state.copy();
            MoveResult result = Rules.play(nextState, new Move(position, direction));
            int[] nextRemaining = room.remainingMs.clone();
            int[] nextUsed = room.usedMs.clone();
            apDungThoiGian(nextRemaining, nextUsed, slot, spent);
            int nextMoveNo = room.moveNo + 1;
            MatchDao.Completion completion = nextState.isOver()
                    ? ketQua(room, nextState, "NORMAL", nextUsed) : null;
            if (room.mode == Mode.RANKED) {
                matchDao.ghiNuoc(room.matchId, nextMoveNo, slot, "MOVE", position, direction,
                        result.gained(), spent, boardJson(nextState), completion);
            }
            room.state = nextState;
            room.remainingMs = nextRemaining;
            room.usedMs = nextUsed;
            room.moveNo = nextMoveNo;
            room.consecutivePasses = 0;
            if (nextState.isOver()) ketThucTrongPhong(room, completion);
            else batDauDongHo(room, result.events().size());
            phatTrangThai(room, "state", result.events());
            return Map.of("status", nextState.isOver() ? "FINISHED" : "MOVED",
                    "gained", result.gained());
        }
    }

    Map<String, Object> boLuot(Users user, ClientHandler client) {
        Room room = roomCuaNguoiChoi(user);
        synchronized (room) {
            int slot = kiemTraNguoiChoi(room, user, client);
            kiemTraChuaTamDung(room);
            if (kiemTraHetGio(room)) return Map.of("status",
                    room.state.isOver() ? "FINISHED" : "TURN_SKIPPED", "reason", "TIMEOUT");
            if (room.state.turn() != slot) throw new IllegalStateException("Chưa đến lượt của bạn");
            long now = System.nanoTime();
            int spent = room.mode == Mode.BOT ? 0 : thoiGianDaDung(room, slot, now);
            int nextConsecutive = room.consecutivePasses + 1;
            int nextTotal = room.totalPasses[slot] + 1;
            boolean lost = nextTotal >= 2;
            GameState nextState = room.state.copy();
            Move autoMove = null;
            MoveResult moveResult = null;
            if (lost) nextState.forfeit(slot);
            else {
                List<Move> legalMoves = Rules.legalMoves(nextState);
                if (legalMoves.isEmpty()) {
                    nextState.forfeit(slot);
                    lost = true;
                } else {
                    autoMove = legalMoves.get(RANDOM.nextInt(legalMoves.size()));
                    moveResult = Rules.play(nextState, autoMove);
                    lost = nextState.isOver();
                }
            }
            int[] nextRemaining = room.remainingMs.clone();
            int[] nextUsed = room.usedMs.clone();
            apDungThoiGian(nextRemaining, nextUsed, slot, spent);
            int nextMoveNo = room.moveNo + 1;
            MatchDao.Completion completion = lost
                    ? ketQua(room, nextState, nextTotal >= 2 ? "PASS_LIMIT" : "NORMAL", nextUsed) : null;
            if (room.mode == Mode.RANKED) {
                matchDao.ghiNuoc(room.matchId, nextMoveNo, slot, autoMove == null ? "PASS" : "AUTO_MOVE",
                        autoMove == null ? null : autoMove.pit(), autoMove == null ? null : autoMove.dir(),
                        moveResult == null ? 0 : moveResult.gained(), spent, boardJson(nextState), completion);
            }
            room.state = nextState;
            room.remainingMs = nextRemaining;
            room.usedMs = nextUsed;
            room.totalPasses[slot] = nextTotal;
            room.consecutivePasses = nextConsecutive;
            room.moveNo = nextMoveNo;
            if (lost) ketThucTrongPhong(room, completion);
            else batDauDongHo(room, moveResult == null ? 0 : moveResult.events().size());
            phatTrangThai(room, "state", moveResult == null ? List.of() : moveResult.events());
            return Map.of("status", lost ? "FINISHED" : "PASSED",
                    "consecutivePasses", room.consecutivePasses, "totalPasses", nextTotal);
        }
    }

    Map<String, Object> taiDau(Users user, ClientHandler client) {
        Room room = roomCuaNguoiChoi(user);
        synchronized (room) {
            int slot = kiemTraThanhVien(room, user, client);
            if (room.mode != Mode.RANKED || room.completion == null) throw new IllegalStateException("Ván đấu chưa kết thúc");
            if (room.exited[0] || room.exited[1]) throw new IllegalStateException("Không thể tái đấu vì người chơi đã rời phòng");
            room.rematchVotes[slot] = true;
            if (!room.rematchVotes[1 - slot]) {
                emit(room.connections[1 - slot], "rematch_requested", Map.of("username", room.playerNames[slot]));
                return Map.of("status", "WAITING");
            }
            room.matchId = matchDao.taoVan(room.playerIds[0], room.playerIds[1], "RANKED", room.timeControlMs);
            room.state = GameState.initial();
            room.completion = null;
            room.completedAtMillis = 0;
            room.moveNo = 0;
            room.consecutivePasses = 0;
            room.totalPasses[0] = room.totalPasses[1] = 0;
            room.timeoutCounts[0] = room.timeoutCounts[1] = 0;
            room.usedMs[0] = room.usedMs[1] = 0;
            room.remainingMs[0] = room.remainingMs[1] = room.timeControlMs;
            room.rematchVotes[0] = room.rematchVotes[1] = false;
            batDauDongHo(room);
            phatTrangThai(room, "match_started");
            return Map.of("status", "STARTED", "code", room.code, "matchId", room.matchId);
        }
    }

    Map<String, Object> thoatTran(Users user, ClientHandler client) {
        Room room = roomCuaNguoiChoi(user);
        synchronized (room) {
            int slot = kiemTraThanhVien(room, user, client);
            if (room.mode != Mode.RANKED || room.completion == null) throw new IllegalStateException("Ván đấu chưa kết thúc");
            room.exited[slot] = true;
            playerRooms.remove(user.getId(), room.code);
            emit(room.connections[1 - slot], "rematch_unavailable", Map.of("username", room.playerNames[slot]));
            room.connections[slot] = null;
            if (room.exited[0] && room.exited[1]) rooms.remove(room.code, room);
            return Map.of("status", "EXITED");
        }
    }

    Map<String, Object> dauHang(Users user, ClientHandler client) {
        Room room = roomCuaNguoiChoi(user);
        synchronized (room) {
            int slot = kiemTraNguoiChoi(room, user, client);
            if (kiemTraHetGio(room)) return Map.of("status",
                    room.state.isOver() ? "FINISHED" : "TURN_SKIPPED", "reason", "TIMEOUT");
            if (!room.paused) capNhatThoiGianHienTai(room);
            GameState nextState = room.state.copy();
            nextState.forfeit(slot);
            MatchDao.Completion completion = ketQua(room, nextState, "RESIGN", room.usedMs);
            if (room.mode == Mode.RANKED) matchDao.ketThucVan(room.matchId, completion);
            room.state = nextState;
            ketThucTrongPhong(room, completion);
            phatTrangThai(room, "state");
            return Map.of("status", "FINISHED", "reason", "RESIGN");
        }
    }

    Map<String, Object> chat(Users user, ClientHandler client, String rawContent) {
        Room room = roomCuaNguoiChoi(user);
        synchronized (room) {
            int slot = kiemTraNguoiChoi(room, user, client);
            if (room.state == null || room.state.isOver()) {
                throw new IllegalStateException("Trận đấu chưa bắt đầu hoặc đã kết thúc");
            }
            String content = rawContent.strip();
            if (content.isEmpty() || content.length() > 500) {
                throw new IllegalArgumentException("Tin nhắn phải dài từ 1 đến 500 ký tự");
            }
            if (room.mode == Mode.RANKED) matchDao.themChat(room.matchId, user.getId(), content);
            Map<String, Object> message = Map.of("username", room.playerNames[slot],
                    "content", content, "sentAt", java.time.Instant.now().toString());
            for (ClientHandler connection : room.connections) emit(connection, "chat", message);
            return Map.of("status", "SENT");
        }
    }

    Map<String, Object> trangThai(Users user, ClientHandler client, String rawCode) {
        Room room = rooms.get(chuanHoaMa(rawCode));
        if (room == null) throw new IllegalArgumentException("Không tìm thấy trận đấu");
        synchronized (room) {
            kiemTraNguoiChoi(room, user, client);
            phatTrangThai(room, "state");
            return Map.of("status", "SENT");
        }
    }

    Map<String, Object> ketNoiLai(Users user, ClientHandler client, String rawCode) {
        Room room;
        if (rawCode == null || rawCode.isBlank()) {
            String code = playerRooms.get(user.getId());
            room = code == null ? null : rooms.get(code);
            if (room == null) room = tranRankGanNhat(user.getId());
            if (room == null) return Map.of("status", "NO_ACTIVE_MATCH");
        } else {
            room = rooms.get(chuanHoaMa(rawCode));
            if (room == null) throw new IllegalArgumentException("Không tìm thấy trận đấu đang diễn ra");
        }
        synchronized (room) {
            int slot = viTriNguoiChoi(room, user.getId());
            if (slot < 0 || room.state == null
                    || (room.state.isOver() && (room.mode != Mode.RANKED || room.completion == null))) {
                throw new IllegalArgumentException("Không tìm thấy trận đấu đang diễn ra");
            }
            room.connections[slot] = client;
            playerRooms.put(user.getId(), room.code);
            if (room.state.isOver()) {
                phatTrangThai(room, "state");
                return Map.of("status", "MATCH_FINISHED", "matchId", room.matchId);
            }
            room.disconnected[slot] = false;
            if (room.disconnectTasks[slot] != null) room.disconnectTasks[slot].cancel(false);
            if (!room.disconnected[0] && (room.mode == Mode.BOT || !room.disconnected[1])
                    && room.paused) {
                room.paused = false;
                batDauDongHo(room);
            }
            int opponent = 1 - slot;
            emit(room.connections[opponent], "player_reconnected",
                    Map.of("username", room.playerNames[slot]));
            phatTrangThai(room, "state");
            return Map.of("status", "RECONNECTED", "matchId", room.matchId);
        }
    }

    private Room tranRankGanNhat(long userId) {
        Room latest = null;
        for (Room candidate : rooms.values()) {
            synchronized (candidate) {
                if (candidate.mode == Mode.RANKED && candidate.state != null
                        && candidate.state.isOver() && candidate.completion != null
                        && viTriNguoiChoi(candidate, userId) >= 0
                        && (latest == null || candidate.completedAtMillis > latest.completedAtMillis)) {
                    latest = candidate;
                }
            }
        }
        return latest;
    }

    Map<String, Object> huyPhong(Users user, String rawCode) {
        String code = chuanHoaMa(rawCode);
        Room room = rooms.get(code);
        if (room == null) return Map.of("status", "NOT_FOUND");
        synchronized (room) {
            if (rooms.get(code) != room) return Map.of("status", "NOT_FOUND");
            if (room.state != null || room.playerIds[0] != user.getId()) {
                throw new IllegalStateException("Chỉ chủ phòng đang chờ mới có thể hủy phòng");
            }
            rooms.remove(code, room);
            for (long playerId : room.playerIds) {
                if (playerId != 0) playerRooms.remove(playerId, code);
            }
            if (room.roomExpiryTask != null) room.roomExpiryTask.cancel(false);
            emit(room.connections[1], "room_cancelled", Map.of("code", code));
            return Map.of("status", "CANCELLED");
        }
    }

    void ngatKetNoi(ClientHandler client) {
        Users user = client.currentUser();
        if (user == null) return;
        synchronized (matchmakingLock) {
            QueueEntry entry = queuedPlayers.remove(user.getId());
            if (entry != null) rankedQueue.remove(entry);
        }
        String code = playerRooms.get(user.getId());
        if (code == null) return;
        Room room = rooms.get(code);
        if (room == null) return;
        synchronized (room) {
            int slot = viTriNguoiChoi(room, user.getId());
            if (slot < 0 || room.connections[slot] != client) return;
            if (room.state == null) {
                room.connections[slot] = null;
                playerRooms.remove(user.getId(), code);
                if (slot == 0) {
                    rooms.remove(code, room);
                    if (room.playerIds[1] != 0) playerRooms.remove(room.playerIds[1], code);
                    if (room.roomExpiryTask != null) room.roomExpiryTask.cancel(false);
                    emit(room.connections[1], "room_cancelled", Map.of("code", code));
                } else {
                    room.playerIds[1] = 0;
                    room.playerNames[1] = null;
                    room.ratings[1] = 0;
                    emit(room.connections[0], "room_player_left", Map.of("code", code,
                            "playerNames", List.of(room.playerNames[0])));
                }
                return;
            }
            if (room.state.isOver()) {
                room.connections[slot] = null;
                if (room.mode == Mode.RANKED && room.completion != null) {
                    room.exited[slot] = true;
                    playerRooms.remove(user.getId(), code);
                    emit(room.connections[1 - slot], "rematch_unavailable",
                            Map.of("username", room.playerNames[slot]));
                    if (room.exited[0] && room.exited[1]) rooms.remove(code, room);
                }
                return;
            }
            if (kiemTraHetGio(room)) return;
            room.connections[slot] = null;
            room.disconnected[slot] = true;
            dungDongHo(room);
            if (!room.paused) {
                capNhatThoiGianHienTai(room);
                if (room.remainingMs[room.state.turn()] == 0) {
                    ketThua(room, room.state.turn(), "TIMEOUT");
                    return;
                }
                room.paused = true;
            }
            for (ClientHandler connection : room.connections) {
                emit(connection, "player_disconnected",
                        Map.of("username", room.playerNames[slot],
                                "reconnectGraceMs", disconnectGraceMs));
            }
            phatTrangThai(room, "state");
            room.disconnectTasks[slot] = scheduler.schedule(
                    () -> hetHanKetNoiLai(room, slot), disconnectGraceMs, TimeUnit.MILLISECONDS);
        }
    }

    private void hetHanKetNoiLai(Room room, int slot) {
        synchronized (room) {
            if (!room.disconnected[slot] || room.state == null || room.state.isOver()) return;
            try {
                ketThua(room, slot, "DISCONNECT");
            } catch (RuntimeException e) {
                System.err.println("Could not save disconnect result: " + e.getMessage());
                room.disconnectTasks[slot] = scheduler.schedule(
                        () -> hetHanKetNoiLai(room, slot), 5, TimeUnit.SECONDS);
            }
        }
    }

    private void hetHanPhongCho(Room room) {
        synchronized (room) {
            if (rooms.get(room.code) != room || room.state != null) return;
            rooms.remove(room.code, room);
            for (int slot = 0; slot < room.playerIds.length; slot++) {
                if (room.playerIds[slot] != 0) playerRooms.remove(room.playerIds[slot], room.code);
                emit(room.connections[slot], "room_expired", Map.of("code", room.code));
            }
        }
    }

    private void batDauDongHo(Room room) {
        batDauDongHo(room, 0);
    }

    private void batDauDongHo(Room room, int previousMoveEventCount) {
        dungDongHo(room);
        if (room.state == null || room.state.isOver() || room.paused) return;
        room.turnStartedNanos = System.nanoTime();
        int slot = room.state.turn();
        if (room.mode == Mode.BOT) {
            if (slot == 1) {
                long animationDurationMs = previousMoveEventCount == 0 ? 0
                        : (long) (previousMoveEventCount * Math.max(1,
                                Math.min(120, 4000.0 / previousMoveEventCount))) + 120;
                long turnNoticeDurationMs = 2800;
                room.clockTask = scheduler.schedule(() -> nuocBot(room),
                        animationDurationMs + turnNoticeDurationMs, TimeUnit.MILLISECONDS);
            }
            return;
        }
        long remaining = room.remainingMs[slot];
        room.clockTask = scheduler.schedule(() -> hetGio(room), remaining, TimeUnit.MILLISECONDS);
    }

    private void dungDongHo(Room room) {
        if (room.clockTask != null) room.clockTask.cancel(false);
        room.clockTask = null;
    }

    private void hetGio(Room room) {
        synchronized (room) {
            if (room.state == null || room.state.isOver() || room.paused) return;
            try {
                if (!kiemTraHetGio(room)) {
                    int slot = room.state.turn();
                    int remaining = room.remainingMs[slot] - thoiGianDaDung(room, slot, System.nanoTime());
                    room.clockTask = scheduler.schedule(
                            () -> hetGio(room), Math.max(1, remaining), TimeUnit.MILLISECONDS);
                }
            } catch (RuntimeException e) {
                System.err.println("Could not save timeout result: " + e.getMessage());
                room.clockTask = scheduler.schedule(() -> hetGio(room), 5, TimeUnit.SECONDS);
            }
        }
    }

    private boolean kiemTraHetGio(Room room) {
        if (room.mode == Mode.BOT || room.paused) return false;
        int slot = room.state.turn();
        if (room.remainingMs[slot] - thoiGianDaDung(room, slot, System.nanoTime()) > 0) return false;
        xuLyHetGio(room, slot);
        return true;
    }

    private void xuLyHetGio(Room room, int slot) {
        long now = System.nanoTime();
        int spent = thoiGianDaDung(room, slot, now);
        int[] nextRemaining = room.remainingMs.clone();
        int[] nextUsed = room.usedMs.clone();
        apDungThoiGian(nextRemaining, nextUsed, slot, spent);
        GameState nextState = room.state.copy();
        int nextMoveNo = room.moveNo + 1;
        int timeoutCount = room.timeoutCounts[slot] + 1;
        boolean lost = false;
        MatchDao.Completion completion = null;
        Move autoMove = null;
        MoveResult moveResult = null;
        List<Move> legalMoves = Rules.legalMoves(nextState);
        if (legalMoves.isEmpty()) {
            nextState.forfeit(slot);
            lost = true;
            completion = ketQua(room, nextState, "TIMEOUT", nextUsed);
        } else {
            autoMove = legalMoves.get(RANDOM.nextInt(legalMoves.size()));
            moveResult = Rules.play(nextState, autoMove);
            if (nextState.isOver()) completion = ketQua(room, nextState, "NORMAL", nextUsed);
            lost = nextState.isOver();
        }
        if (room.mode == Mode.RANKED) {
            matchDao.ghiNuoc(room.matchId, nextMoveNo, slot, autoMove == null ? "TIMEOUT" : "AUTO_MOVE",
                    autoMove == null ? null : autoMove.pit(), autoMove == null ? null : autoMove.dir(),
                    moveResult == null ? 0 : moveResult.gained(), spent, boardJson(nextState), completion);
        }
        room.state = nextState;
        room.remainingMs = nextRemaining;
        room.usedMs = nextUsed;
        room.moveNo = nextMoveNo;
        room.timeoutCounts[slot] = timeoutCount;
        if (lost) {
            room.completion = completion;
            ketThucTrongPhong(room, completion);
        } else {
            batDauDongHo(room);
        }
        phatTrangThai(room, "state", moveResult == null ? List.of() : moveResult.events());
    }

    private void ketThua(Room room, int loser, String reason) {
        dungDongHo(room);
        if (!room.paused) capNhatThoiGianHienTai(room);
        GameState nextState = room.state.copy();
        nextState.forfeit(loser);
        MatchDao.Completion completion = ketQua(room, nextState, reason, room.usedMs);
        if (room.mode == Mode.RANKED) matchDao.ketThucVan(room.matchId, completion);
        room.state = nextState;
        ketThucTrongPhong(room, completion);
        if (room.mode == Mode.RANKED && "DISCONNECT".equals(reason)) {
            room.exited[loser] = true;
            playerRooms.remove(room.playerIds[loser], room.code);
            emit(room.connections[1 - loser], "rematch_unavailable",
                    Map.of("username", room.playerNames[loser]));
        }
        phatTrangThai(room, "state");
    }

    private void capNhatThoiGianHienTai(Room room) {
        int slot = room.state.turn();
        int spent = thoiGianDaDung(room, slot, System.nanoTime());
        apDungThoiGian(room.remainingMs, room.usedMs, slot, spent);
        room.turnStartedNanos = System.nanoTime();
    }

    private int thoiGianDaDung(Room room, int slot, long now) {
        if (room.mode == Mode.BOT) return 0;
        long elapsed = TimeUnit.NANOSECONDS.toMillis(Math.max(0, now - room.turnStartedNanos));
        return (int) Math.min(room.remainingMs[slot], elapsed);
    }

    private void apDungThoiGian(int[] remaining, int[] used, int slot, int spent) {
        remaining[slot] = timeControlMs;
        used[slot] += spent;
    }

    private void nuocBot(Room room) {
        synchronized (room) {
            if (room.state == null || room.state.isOver() || room.paused
                    || room.mode != Mode.BOT || room.state.turn() != 1) return;
            List<Move> moves = Rules.legalMoves(room.state);
            if (moves.isEmpty()) {
                try {
                    ketThua(room, 1, "NORMAL");
                } catch (RuntimeException e) {
                    System.err.println("Could not complete bot match: " + e.getMessage());
                }
                return;
            }
            Move move = moves.get(RANDOM.nextInt(moves.size()));
            GameState nextState = room.state.copy();
            MoveResult result = Rules.play(nextState, move);
            int nextMoveNo = room.moveNo + 1;
            MatchDao.Completion completion = nextState.isOver()
                    ? ketQua(room, nextState, "NORMAL", room.usedMs) : null;
            room.state = nextState;
            room.moveNo = nextMoveNo;
            room.consecutivePasses = 0;
            if (nextState.isOver()) ketThucTrongPhong(room, completion);
            else batDauDongHo(room);
            phatTrangThai(room, "state", result.events());
        }
    }

    private MatchDao.Completion ketQua(Room room, GameState state, String reason, int[] used) {
        GameState.Outcome outcome = state.outcome();
        String result = switch (outcome) {
            case P0_WIN -> "P1_WIN";
            case P1_WIN -> "P2_WIN";
            case DRAW -> "DRAW";
        };
        int rating0 = 0;
        int rating1 = 0;
        if (room.mode == Mode.RANKED) {
            double expected0 = 1.0 / (1.0 + Math.pow(10.0, (room.ratings[1] - room.ratings[0]) / 400.0));
            double actual0 = outcome == GameState.Outcome.P0_WIN ? 1.0
                    : outcome == GameState.Outcome.DRAW ? 0.5 : 0.0;
            rating0 = (int) Math.round(ELO_K_FACTOR * (actual0 - expected0));
            rating1 = -rating0;
        }
        int points0 = room.mode == Mode.RANKED
                ? Ranking.thayDoiDiem(room.points[0], outcome, 0) : 0;
        int points1 = room.mode == Mode.RANKED
                ? Ranking.thayDoiDiem(room.points[1], outcome, 1) : 0;
        return new MatchDao.Completion(state.score(0), state.score(1), used[0], used[1],
                result, reason, rating0, rating1, points0, points1);
    }

    private void ketThucTrongPhong(Room room, MatchDao.Completion completion) {
        dungDongHo(room);
        room.paused = false;
        room.completion = completion;
        room.completedAtMillis = System.currentTimeMillis();
        if (room.mode == Mode.RANKED) {
            room.points[0] = Math.max(0, room.points[0] + completion.pointsDelta0());
            room.points[1] = Math.max(0, room.points[1] + completion.pointsDelta1());
            room.ratings[0] += completion.ratingDelta0();
            room.ratings[1] += completion.ratingDelta1();
        }
        for (int slot = 0; slot < 2; slot++) {
            if (room.disconnectTasks[slot] != null) room.disconnectTasks[slot].cancel(false);
            if (room.mode != Mode.RANKED && room.playerIds[slot] != 0) playerRooms.remove(room.playerIds[slot], room.code);
        }
        if (room.mode != Mode.RANKED) rooms.remove(room.code, room);
    }

    private int kiemTraThanhVien(Room room, Users user, ClientHandler client) {
        int slot = viTriNguoiChoi(room, user.getId());
        if (slot < 0) throw new IllegalArgumentException("Bạn không thuộc trận đấu này");
        if (room.connections[slot] != client) throw new IllegalStateException("Kết nối chưa được gắn với trận đấu");
        return slot;
    }

    private int kiemTraNguoiChoi(Room room, Users user, ClientHandler client) {
        int slot = viTriNguoiChoi(room, user.getId());
        if (slot < 0) throw new IllegalArgumentException("Bạn không thuộc trận đấu này");
        if (room.connections[slot] != client) {
            throw new IllegalStateException("Kết nối chưa được gắn với trận đấu; hãy gửi reconnect");
        }
        if (room.state == null || room.state.isOver()) {
            throw new IllegalStateException("Trận đấu chưa bắt đầu hoặc đã kết thúc");
        }
        return slot;
    }

    private int viTriNguoiChoi(Room room, long userId) {
        if (room.playerIds[0] == userId) return 0;
        if (room.playerIds[1] == userId) return 1;
        return -1;
    }

    private Room roomCuaNguoiChoi(Users user) {
        String code = playerRooms.get(user.getId());
        Room room = code == null ? null : rooms.get(code);
        if (room == null) throw new IllegalArgumentException("Bạn hiện không ở trong trận đấu");
        return room;
    }

    private void kiemTraChuaTrongTran(Users user) {
        if (playerRooms.containsKey(user.getId())) {
            throw new IllegalStateException("Bạn đang ở trong phòng hoặc trận đấu khác");
        }
        if (queuedPlayers.containsKey(user.getId())) {
            throw new IllegalStateException("Bạn đang ở hàng chờ ghép trận");
        }
    }

    private void kiemTraChuaTamDung(Room room) {
        if (room.paused) throw new IllegalStateException("Trận đấu đang tạm dừng để chờ kết nối lại");
    }

    private void phatTrangThai(Room room, String event) {
        phatTrangThai(room, event, List.of());
    }

    private void phatTrangThai(Room room, String event, List<MoveEvent> moveEvents) {
        if (room.state == null) return;
        int[] remaining = room.remainingMs.clone();
        if (!room.paused && !room.state.isOver()) {
            int slot = room.state.turn();
            remaining[slot] = Math.max(0, remaining[slot]
                    - thoiGianDaDung(room, slot, System.nanoTime()));
        }
        int[] dan = new int[GameState.PIT_COUNT];
        boolean[] quan = new boolean[GameState.PIT_COUNT];
        for (int pit = 0; pit < GameState.PIT_COUNT; pit++) {
            dan[pit] = room.state.danAt(pit);
            quan[pit] = room.state.hasQuan(pit);
        }
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("code", room.code);
        state.put("matchId", room.matchId);
        state.put("mode", room.mode.name());
        state.put("dan", dan);
        state.put("quan", quan);
        state.put("scores", List.of(room.state.score(0), room.state.score(1)));
        state.put("turn", room.state.turn());
        state.put("remainingMs", remaining);
        state.put("timeUsedMs", room.usedMs.clone());
        state.put("playerNames", List.of(room.playerNames[0],
                room.playerNames[1] == null ? "Máy" : room.playerNames[1]));
        state.put("passCounts", List.of(room.totalPasses[0], room.totalPasses[1]));
        state.put("rematchVotes", List.of(room.rematchVotes[0], room.rematchVotes[1]));
        state.put("rematchUnavailable", room.exited[0] || room.exited[1]);
        state.put("timeoutCounts", List.of(room.timeoutCounts[0], room.timeoutCounts[1]));
        state.put("consecutivePasses", room.consecutivePasses);
        state.put("gameOver", room.state.isOver());
        state.put("moveEvents", moveEvents);
        if (room.state.isOver()) {
            state.put("result", room.state.outcome().name());
            state.put("endReason", room.completion.reason());
            state.put("ratingChange", List.of(
                    room.completion.ratingDelta0(), room.completion.ratingDelta1()));
        }
        for (ClientHandler connection : room.connections) {
            emit(connection, event, state);
            if (room.state.isOver()) {
                emit(connection, "match_ended", Map.of(
                        "matchId", room.matchId,
                        "mode", room.mode.name(),
                        "result", room.completion.result(),
                        "reason", room.completion.reason(),
                        "ratingChange", List.of(room.completion.ratingDelta0(),
                                room.completion.ratingDelta1())));
            }
        }
    }

    private String boardJson(GameState state) {
        int[] dan = new int[GameState.PIT_COUNT];
        boolean[] quan = new boolean[GameState.PIT_COUNT];
        for (int pit = 0; pit < GameState.PIT_COUNT; pit++) {
            dan[pit] = state.danAt(pit);
            quan[pit] = state.hasQuan(pit);
        }
        Map<String, Object> board = Map.of("dan", dan, "quan", quan,
                "scores", List.of(state.score(0), state.score(1)),
                "turn", state.turn(), "gameOver", state.isOver());
        try {
            return json.writeValueAsString(board);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không thể mã hóa trạng thái bàn cờ", e);
        }
    }

    private void emit(ClientHandler client, String event, Object data) {
        if (client == null) return;
        try {
            client.sendEvent(event, data);
        } catch (IllegalStateException e) {
            System.err.println("Could not notify TCP client: " + e.getMessage());
        }
    }

    private String taoMaPhong() {
        StringBuilder code = new StringBuilder(6);
        do {
            code.setLength(0);
            for (int i = 0; i < 6; i++) {
                code.append(ROOM_ALPHABET.charAt(RANDOM.nextInt(ROOM_ALPHABET.length())));
            }
        } while (rooms.containsKey(code.toString()));
        return code.toString();
    }

    private String chuanHoaMa(String code) {
        String normalized = code.strip().toUpperCase(Locale.ROOT);
        if (normalized.length() != 6) throw new IllegalArgumentException("Mã phòng phải có 6 ký tự");
        return normalized;
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }

    private enum Mode { BOT, FRIENDS, RANKED }

    private record QueueEntry(Users user, ClientHandler client) { }

    private static final class Room {
        private final String code;
        private final Mode mode;
        private final int timeControlMs;
        private final long createdAtMillis = System.currentTimeMillis();
        private final long[] playerIds = new long[2];
        private final String[] playerNames = new String[2];
        private final int[] ratings = new int[2];
        private final int[] points = new int[2];
        private final ClientHandler[] connections = new ClientHandler[2];
        private int[] remainingMs = new int[2];
        private int[] usedMs = new int[2];
        private final int[] totalPasses = new int[2];
        private final int[] timeoutCounts = new int[2];
        private final boolean[] disconnected = new boolean[2];
        private final boolean[] rematchVotes = new boolean[2];
        private final boolean[] exited = new boolean[2];
        private final ScheduledFuture<?>[] disconnectTasks = new ScheduledFuture<?>[2];
        private GameState state;
        private long matchId;
        private int moveNo;
        private int consecutivePasses;
        private long turnStartedNanos;
        private boolean paused;
        private MatchDao.Completion completion;
        private long completedAtMillis;
        private ScheduledFuture<?> clockTask;
        private ScheduledFuture<?> roomExpiryTask;

        private Room(String code, Mode mode, int timeControlMs) {
            this.code = code;
            this.mode = mode;
            this.timeControlMs = timeControlMs;
        }
    }
}
