package com.babchingu.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.babchingu.app.model.Post;

import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends AppCompatActivity {

    private PostAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        setTitle("밥친구");

        RecyclerView rvHomePosts =
                findViewById(R.id.rvHomePosts);
        Button btnHomeCreate =
                findViewById(R.id.btnHomeCreate);
        Button btnHomeMyPage =
                findViewById(R.id.btnHomeMyPage);

        adapter = new PostAdapter(post -> {
            Intent intent = new Intent(
                    HomeActivity.this, PostDetailActivity.class);
            intent.putExtra(Keys.EXTRA_POST_ID, post.id);
            startActivity(intent);
        });

        rvHomePosts.setLayoutManager(
                new LinearLayoutManager(this));
        rvHomePosts.setAdapter(adapter);

        btnHomeCreate.setOnClickListener(view ->
                startActivity(new Intent(
                        HomeActivity.this,
                        CreatePostActivity.class)));

        btnHomeMyPage.setOnClickListener(view ->
                startActivity(new Intent(
                        HomeActivity.this,
                        MyPageActivity.class)));

        loadDummy();
    }

    private void loadDummy() {
        List<Post> posts = new ArrayList<>();

        for (int i = 1; i <= 3; i++) {
            Post post = new Post();
            post.id = "dummy" + i;
            post.authorUid = "test-author-" + i;
            post.authorName = "[테스트] 작성자" + i;
            post.authorDept = "컴퓨터정보공학부";
            post.mealTime = "12:" + (i * 10);
            post.place = "학생식당";
            post.maxPeople = 4;
            post.participants.add(post.authorUid);
            post.memo = "[테스트] 화면 이동 확인용";
            post.status = Keys.STATUS_OPEN;
            post.createdAt = System.currentTimeMillis();

            posts.add(post);
        }

        adapter.setPosts(posts);
    }
}