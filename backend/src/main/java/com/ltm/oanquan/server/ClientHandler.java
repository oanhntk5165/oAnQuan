package com.ltm.oanquan.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ltm.oanquan.common.BusinessException;
import com.ltm.oanquan.dao.DaoException;
import com.ltm.oanquan.model.Users;
import com.ltm.oanquan.model.Ranking;

import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ClientHandler:
 * - Nhận request JSON từ TCP/WebSocket.
 * - Phân loại action: register, login, move, chat, match, ...
 * - Xác thực người dùng và trả JSON response về client.
 *
 * Đây là lớp trung tâm để nối transport (TCP / WebSocket) với GameService và DAO.
 */
final class ClientHandler {

    /** Limit a single JSON message so clients cannot submit oversized requests. */
    static final int MAX_REQUEST_LENGTH = 16_384;

    private final ObjectMapper json;
    private final TcpGameServer server;
    private final ClientTransport transport;
    private final AtomicBoolean disconnected = new AtomicBoolean();
    private volatile Users currentUser;

    ClientHandler(ObjectMapper json, TcpGameServer server, ClientTransport transport) {
        this.json = json;
        this.server = server;
        this.transport = transport;
        server.registerClient(this);
    }

    /** Process one complete JSON message; returns false when its size exceeds the limit. */
    synchronized boolean handleMessage(String message) {
        if (message.length() > MAX_REQUEST_LENGTH) {
            sendError("Yêu cầu vượt quá giới hạn kích thước");
            return false;
        }
        try {
            JsonNode parsed = json.readTree(message);
            if (!(parsed instanceof ObjectNode request)) {
                sendError("Yêu cầu phải là một JSON object");
                return true;
            }
            String action = request.path("action").asText("");
            Object data = dispatch(action, request);
            send(Map.of("type", "ok", "data", data == null ? Map.of() : data));
        } catch (BusinessException | IllegalArgumentException | IllegalStateException e) {
            sendError(e.getMessage());
        } catch (DaoException e) {
            logDatabaseFailure(e);
            sendError("Không thể hoàn tất yêu cầu do lỗi cơ sở dữ liệu");
        } catch (IOException e) {
            sendError("JSON không hợp lệ");
        } catch (RuntimeException e) {
            System.err.println("Client request failed: " + e.getMessage());
            sendError("Lỗi máy chủ");
        }
        return true;
    }

    private void logDatabaseFailure(DaoException error) {
        Throwable cause = error.getCause();
        if (cause instanceof SQLException sqlError) {
            System.err.printf("Database request failed: %s (SQLState=%s, errorCode=%d): %s%n",
                    error.getMessage(), sqlError.getSQLState(), sqlError.getErrorCode(),
                    sqlError.getMessage());
        } else if (cause != null) {
            System.err.printf("Database request failed: %s (cause=%s): %s%n",
                    error.getMessage(), cause.getClass().getSimpleName(), cause.getMessage());
        } else {
            System.err.println("Database request failed: " + error.getMessage());
        }
    }

    void disconnect() {
        if (!disconnected.compareAndSet(false, true)) return;
        try {
            server.gameService().ngatKetNoi(this);
        } catch (RuntimeException e) {
            System.err.println("Could not process client disconnect: " + e.getMessage());
        } finally {
            server.unregisterClient(this);
        }
    }

    private Object dispatch(String action, ObjectNode request) throws BusinessException {
        return switch (action) {
            case "register" -> dangKy(request);
            case "login" -> dangNhap(request);
            case "profile" -> hoSo(nguoiDungMoi(request));
            case "leaderboard" -> bangRanking(request);
            case "history" -> lichSu(request);
            case "match_stats" -> thongKe(request);
            case "match_detail" -> chiTiet(request);
            case "list_rooms" -> {
                nguoiDung(request);
                yield server.gameService().phongDangCho();
            }
            case "online_count" -> Map.of("count", server.onlineUserCount());
            case "logout" -> dangXuat();
            case "create_room" -> server.gameService().taoPhong(nguoiDung(request), this);
            case "join_room" -> server.gameService().thamGiaPhong(
                    nguoiDung(request), this, batBuocChuoi(request, "code"));
            case "start_room" -> server.gameService().batDauPhong(
                    nguoiDung(request), this, batBuocChuoi(request, "code"));
            case "create_bot" -> server.gameService().taoTranBot(
                    nguoiDung(request), this, tuyChonBoolean(request, "goFirst", true));
            case "pause_bot" -> server.gameService().tamDungTranBot(nguoiDung(request), this);
            case "resume_bot" -> server.gameService().tiepTucTranBot(nguoiDung(request), this);
            case "find_match" -> server.gameService().timTran(
                    nguoiDungMoi(request), this);
            case "cancel_matchmaking" -> server.gameService().huyTimTran(nguoiDung(request));
            case "move" -> server.gameService().diChuyen(
                    nguoiDung(request), this,
                    batBuocSoNguyen(request, "position"), batBuocSoNguyen(request, "direction"));
            case "pass" -> server.gameService().boLuot(nguoiDung(request), this);
            case "resign" -> server.gameService().dauHang(nguoiDung(request), this);
            case "rematch" -> server.gameService().taiDau(nguoiDung(request), this);
            case "exit_match" -> server.gameService().thoatTran(nguoiDung(request), this);
            case "chat" -> server.gameService().chat(
                    nguoiDung(request), this, batBuocChuoi(request, "content"));
            case "community_send" -> server.communityChat().send(
                    nguoiDung(request), batBuocChuoi(request, "content"));
            case "community_history" -> {
                nguoiDung(request);
                long before = request.hasNonNull("before")
                        ? Long.parseLong(request.path("before").asText()) : Long.MAX_VALUE;
                yield server.communityChat().history(before);
            }
            case "state" -> server.gameService().trangThai(
                    nguoiDung(request), this, batBuocChuoi(request, "code"));
            case "reconnect" -> server.gameService().ketNoiLai(
                    nguoiDung(request), this, request.path("code").asText(""));
            case "cancel_room" -> server.gameService().huyPhong(
                    nguoiDung(request), batBuocChuoi(request, "code"));
            default -> throw new IllegalArgumentException("Hành động không được hỗ trợ");
        };
    }

