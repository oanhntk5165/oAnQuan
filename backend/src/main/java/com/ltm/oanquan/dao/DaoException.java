package com.ltm.oanquan.dao;

/** Loi ha tang khi lam viec voi CSDL (mat ket noi, sai cau lenh...). */
public class DaoException extends RuntimeException {

    public DaoException(String thongBao, Throwable nguyenNhan) {
        super(thongBao, nguyenNhan);
    }
}