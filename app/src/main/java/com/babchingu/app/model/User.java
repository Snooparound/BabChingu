package com.babchingu.app.model;

public class User {
    public String uid;
    public String email;
    public String nickname;
    public String dept;

    public User() {}  // Firestore가 자동 변환할 때 필요. 지우면 안 돼요

    public User(String uid, String email, String nickname, String dept) {
        this.uid = uid;
        this.email = email;
        this.nickname = nickname;
        this.dept = dept;
    }
}