package com.ltm.oanquan.dao;

import com.ltm.oanquan.common.BusinessException;
import com.ltm.oanquan.model.Users;
import com.ltm.oanquan.model.Ranking;
import com.ltm.oanquan.server.AccountService;

import java.sql.Connection;

/** Kiểm tra nhanh kết nối và luồng tài khoản; chạy thủ công từ thư mục gốc dự án. */
public class DatabaseSmokeTest {

    public static void main(String[] args) throws Exception {
        try (Connection kn = DatabaseConnection.layKetNoi()) {
            System.out.println("1. Ket noi MySQL OK: " + kn.getMetaData().getDatabaseProductVersion());
        }

        AccountService control = new AccountService();

        try {
            Users moi = control.dangKy("thu_nghiem", "matkhau123");
            System.out.println("2. Dang ky OK, id = " + moi.getId());
        } catch (BusinessException e) {
            System.out.println("2. Dang ky: " + e.getMessage() + " (neu da chay lan truoc thi binh thuong)");
        }

        try {
            control.dangKy("thu_nghiem", "matkhau123");
            System.out.println("3. LOI: dang ky trung ten ma van thanh cong");
        } catch (BusinessException e) {
            System.out.println("3. Dang ky trung ten bi tu choi dung: " + e.getMessage());
        }

        try {
            Users nd = control.dangNhap("thu_nghiem", "matkhau123");
            int bac = Ranking.bac(nd.getDiem());
            System.out.println("4. Dang nhap dung OK: " + nd.getTenDangNhap() + ", diem = " + nd.getDiem()
                    + ", cap " + Ranking.capDo(nd.getDiem()) + ", bac " + Ranking.tenBac(bac));
            System.out.println("   Chuoi bam luu trong CSDL: " + nd.getMatKhauBam().substring(0, 12) + "...");
        } catch (BusinessException e) {
            System.out.println("4. LOI: dang nhap dung ma bi tu choi: " + e.getMessage());
        }

        try {
            control.dangNhap("thu_nghiem", "sai_mat_khau");
            System.out.println("5. LOI: sai mat khau ma van dang nhap duoc");
        } catch (BusinessException e) {
            System.out.println("5. Sai mat khau bi tu choi dung: " + e.getMessage());
        }

        try {
            control.dangKy("a b", "matkhau123");
            System.out.println("6. LOI: ten khong hop le ma van dang ky duoc");
        } catch (BusinessException e) {
            System.out.println("6. Ten khong hop le bi tu choi dung: " + e.getMessage());
        }
    }
}