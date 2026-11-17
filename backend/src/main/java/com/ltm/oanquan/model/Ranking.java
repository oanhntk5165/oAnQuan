package com.ltm.oanquan.model;

import com.ltm.oanquan.engine.GameState;

/** Quy đổi điểm tích lũy thành cấp độ và bậc xếp hạng. */
public final class Ranking {

    public static final int DIEM_MOI_CAP = 10;
    public static final int DIEM_MOI_BAC = 100;

    private static final String[] TEN_BAC =
            {"Đồng", "Bạc", "Vàng", "Bạch kim", "Kim cương", "Cao thủ"};
    private static final int[] DIEM_THANG = {4, 4, 3, 3, 2, 2};
    private static final int[] DIEM_HOA = {1, 1, 0, 0, 0, -1};
    private static final int[] DIEM_THUA = {-1, -2, -2, -3, -3, -4};

    private Ranking() { }

    /** Cấp chạy từ 1 đến 10 trong mỗi bậc; Cao thủ giữ cấp 10 khi vượt 600 điểm. */
    public static int capDo(int points) {
        int normalized = Math.max(0, points);
        if (normalized >= DIEM_MOI_BAC * TEN_BAC.length) return 10;
        return (normalized % DIEM_MOI_BAC) / DIEM_MOI_CAP + 1;
    }

    /** Bậc bắt đầu từ 0 và được xác định bởi tổng điểm rank. */
    public static int bac(int points) {
        return Math.min(Math.max(0, points) / DIEM_MOI_BAC, TEN_BAC.length - 1);
    }

    public static String tenBac(int bac) {
        return TEN_BAC[Math.max(0, Math.min(bac, TEN_BAC.length - 1))];
    }

    /** Điểm thay đổi theo bậc trước trận; người chơi không thể bị trừ xuống dưới 0. */
    public static int thayDoiDiem(int points, GameState.Outcome outcome, int player) {
        if (player < 0 || player > 1) {
            throw new IllegalArgumentException("Người chơi không hợp lệ");
        }
        int tier = bac(points);
        int delta;
        if (outcome == GameState.Outcome.DRAW) {
            delta = DIEM_HOA[tier];
        } else {
            boolean won = player == 0
                    ? outcome == GameState.Outcome.P0_WIN
                    : outcome == GameState.Outcome.P1_WIN;
            delta = won ? DIEM_THANG[tier] : DIEM_THUA[tier];
        }
        return Math.max(-Math.max(0, points), delta);
    }
}
