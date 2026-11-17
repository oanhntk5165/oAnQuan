package com.ltm.oanquan.server;

import com.ltm.oanquan.common.BusinessException;
import com.ltm.oanquan.common.PasswordHasher;
import com.ltm.oanquan.dao.UserDao;
import com.ltm.oanquan.dao.UsernameAlreadyExistsException;
import com.ltm.oanquan.model.Users;

import java.util.regex.Pattern;

/**
 * AccountService:
 * - Validate dữ liệu đăng ký / đăng nhập.
 * - Gọi UserDao để đọc/ghi tài khoản.
 * - Sử dụng PasswordHasher để băm mật khẩu trước khi lưu.
 *
 * Mục đích: tách logic nghiệp vụ khỏi DAO và khỏi WebSocket/TCP.
 */
public class AccountService {

    private static final Pattern TEN_HOP_LE = Pattern.compile("[A-Za-z0-9_]{3,20}");
    private static final int DO_DAI_MAT_KHAU_TOI_THIEU = 6;
    private static final int DO_DAI_MAT_KHAU_TOI_DA = 64;

    private final UserDao nguoiDungDao = new UserDao();

    public Users dangKy(String tenDangNhap, String matKhau) throws BusinessException {
        if (tenDangNhap == null || !TEN_HOP_LE.matcher(tenDangNhap).matches()) {
            throw new BusinessException(
                    "Ten dang nhap gom 3-20 ky tu: chu khong dau, so hoac dau gach duoi");
        }
        if (matKhau == null || matKhau.length() < DO_DAI_MAT_KHAU_TOI_THIEU
                || matKhau.length() > DO_DAI_MAT_KHAU_TOI_DA) {
            throw new BusinessException("Mat khau phai tu " + DO_DAI_MAT_KHAU_TOI_THIEU
                    + " den " + DO_DAI_MAT_KHAU_TOI_DA + " ky tu");
        }
        try {
            long id = nguoiDungDao.them(tenDangNhap, PasswordHasher.bam(matKhau));
            return nguoiDungDao.timTheoId(id);
        } catch (UsernameAlreadyExistsException e) {
            // Chuyển lỗi đặc thù của tầng lưu trữ thành lỗi có thể trả về cho client.
            throw new BusinessException("Ten dang nhap da ton tai");
        }
    }

    public Users dangNhap(String tenDangNhap, String matKhau) throws BusinessException {
        Users nguoiDung = (tenDangNhap == null) ? null : nguoiDungDao.timTheoTen(tenDangNhap);
        // Dùng cùng thông báo cho tài khoản không tồn tại và mật khẩu sai để tránh dò username.
        if (nguoiDung == null || !PasswordHasher.kiemTra(matKhau, nguoiDung.getMatKhauBam())) {
            throw new BusinessException("Sai ten dang nhap hoac mat khau");
        }
        return nguoiDung;
    }
}