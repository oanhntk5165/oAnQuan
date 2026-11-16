package com.ltm.oanquan.dao;

/** Báo riêng trường hợp username bị trùng để tầng dịch vụ đổi thành lỗi nghiệp vụ phù hợp. */
public class UsernameAlreadyExistsException extends Exception {

    public UsernameAlreadyExistsException(String tenDangNhap) {
        super("Ten dang nhap da ton tai: " + tenDangNhap);
    }
}