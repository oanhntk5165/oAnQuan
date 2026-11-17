package com.ltm.oanquan.common;

/** Lỗi nghiệp vụ có thể hiển thị an toàn cho người chơi, chẳng hạn thông tin đăng nhập sai. */
public class BusinessException extends Exception {

    public BusinessException(String message) {
        super(message);
    }
}