    private Object dangKy(ObjectNode request) throws BusinessException {
        Users user = server.taiKhoan().dangKy(
                batBuocChuoi(request, "username"), batBuocChuoi(request, "password"));
        return hoSo(user);
    }

    private Object dangNhap(ObjectNode request) throws BusinessException {
        Users user = server.taiKhoan().dangNhap(
                batBuocChuoi(request, "username"), batBuocChuoi(request, "password"));
        currentUser = user;
        String token = server.sessions().tao(user);
        Map<String, Object> response = new LinkedHashMap<>(hoSo(user));
        response.put("token", token);
        return response;
    }

    private Object dangXuat() {
        server.gameService().ngatKetNoi(this);
        currentUser = null;
        return Map.of("status", "SIGNED_OUT");
    }

    private Map<String, Object> hoSo(Users user) {
        return Map.of("userId", user.getId(), "username", user.getTenDangNhap(),
                "points", user.getDiem(), "rating", user.getRating(),
                "level", Ranking.capDo(user.getDiem()),
                "tier", Ranking.tenBac(Ranking.bac(user.getDiem())));
    }

    private Object bangRanking(ObjectNode request) {
        int limit = request.path("limit").asInt(20);
        List<Map<String, Object>> players = server.userDao().bangRanking(limit).stream()
                .map(user -> Map.<String, Object>of(
                        "username", user.getTenDangNhap(),
                        "points", user.getDiem(),
                        "rating", user.getRating(),
                        "level", Ranking.capDo(user.getDiem()),
                        "tier", Ranking.tenBac(Ranking.bac(user.getDiem()))))
                .toList();
        return Map.of("players", players);
    }

    private Object lichSu(ObjectNode request) throws BusinessException {
        Users user = nguoiDung(request);
        int limit = request.path("limit").asInt(50);
        return Map.of("matches", server.matchDao().lichSu(user.getId(), limit));
    }

    private Object thongKe(ObjectNode request) throws BusinessException {
        Users user = nguoiDung(request);
        return Map.of("modes", server.matchDao().thongKe(user.getId()));
    }

    private Object chiTiet(ObjectNode request) throws BusinessException {
        Users user = nguoiDung(request);
        long matchId = request.path("matchId").asLong(-1);
        if (matchId < 1) throw new IllegalArgumentException("matchId không hợp lệ");
        Map<String, Object> detail = server.matchDao().chiTiet(matchId, user.getId());
        if (detail == null) throw new IllegalArgumentException("Không tìm thấy trận đấu");
        return detail;
    }

    private Users nguoiDung(ObjectNode request) throws BusinessException {
        if (request.hasNonNull("token")) {
            Users user = server.sessions().tim(request.path("token").asText());
            if (user == null) throw new BusinessException("Phiên đăng nhập không hợp lệ hoặc đã hết hạn");
            currentUser = user;
            return user;
        }
        if (currentUser == null) throw new BusinessException("Vui lòng đăng nhập");
        return currentUser;
    }

    private Users nguoiDungMoi(ObjectNode request) throws BusinessException {
        Users authenticated = nguoiDung(request);
        Users current = server.userDao().timTheoId(authenticated.getId());
        if (current == null) throw new BusinessException("Tài khoản không còn tồn tại");
        return current;
    }

    private String batBuocChuoi(ObjectNode request, String field) {
        JsonNode value = request.get(field);
        if (value == null || !value.isTextual() || value.asText().isEmpty()) {
            throw new IllegalArgumentException("Thiếu trường " + field);
        }
        return value.asText();
    }

    private int batBuocSoNguyen(ObjectNode request, String field) {
        JsonNode value = request.get(field);
        if (value == null || !value.canConvertToInt()) {
            throw new IllegalArgumentException("Trường " + field + " phải là số nguyên");
        }
        return value.asInt();
    }

    private boolean tuyChonBoolean(ObjectNode request, String field, boolean defaultValue) {
        JsonNode value = request.get(field);
        if (value == null) return defaultValue;
        if (!value.isBoolean()) {
            throw new IllegalArgumentException("Trường " + field + " phải là boolean");
        }
        return value.booleanValue();
    }

    synchronized void sendEvent(String event, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("type", "event");
        response.put("event", event);
        response.put("data", data);
        send(response);
    }

    synchronized void sendError(String message) {
        send(Map.of("type", "error", "message", message == null ? "Yêu cầu không hợp lệ" : message));
    }

    private synchronized void send(Object message) {
        if (!transport.isOpen()) return;
        try {
            transport.sendText(json.writeValueAsString(message));
        } catch (IOException e) {
            throw new IllegalStateException("Không gửi được dữ liệu tới client", e);
        }
    }

    Users currentUser() {
        return currentUser;
    }

}
