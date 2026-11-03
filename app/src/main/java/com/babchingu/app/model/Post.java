package com.babchingu.app.model;

import java.util.ArrayList;
import java.util.List;

public class Post {
    public String id;
    public String authorUid;
    public String authorName;
    public String authorDept;
    public String mealTime;      // "12:10"
    public String place;         // "학생식당" 등
    public int maxPeople;        // 2~4
    public List<String> participants = new ArrayList<>();
    public String memo;
    public String status;        // Keys.STATUS_OPEN / STATUS_CLOSED
    public long createdAt;       // System.currentTimeMillis()

    public Post() {}
}
