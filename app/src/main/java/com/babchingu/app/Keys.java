package com.babchingu.app;

public class Keys {
    // 화면 간 데이터 전달 키
    public static final String EXTRA_POST_ID = "postId";

    // Firestore 컬렉션 이름
    public static final String COL_USERS = "users";
    public static final String COL_POSTS = "posts";
    public static final String FIELD_PARTICIPANTS = "participants";
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_CREATED_AT = "createdAt";

    // 모집 상태
    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_CLOSED = "CLOSED";

    // 신고 컬렉션
    public static final String COL_REPORTS = "reports";

    // 학교 이메일 도메인 (학교 메일 주소 형식 확인해서 맞게 수정)
    public static final String SCHOOL_DOMAIN = "@kw.ac.kr";

    // users 필드
    public static final String FIELD_NICKNAME = "nickname";

    public static final String FIELD_POST_ID = "postId";
    public static final String FIELD_REPORTER_UID = "reporterUid";
}