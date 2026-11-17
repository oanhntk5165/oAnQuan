package com.ltm.oanquan.engine;

import java.util.List;
import java.util.Random;
import java.util.Scanner;

/** Bạn là người 0 (hàng dưới, ô 1-5), máy đánh ngẫu nhiên là người 1. */
public final class ConsoleDemo {

    public static void main(String[] args) {
        GameState s = GameState.initial();
        Random rnd = new Random();
        Scanner in = new Scanner(System.in);

        while (!s.isOver()) {
            System.out.println(s);
            List<Move> legal = Rules.legalMoves(s);
            Move m = null;
            if (s.turn() == 0) {
                while (m == null) {
                    System.out.print("Nhập 'ô hướng' (hướng + là tăng chỉ số ô, - là giảm), ví dụ 3 +: ");
                    try {
                        String[] p = in.nextLine().trim().split("\\s+");
                        Move c = new Move(Integer.parseInt(p[0]), p[1].equals("+") ? 1 : -1);
                        if (legal.contains(c)) m = c; else System.out.println("Nước đi không hợp lệ.");
                    } catch (RuntimeException e) {
                        System.out.println("Sai định dạng.");
                    }
                }
            } else {
                m = legal.get(rnd.nextInt(legal.size()));
                System.out.println("Máy đi: ô " + m.pit() + ", hướng " + (m.dir() > 0 ? "+" : "-"));
            }
            MoveResult r = Rules.play(s, m);
            System.out.println("Sự kiện: " + r.events());
        }
        System.out.println(s);
        System.out.println("Kết quả: " + s.outcome());
    }
}