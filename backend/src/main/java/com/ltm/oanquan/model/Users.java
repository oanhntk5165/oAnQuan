package com.ltm.oanquan.model;

/** Mot dong cua bang users. Chi chua du lieu, khong chua logic. */
public class Users {

    private long id;
    private String tenDangNhap;
    private String matKhauBam;
    private int diem;
    private int rating;

    public Users() {
    }

    public Users(long id, String tenDangNhap, String matKhauBam, int diem) {
        this(id, tenDangNhap, matKhauBam, diem, 0);
    }

    public Users(long id, String tenDangNhap, String matKhauBam, int diem, int rating) {
        this.id = id;
        this.tenDangNhap = tenDangNhap;
        this.matKhauBam = matKhauBam;
        this.diem = diem;
        this.rating = rating;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getTenDangNhap() { return tenDangNhap; }
    public void setTenDangNhap(String tenDangNhap) { this.tenDangNhap = tenDangNhap; }

    public String getMatKhauBam() { return matKhauBam; }
    public void setMatKhauBam(String matKhauBam) { this.matKhauBam = matKhauBam; }

    public int getDiem() { return diem; }
    public void setDiem(int diem) { this.diem = diem; }

    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }
}