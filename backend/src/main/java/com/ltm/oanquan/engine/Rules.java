package com.ltm.oanquan.engine;

import java.util.ArrayList;
import java.util.List;

public final class Rules {

    /** Chốt an toàn chống vòng rải vô hạn (hầu như không bao giờ chạm tới). */
    private static final int MAX_STEPS = 1000;

    private Rules() { }

    /** Các nước đi hợp lệ của người đang đến lượt. */
    public static List<Move> legalMoves(GameState s) {
        List<Move> moves = new ArrayList<>();
        if (s.isOver()) return moves;
        int first = GameState.firstPitOf(s.turn());
        for (int pit = first; pit < first + 5; pit++) {
            if (s.danAt(pit) > 0) {
                moves.add(new Move(pit, 1));
                moves.add(new Move(pit, -1));
            }
        }
        return moves;
    }

    /** Thực hiện một nước đi, thay đổi trực tiếp `s`. Muốn thử nước đi thì gọi s.copy() trước. */
    public static MoveResult play(GameState s, Move move) {
        if (s.isOver()) throw new IllegalStateException("Ván đấu đã kết thúc");
        int player = s.turn();
        int dir = move.dir();
        if (dir != 1 && dir != -1)
            throw new IllegalArgumentException("Hướng phải là +1 hoặc -1");
        if (GameState.ownerOf(move.pit()) != player)
            throw new IllegalArgumentException("Chỉ được chọn ô dân của mình");
        if (s.danAt(move.pit()) == 0)
            throw new IllegalArgumentException("Ô được chọn không có quân");

        List<MoveEvent> events = new ArrayList<>();
        int gained = 0;
        int pos = move.pit();
        int hand = s.takeDan(pos);
        events.add(MoveEvent.pickup(pos, hand));

        int steps = 0;
        while (steps++ < MAX_STEPS) {
            // 1. Rải từng quân
            while (hand > 0) {
                pos = GameState.next(pos, dir);
                s.addDan(pos, 1);
                hand--;
                events.add(MoveEvent.sow(pos));
            }
            int nxt = GameState.next(pos, dir);

            // 2. Ô kế tiếp là ô dân có quân: bốc hết, rải tiếp
            if (!GameState.isQuanPit(nxt) && s.danAt(nxt) > 0) {
                pos = nxt;
                hand = s.takeDan(pos);
                events.add(MoveEvent.pickup(pos, hand));
                continue;
            }
            // 3. Ô kế tiếp là ô quan có quân: dừng lượt
            if (GameState.isQuanPit(nxt) && !s.isEmpty(nxt)) break;

            // 4. Ô kế tiếp trống: ăn ô có quân ngay sau nó, lặp lại theo cặp "trống - có quân".
            int cur = nxt;
            while (s.isEmpty(cur)) {
                int target = GameState.next(cur, dir);
                if (s.isEmpty(target)) break;           // hai ô trống liên tiếp: dừng
                int value = s.capture(target);
                gained += value;
                events.add(MoveEvent.capture(target, value));
                if (s.quanPitsCleared()) break;         // hết quan: ván kết thúc ngay
                cur = GameState.next(target, dir);
            }
            break;
        }
        // Chốt an toàn: nếu còn quân trên tay thì trả lại ô vừa bốc để không mất quân
        if (hand > 0) s.addDan(pos, hand);

        s.addScore(player, gained);

        if (s.quanPitsCleared()) {
            s.finish(-1);
        } else {
            s.setTurn(1 - player);
            prepareTurn(s, events);
        }
        return new MoveResult(events, gained, s.isOver());
    }

    /** Đầu lượt: nếu 5 ô dân của người đi đều trống thì bù dân, không đủ điểm thì thua. */
    private static void prepareTurn(GameState s, List<MoveEvent> events) {
        int p = s.turn();
        int first = GameState.firstPitOf(p);
        for (int i = first; i < first + 5; i++) {
            if (s.danAt(i) > 0) return;
        }
        if (s.score(p) >= GameState.REFILL_COST) {
            s.addScore(p, -GameState.REFILL_COST);
            for (int i = first; i < first + 5; i++) s.addDan(i, 1);
            events.add(MoveEvent.refill(p));
        } else {
            s.finish(p);
        }
    }
}