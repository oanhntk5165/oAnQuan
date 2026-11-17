package com.ltm.oanquan.engine;

/**
 * Bàn 12 ô xếp vòng tròn:
 *   0 = ô quan | 1..5 = ô dân của người 0 | 6 = ô quan | 7..11 = ô dân của người 1
 * Mỗi ô có số quân dân và cờ có quân quan (10 điểm).
 */
public final class GameState {

    public static final int PIT_COUNT = 12;
    public static final int DAN_PER_PIT = 5;
    public static final int QUAN_VALUE = 10;
    public static final int REFILL_COST = 5;

    public enum Outcome { P0_WIN, P1_WIN, DRAW }

    private final int[] dan = new int[PIT_COUNT];
    private final boolean[] quan = new boolean[PIT_COUNT];
    private final int[] scores = new int[2];
    private int turn;
    private boolean over;
    private int forfeitLoser = -1;   // -1: kết thúc bình thường

    private GameState() { }

    /** Bàn mới: mỗi ô dân 5 quân, mỗi ô quan 1 quân quan. Tổng 70 điểm. */
    public static GameState initial() {
        return initial(0);
    }

    /** Bàn mới với người chơi được chỉ định đi trước. */
    public static GameState initial(int startingPlayer) {
        if (startingPlayer < 0 || startingPlayer > 1) {
            throw new IllegalArgumentException("Người đi trước không hợp lệ");
        }
        GameState s = new GameState();
        for (int i = 0; i < PIT_COUNT; i++) {
            if (isQuanPit(i)) s.quan[i] = true;
            else s.dan[i] = DAN_PER_PIT;
        }
        s.turn = startingPlayer;
        return s;
    }

    /** Bàn trống, dùng cho unit test. */
    public static GameState empty() { return new GameState(); }

    // ---------- hằng số hình học ----------
    public static boolean isQuanPit(int pit) { return pit == 0 || pit == 6; }

    public static int ownerOf(int pit) {
        if (pit >= 1 && pit <= 5) return 0;
        if (pit >= 7 && pit <= 11) return 1;
        return -1;
    }

    public static int firstPitOf(int player) { return player == 0 ? 1 : 7; }

    public static int next(int pit, int dir) { return Math.floorMod(pit + dir, PIT_COUNT); }

    // ---------- đọc trạng thái ----------
    public int danAt(int pit)        { return dan[pit]; }
    public boolean hasQuan(int pit)  { return quan[pit]; }
    public boolean isEmpty(int pit)  { return dan[pit] == 0 && !quan[pit]; }
    public int valueAt(int pit)      { return dan[pit] + (quan[pit] ? QUAN_VALUE : 0); }
    public int score(int player)     { return scores[player]; }
    public int turn()                { return turn; }
    public boolean isOver()          { return over; }

    public void passTurn() {
        if (over) throw new IllegalStateException("Ván đấu đã kết thúc");
        turn = 1 - turn;
    }

    public void forfeit(int loser) {
        if (loser < 0 || loser > 1) throw new IllegalArgumentException("Người chơi không hợp lệ");
        if (over) throw new IllegalStateException("Ván đấu đã kết thúc");
        over = true;
        forfeitLoser = loser;
    }

    /** Tổng giá trị trên bàn + điểm hai người. Luôn bằng 70 trong suốt ván. */
    public int totalValue() {
        int t = scores[0] + scores[1];
        for (int i = 0; i < PIT_COUNT; i++) t += valueAt(i);
        return t;
    }

    public Outcome outcome() {
        if (!over) throw new IllegalStateException("Ván chưa kết thúc");
        if (forfeitLoser == 0) return Outcome.P1_WIN;
        if (forfeitLoser == 1) return Outcome.P0_WIN;
        if (scores[0] > scores[1]) return Outcome.P0_WIN;
        if (scores[1] > scores[0]) return Outcome.P1_WIN;
        return Outcome.DRAW;
    }

    public GameState copy() {
        GameState c = new GameState();
        System.arraycopy(dan, 0, c.dan, 0, PIT_COUNT);
        System.arraycopy(quan, 0, c.quan, 0, PIT_COUNT);
        System.arraycopy(scores, 0, c.scores, 0, 2);
        c.turn = turn;
        c.over = over;
        c.forfeitLoser = forfeitLoser;
        return c;
    }

    // ---------- dựng bàn cho test ----------
    public GameState withDan(int pit, int n)       { dan[pit] = n; return this; }
    public GameState withQuan(int pit, boolean q)  { quan[pit] = q; return this; }
    public GameState withScore(int player, int s)  { scores[player] = s; return this; }
    public GameState withTurn(int player)          { turn = player; return this; }

    // ---------- thay đổi trạng thái (chỉ Rules được gọi) ----------
    int takeDan(int pit)           { int n = dan[pit]; dan[pit] = 0; return n; }
    void addDan(int pit, int n)    { dan[pit] += n; }
    void addScore(int player, int n) { scores[player] += n; }
    void setTurn(int player)       { turn = player; }

    int capture(int pit) {
        int v = valueAt(pit);
        dan[pit] = 0;
        quan[pit] = false;
        return v;
    }

    boolean quanPitsCleared() { return isEmpty(0) && isEmpty(6); }

    /** Kết thúc ván: dân còn trong 5 ô mỗi bên thuộc về chủ ô đó. */
    void finish(int loserByForfeit) {
        for (int p = 0; p < 2; p++) {
            int first = firstPitOf(p);
            for (int i = first; i < first + 5; i++) {
                scores[p] += dan[i];
                dan[i] = 0;
            }
        }
        over = true;
        forfeitLoser = loserByForfeit;
    }

    // ---------- in bàn cờ ra console ----------
    private String cell(int i) {
        return isQuanPit(i)
                ? "<" + (quan[i] ? "Q" : "-") + String.format("%2d", dan[i]) + ">"
                : "[" + String.format("%3d", dan[i]) + "]";
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Điểm: P0=%d  P1=%d  | Lượt: P%d%s%n",
                scores[0], scores[1], turn, over ? " (ĐÃ KẾT THÚC)" : ""));
        sb.append("     ");
        for (int i = 11; i >= 7; i--) sb.append(String.format("  %d%s ", i, i >= 10 ? "" : " "));
        sb.append('\n').append("     ");
        for (int i = 11; i >= 7; i--) sb.append(cell(i));
        sb.append('\n').append(cell(0)).append(" ".repeat(25)).append(cell(6)).append('\n');
        sb.append("     ");
        for (int i = 1; i <= 5; i++) sb.append(cell(i));
        sb.append('\n').append("     ");
        for (int i = 1; i <= 5; i++) sb.append(String.format("  %d   ", i));
        sb.append('\n');
        return sb.toString();
    }
}