package com.ltm.oanquan.model;

import com.ltm.oanquan.engine.GameState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RankingTest {

    @Test
    void capDoVaBacTheoDiemRank() {
        assertEquals(1, Ranking.capDo(0));
        assertEquals(10, Ranking.capDo(99));
        assertEquals(1, Ranking.capDo(100));
        assertEquals(2, Ranking.capDo(110));
        assertEquals(0, Ranking.bac(0));
        assertEquals(0, Ranking.bac(99));
        assertEquals(1, Ranking.bac(100));
        assertEquals(5, Ranking.bac(500));
        assertEquals("Bạc", Ranking.tenBac(1));
        assertEquals("Cao thủ", Ranking.tenBac(99));
        assertEquals(10, Ranking.capDo(600));
    }

    @Test
    void diemThayDoiTheoBacVaKhongXuongDuoiKhong() {
        int[] points = {1, 100, 200, 300, 400, 500};
        int[] wins = {4, 4, 3, 3, 2, 2};
        int[] draws = {1, 1, 0, 0, 0, -1};
        int[] losses = {-1, -2, -2, -3, -3, -4};
        for (int tier = 0; tier < points.length; tier++) {
            assertEquals(wins[tier], Ranking.thayDoiDiem(points[tier],
                    GameState.Outcome.P0_WIN, 0));
            assertEquals(draws[tier], Ranking.thayDoiDiem(points[tier],
                    GameState.Outcome.DRAW, 0));
            assertEquals(draws[tier], Ranking.thayDoiDiem(points[tier],
                    GameState.Outcome.DRAW, 1));
            assertEquals(losses[tier], Ranking.thayDoiDiem(points[tier],
                    GameState.Outcome.P1_WIN, 0));
        }
        assertEquals(0, Ranking.thayDoiDiem(0, GameState.Outcome.P1_WIN, 0));
        assertEquals(-1, Ranking.thayDoiDiem(1, GameState.Outcome.P1_WIN, 0));
        assertEquals(-1, Ranking.thayDoiDiem(501, GameState.Outcome.DRAW, 0));
        assertEquals(-4, Ranking.thayDoiDiem(501, GameState.Outcome.P1_WIN, 0));
    }
}