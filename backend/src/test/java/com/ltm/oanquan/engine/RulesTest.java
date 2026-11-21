package com.ltm.oanquan.engine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class RulesTest {

    /** Bàn trống có đủ 2 quan, lượt người 0. */
    private static GameState base() {
        return GameState.empty().withQuan(0, true).withQuan(6, true);
    }

    private static boolean has(MoveResult r, MoveEvent.Type t) {
        return r.events().stream().anyMatch(e -> e.type() == t);
    }

    @Test
    void initialBoard() {
        GameState s = GameState.initial();
        assertEquals(70, s.totalValue());
        assertEquals(5, s.danAt(1));
        assertEquals(5, s.danAt(11));
        assertTrue(s.hasQuan(0));
        assertTrue(s.hasQuan(6));
        assertEquals(10, Rules.legalMoves(s).size());
    }

    @Test
    void initialBoardCanStartWithPlayerOne() {
        GameState s = GameState.initial(1);
        assertEquals(1, s.turn());
        assertEquals(70, s.totalValue());
        assertThrows(IllegalArgumentException.class, () -> GameState.initial(2));
    }

    @Test
    void sowThenStopOnTwoEmptyPits() {
        GameState s = base().withDan(1, 2).withDan(7, 5);
        MoveResult r = Rules.play(s, new Move(1, 1));
        assertEquals(0, r.gained());
        assertEquals(0, s.danAt(1));
        assertEquals(1, s.danAt(2));
        assertEquals(1, s.danAt(3));
        assertEquals(1, s.turn());
        assertFalse(s.isOver());
    }

    @Test
    void captureAfterEmptyPit() {
        GameState s = base().withDan(1, 1).withDan(4, 4).withDan(5, 1).withDan(7, 3);
        MoveResult r = Rules.play(s, new Move(1, 1));
        assertEquals(4, r.gained());
        assertEquals(4, s.score(0));
        assertEquals(0, s.danAt(4));
        assertEquals(1, s.danAt(2));
        assertEquals(1, s.danAt(5));
        assertTrue(has(r, MoveEvent.Type.CAPTURE));
    }

    @Test
    void relaySowing() {
        GameState s = base().withDan(1, 1).withDan(3, 2).withDan(7, 3);
        MoveResult r = Rules.play(s, new Move(1, 1));
        assertEquals(0, r.gained());
        assertEquals(1, s.danAt(2));
        assertEquals(0, s.danAt(3));
        assertEquals(1, s.danAt(4));
        assertEquals(1, s.danAt(5));
        assertTrue(s.hasQuan(6));          // dừng vì gặp ô quan có quân
    }

    @Test
    void captureQuanPit() {
        GameState s = base().withDan(3, 1).withDan(7, 3);
        MoveResult r = Rules.play(s, new Move(3, 1));
        assertEquals(10, r.gained());
        assertFalse(s.hasQuan(6));
        assertFalse(s.isOver());           // ô quan 0 vẫn còn quan
    }

    @Test
    void refillWhenAllOwnPitsEmpty() {
        GameState s = base().withDan(1, 1).withScore(1, 7);
        MoveResult r = Rules.play(s, new Move(1, 1));
        assertTrue(has(r, MoveEvent.Type.REFILL));
        assertEquals(2, s.score(1));
        for (int i = 7; i <= 11; i++) assertEquals(1, s.danAt(i));
    }

    @Test
    void loseWhenCannotRefill() {
        GameState s = base().withDan(1, 1).withScore(1, 3);
        MoveResult r = Rules.play(s, new Move(1, 1));
        assertTrue(r.gameOver());
        assertEquals(GameState.Outcome.P0_WIN, s.outcome());
    }

    @Test
    void gameEndsWhenBothQuanPitsEmpty() {
        GameState s = GameState.empty().withQuan(6, true)
                .withDan(2, 2).withDan(3, 1).withDan(8, 3);
        MoveResult r = Rules.play(s, new Move(3, 1));
        assertTrue(r.gameOver());
        assertEquals(13, s.score(0));      // 10 (quan) + 3 dân còn lại bên mình
        assertEquals(3, s.score(1));
        assertEquals(GameState.Outcome.P0_WIN, s.outcome());
    }

    @Test
    void sowingWrapsAroundBoard() {
        GameState s = base().withTurn(1).withDan(11, 2);
        Rules.play(s, new Move(11, 1));
        assertEquals(1, s.danAt(0));
        assertEquals(1, s.danAt(1));
        assertTrue(s.hasQuan(0));
    }

    @Test
    void sowingInNegativeDirection() {
        GameState s = base().withTurn(1).withDan(7, 3);
        Rules.play(s, new Move(7, -1));
        assertEquals(1, s.danAt(6));
        assertEquals(1, s.danAt(5));
        assertEquals(1, s.danAt(4));
        assertTrue(s.hasQuan(6));
    }

    @Test
    void invalidMovesAreRejected() {
        GameState s = GameState.initial();
        assertThrows(IllegalArgumentException.class, () -> Rules.play(s, new Move(7, 1)));
        assertThrows(IllegalArgumentException.class, () -> Rules.play(s, new Move(0, 1)));
        assertThrows(IllegalArgumentException.class, () -> Rules.play(s, new Move(1, 0)));
        GameState e = base().withDan(2, 1);
        assertThrows(IllegalArgumentException.class, () -> Rules.play(e, new Move(1, 1)));
    }

    @Test
    void passChangesTurnAndForfeitEndsWithoutChangingBoard() {
        GameState s = GameState.initial();
        int total = s.totalValue();
        s.passTurn();
        assertEquals(1, s.turn());
        s.forfeit(1);
        assertTrue(s.isOver());
        assertEquals(GameState.Outcome.P0_WIN, s.outcome());
        assertEquals(total, s.totalValue());
        assertThrows(IllegalStateException.class, s::passTurn);
    }

    @Test
    void equalScoresProduceDrawOutcome() {
        GameState s = base().withScore(0, 10).withScore(1, 10);
        s.finish(-1);
        assertTrue(s.isOver());
        assertEquals(GameState.Outcome.DRAW, s.outcome());
    }

    /** Chơi ngẫu nhiên nhiều ván: tổng giá trị luôn là 70 và không phát sinh lỗi. */
    @Test
    void randomGamesConserveTotalValue() {
        Random rnd = new Random(42);
        for (int g = 0; g < 200; g++) {
            GameState s = GameState.initial();
            int moves = 0;
            while (!s.isOver() && moves < 500) {
                List<Move> legal = Rules.legalMoves(s);
                assertFalse(legal.isEmpty());
                Rules.play(s, legal.get(rnd.nextInt(legal.size())));
                assertEquals(70, s.totalValue());
                moves++;
            }
        }
    }
}