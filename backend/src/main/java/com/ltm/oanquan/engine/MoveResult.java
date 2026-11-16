package com.ltm.oanquan.engine;

import java.util.List;

/** Kết quả một nước đi: các sự kiện, điểm ăn được trong nước này, và ván đã kết thúc chưa. */
public record MoveResult(List<MoveEvent> events, int gained, boolean gameOver) { }