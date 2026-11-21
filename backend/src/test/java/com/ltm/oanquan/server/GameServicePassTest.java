package com.ltm.oanquan.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ltm.oanquan.dao.MatchDao;
import com.ltm.oanquan.engine.GameState;
import com.ltm.oanquan.model.Users;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameServicePassTest {

    @Test
    void moveAnimationPausesTheNextPlayersClock() throws Exception {
        ObjectMapper json = new ObjectMapper();
        MatchDao matchDao = mock(MatchDao.class);
        TcpGameServer clientRegistry = new TcpGameServer(json, 20_000, 45_000);
        GameService game = new GameService(matchDao, json, 20_000, 45_000);
        ClientHandler player0 = client(json, clientRegistry);
        ClientHandler player1 = client(json, clientRegistry);
        Users user0 = new Users(1L, "player0", null, 0, 1000);
        Users user1 = new Users(2L, "player1", null, 0, 1000);

        try {
            game.timTran(user0, player0);
            Map<String, Object> matched = game.timTran(user1, player1);
            String code = (String) matched.get("code");
            Object room = roomFor(game, code);
            setState(room, GameState.initial(1));

            Method startClock = GameService.class.getDeclaredMethod(
                    "batDauDongHo", room.getClass(), int.class);
            startClock.setAccessible(true);
            startClock.invoke(game, room, 8);

            Field pausedField = room.getClass().getDeclaredField("turnClockPaused");
            pausedField.setAccessible(true);
            assertTrue(pausedField.getBoolean(room));

            Method elapsed = GameService.class.getDeclaredMethod(
                    "thoiGianDaDung", room.getClass(), int.class, long.class);
            elapsed.setAccessible(true);
            assertEquals(0, ((Integer) elapsed.invoke(game, room, 1,
                    System.nanoTime())).intValue());
        } finally {
            game.close();
            clientRegistry.close();
        }
    }

    @Test
    void timeoutAutoMoveDoesNotCountAsAPlayerPass() throws Exception {
        ObjectMapper json = new ObjectMapper();
        MatchDao matchDao = mock(MatchDao.class);
        when(matchDao.taoVan(1L, 2L, "RANKED", 20_000)).thenReturn(42L);
        TcpGameServer clientRegistry = new TcpGameServer(json, 20_000, 45_000);
        GameService game = new GameService(matchDao, json, 20_000, 45_000);
        ClientHandler player0 = client(json, clientRegistry);
        ClientHandler player1 = client(json, clientRegistry);
        Users user0 = new Users(1L, "player0", null, 0, 1000);
        Users user1 = new Users(2L, "player1", null, 0, 1000);

        try {
            game.timTran(user0, player0);
            Map<String, Object> matched = game.timTran(user1, player1);
            String code = (String) matched.get("code");

            assertEquals("PASSED", game.boLuot(user0, player0).get("status"));
            Object room = roomFor(game, code);
            setState(room, GameState.initial(1));
            game.diChuyen(user1, player1, 7, 1);
            setState(room, GameState.initial(0));
            processTimeout(game, room, 0);

            verify(matchDao).ghiNuoc(eq(42L), eq(3), eq(0), eq("AUTO_MOVE"),
                    anyInt(), anyInt(), anyInt(), anyInt(), anyString(), isNull());

            setState(room, GameState.initial(0));
            assertEquals("FINISHED", game.boLuot(user0, player0).get("status"));
            ArgumentCaptor<MatchDao.Completion> completion =
                    ArgumentCaptor.forClass(MatchDao.Completion.class);
            verify(matchDao).ghiNuoc(eq(42L), eq(4), eq(0), eq("PASS"),
                    isNull(), isNull(), eq(0), anyInt(), anyString(), completion.capture());
            assertEquals("PASS_LIMIT", completion.getValue().reason());
            assertEquals(0, completion.getValue().pointsDelta0());
            assertEquals(4, completion.getValue().pointsDelta1());
        } finally {
            game.close();
            clientRegistry.close();
        }
    }

    @Test
    void rankedAndPrivateRoomResultsUseRankPointsButBotDoesNotPersist() throws Exception {
        ObjectMapper json = new ObjectMapper();
        MatchDao matchDao = mock(MatchDao.class);
        when(matchDao.taoVan(1L, 2L, "RANKED", 20_000)).thenReturn(42L, 43L, 44L);
        TcpGameServer clientRegistry = new TcpGameServer(json, 20_000, 45_000);
        GameService game = new GameService(matchDao, json, 20_000, 45_000);
        ClientHandler player0 = client(json, clientRegistry);
        ClientHandler player1 = client(json, clientRegistry);
        Users user0 = new Users(1L, "player0", null, 2, 1000);
        Users user1 = new Users(2L, "player1", null, 99, 1000);

        try {
            game.timTran(user0, player0);
            Map<String, Object> matched = game.timTran(user1, player1);
            String code = (String) matched.get("code");
            Object room = roomFor(game, code);
            setState(room, GameState.initial(0));
            game.dauHang(user0, player0);

            ArgumentCaptor<MatchDao.Completion> completions =
                    ArgumentCaptor.forClass(MatchDao.Completion.class);
            assertEquals("WAITING", game.taiDau(user0, player0).get("status"));
            assertEquals("STARTED", game.taiDau(user1, player1).get("status"));
            setState(room, GameState.initial(1));
            game.dauHang(user1, player1);
            verify(matchDao, times(2)).ketThucVan(anyLong(), completions.capture());
            assertEquals(-1, completions.getAllValues().get(0).pointsDelta0());
            assertEquals(4, completions.getAllValues().get(0).pointsDelta1());
            assertEquals(4, completions.getAllValues().get(1).pointsDelta0());
            assertEquals(-2, completions.getAllValues().get(1).pointsDelta1());
            game.thoatTran(user0, player0);
            game.thoatTran(user1, player1);

            game.taoTranBot(user0, player0, true);
            game.dauHang(user0, player0);
            String roomCode = (String) game.taoPhong(user0, player0).get("code");
            game.thamGiaPhong(user1, player1, roomCode);
            game.batDauPhong(user0, player0, roomCode);
            Object privateRoom = roomFor(game, roomCode);
            setState(privateRoom, GameState.initial(0));
            game.dauHang(user0, player0);

            verify(matchDao, never()).taoVan(eq(1L), isNull(), eq("BOT"), anyInt());
            verify(matchDao, times(3)).taoVan(eq(1L), eq(2L), eq("RANKED"), anyInt());
            ArgumentCaptor<MatchDao.Completion> privateRoomCompletion =
                    ArgumentCaptor.forClass(MatchDao.Completion.class);
            verify(matchDao).ketThucVan(eq(44L), privateRoomCompletion.capture());
            assertEquals(-1, privateRoomCompletion.getValue().pointsDelta0());
            assertEquals(4, privateRoomCompletion.getValue().pointsDelta1());
            verify(matchDao, never()).ghiNuoc(anyLong(), anyInt(), anyInt(), anyString(),
                    isNull(), isNull(), anyInt(), anyInt(), anyString(), isNull());
        } finally {
            game.close();
            clientRegistry.close();
        }
    }

    @Test
    void disconnectGraceExpirationPersistsRankedForfeitAndNotifiesConnectedPlayer() throws Exception {
        ObjectMapper json = new ObjectMapper();
        MatchDao matchDao = mock(MatchDao.class);
        when(matchDao.taoVan(1L, 2L, "RANKED", 20_000)).thenReturn(42L);
        TcpGameServer clientRegistry = new TcpGameServer(json, 20_000, 45_000);
        GameService game = new GameService(matchDao, json, 20_000, 45_000);
        List<String> player1Messages = new ArrayList<>();
        ClientHandler player0 = client(json, clientRegistry);
        ClientHandler player1 = client(json, clientRegistry, player1Messages);
        Users user0 = new Users(1L, "player0", null, 2, 1000);
        Users user1 = new Users(2L, "player1", null, 99, 1000);

        try {
            game.timTran(user0, player0);
            Map<String, Object> matched = game.timTran(user1, player1);
            String code = (String) matched.get("code");
            setCurrentUser(player0, user0);
            game.ngatKetNoi(player0);

            Object room = roomFor(game, code);
            processDisconnectTimeout(game, room, 0);

            ArgumentCaptor<MatchDao.Completion> completion =
                    ArgumentCaptor.forClass(MatchDao.Completion.class);
            verify(matchDao).ketThucVan(eq(42L), completion.capture());
            assertEquals("P2_WIN", completion.getValue().result());
            assertEquals("DISCONNECT", completion.getValue().reason());
            assertEquals(-1, completion.getValue().pointsDelta0());
            assertEquals(4, completion.getValue().pointsDelta1());
            assertTrue(player1Messages.stream().anyMatch(message ->
                    message.contains("\"event\":\"match_ended\"")
                            && message.contains("\"reason\":\"DISCONNECT\"")));

            List<String> player0Messages = new ArrayList<>();
            ClientHandler reconnectedPlayer0 = client(json, clientRegistry, player0Messages);
            setCurrentUser(reconnectedPlayer0, user0);
            assertEquals("MATCH_FINISHED",
                    game.ketNoiLai(user0, reconnectedPlayer0, "").get("status"));
            assertTrue(player0Messages.stream().anyMatch(message ->
                    message.contains("\"event\":\"state\"")
                            && message.contains("\"gameOver\":true")
                            && message.contains("\"endReason\":\"DISCONNECT\"")));
            game.thoatTran(user0, reconnectedPlayer0);
        } finally {
            game.close();
            clientRegistry.close();
        }
    }

    @Test
    void reconnectByAccountResumesActiveRankedMatchWithoutRoomCode() throws Exception {
        ObjectMapper json = new ObjectMapper();
        MatchDao matchDao = mock(MatchDao.class);
        when(matchDao.taoVan(1L, 2L, "RANKED", 20_000)).thenReturn(42L);
        TcpGameServer clientRegistry = new TcpGameServer(json, 20_000, 45_000);
        GameService game = new GameService(matchDao, json, 20_000, 45_000);
        ClientHandler player0 = client(json, clientRegistry);
        ClientHandler player1 = client(json, clientRegistry);
        Users user0 = new Users(1L, "player0", null, 2, 1000);
        Users user1 = new Users(2L, "player1", null, 99, 1000);

        try {
            game.timTran(user0, player0);
            Map<String, Object> matched = game.timTran(user1, player1);
            String code = (String) matched.get("code");
            Object room = roomFor(game, code);
            setCurrentUser(player0, user0);
            game.ngatKetNoi(player0);

            List<String> player0Messages = new ArrayList<>();
            ClientHandler reconnectedPlayer0 = client(json, clientRegistry, player0Messages);
            setCurrentUser(reconnectedPlayer0, user0);
            assertEquals("RECONNECTED",
                    game.ketNoiLai(user0, reconnectedPlayer0, "").get("status"));
            processDisconnectTimeout(game, room, 0);

            verify(matchDao, never()).ketThucVan(anyLong(), any());
            assertTrue(player0Messages.stream().anyMatch(message ->
                    message.contains("\"event\":\"state\"")
                            && message.contains("\"gameOver\":false")));
        } finally {
            game.close();
            clientRegistry.close();
        }
    }

    private ClientHandler client(ObjectMapper json, TcpGameServer registry) {
        return client(json, registry, new ArrayList<>());
    }

    private ClientHandler client(ObjectMapper json, TcpGameServer registry, List<String> messages) {
        return new ClientHandler(json, registry, new ClientTransport() {
            @Override
            public boolean isOpen() {
                return true;
            }

            @Override
            public void sendText(String message) throws IOException {
                messages.add(message);
            }
        });
    }

    private void setCurrentUser(ClientHandler client, Users user) throws Exception {
        Field currentUser = ClientHandler.class.getDeclaredField("currentUser");
        currentUser.setAccessible(true);
        currentUser.set(client, user);
    }

    private Object roomFor(GameService game, String code) throws Exception {
        Field roomsField = GameService.class.getDeclaredField("rooms");
        roomsField.setAccessible(true);
        return ((Map<?, ?>) roomsField.get(game)).get(code);
    }

    private void setState(Object room, GameState state) throws Exception {
        Field stateField = room.getClass().getDeclaredField("state");
        stateField.setAccessible(true);
        stateField.set(room, state);
        Field turnClockPausedField = room.getClass().getDeclaredField("turnClockPaused");
        turnClockPausedField.setAccessible(true);
        turnClockPausedField.setBoolean(room, false);
    }

    private void processTimeout(GameService game, Object room, int slot) throws Exception {
        Method timeout = GameService.class.getDeclaredMethod(
                "xuLyHetGio", room.getClass(), int.class);
        timeout.setAccessible(true);
        timeout.invoke(game, room, slot);
    }

    private void processDisconnectTimeout(GameService game, Object room, int slot) throws Exception {
        Method timeout = GameService.class.getDeclaredMethod(
                "hetHanKetNoiLai", room.getClass(), int.class);
        timeout.setAccessible(true);
        timeout.invoke(game, room, slot);
    }
}
