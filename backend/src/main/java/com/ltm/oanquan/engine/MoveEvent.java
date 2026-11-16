package com.ltm.oanquan.engine;

/**
 * Sự kiện nhỏ trong một nước đi, dùng để client làm hoạt ảnh và để lưu chi tiết ván.
 * PICKUP: bốc `value` quân ở ô `pit`.  SOW: thả 1 quân vào ô `pit`.
 * CAPTURE: ăn ô `pit` được `value` điểm.  REFILL: bù dân cho người chơi `value` (pit = -1).
 */
public record MoveEvent(Type type, int pit, int value) {

    public enum Type { PICKUP, SOW, CAPTURE, REFILL }

    static MoveEvent pickup(int pit, int count) { return new MoveEvent(Type.PICKUP, pit, count); }
    static MoveEvent sow(int pit)               { return new MoveEvent(Type.SOW, pit, 1); }
    static MoveEvent capture(int pit, int value){ return new MoveEvent(Type.CAPTURE, pit, value); }
    static MoveEvent refill(int player)         { return new MoveEvent(Type.REFILL, -1, player); }
}