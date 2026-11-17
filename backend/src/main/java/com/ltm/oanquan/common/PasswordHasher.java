package com.ltm.oanquan.common;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Tạo và xác minh chuỗi băm mật khẩu bằng PBKDF2-HMAC-SHA256.
 * Mỗi mật khẩu có salt ngẫu nhiên riêng; chuỗi lưu có dạng iterations:salt:hash (Base64).
 */
public final class PasswordHasher {

    private static final int SO_VONG_LAP = 120_000;
    private static final int DO_DAI_MUOI = 16;    // byte
    private static final int DO_DAI_BAM = 32;     // byte
    private static final SecureRandom NGAU_NHIEN = new SecureRandom();

    private PasswordHasher() { }

    public static String bam(String matKhau) {
        byte[] muoi = new byte[DO_DAI_MUOI];
        NGAU_NHIEN.nextBytes(muoi);
        byte[] bam = tinhBam(matKhau, muoi, SO_VONG_LAP, DO_DAI_BAM);
        Base64.Encoder ma = Base64.getEncoder();
        return SO_VONG_LAP + ":" + ma.encodeToString(muoi) + ":" + ma.encodeToString(bam);
    }

    /** Tính lại hash từ thông tin đã lưu và so sánh an toàn theo thời gian. */
    public static boolean kiemTra(String matKhau, String chuoiDaLuu) {
        if (matKhau == null || chuoiDaLuu == null) return false;
        try {
            String[] phan = chuoiDaLuu.split(":");
            if (phan.length != 3) return false;
            int soVong = Integer.parseInt(phan[0]);
            byte[] muoi = Base64.getDecoder().decode(phan[1]);
            byte[] bamDaLuu = Base64.getDecoder().decode(phan[2]);
            byte[] bamMoi = tinhBam(matKhau, muoi, soVong, bamDaLuu.length);
            // Tránh để thời gian so sánh tiết lộ vị trí byte đầu tiên khác nhau.
            return MessageDigest.isEqual(bamDaLuu, bamMoi);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static byte[] tinhBam(String matKhau, byte[] muoi, int soVong, int doDaiByte) {
        PBEKeySpec thongSo = new PBEKeySpec(matKhau.toCharArray(), muoi, soVong, doDaiByte * 8);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(thongSo).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Khong bam duoc mat khau", e);
        } finally {
            thongSo.clearPassword();
        }
    }
}