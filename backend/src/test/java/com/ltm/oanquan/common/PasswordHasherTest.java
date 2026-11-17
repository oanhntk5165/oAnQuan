package com.ltm.oanquan.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    @Test
    void matKhauDungDuocChapNhan() {
        String bam = PasswordHasher.bam("abc123");
        assertTrue(PasswordHasher.kiemTra("abc123", bam));
    }

    @Test
    void matKhauSaiBiTuChoi() {
        String bam = PasswordHasher.bam("abc123");
        assertFalse(PasswordHasher.kiemTra("abc124", bam));
        assertFalse(PasswordHasher.kiemTra("", bam));
    }

    @Test
    void cungMatKhauNhungMuoiKhacNhauThiBamKhacNhau() {
        assertNotEquals(PasswordHasher.bam("abc123"), PasswordHasher.bam("abc123"));
    }

    @Test
    void chuoiLuuSaiDinhDangThiTraVeFalse() {
        assertFalse(PasswordHasher.kiemTra("abc123", "khong phai chuoi bam"));
        assertFalse(PasswordHasher.kiemTra("abc123", null));
        assertFalse(PasswordHasher.kiemTra(null, PasswordHasher.bam("abc123")));
    }

    @Test
    void chuoiBamKhongQuaDaiChoCotCsdl() {
        assertTrue(PasswordHasher.bam("abc123").length() <= 100);
    }
}