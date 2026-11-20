package com.babchingu.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.babchingu.app.model.Post;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends AppCompatActivity {

    private PostAdapter adapter;
    private TextView tvHomeWeather;
    private TextView tvHomeEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        setTitle("밥친구");

        // 로그인이 풀려 있으면 로그인 화면으로
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        tvHomeWeather = findViewById(R.id.tvHomeWeather);
        tvHomeEmpty = findViewById(R.id.tvHomeEmpty);
        RecyclerView rvHomePosts =
                findViewById(R.id.rvHomePosts);
        Button btnHomeCreate =
                findViewById(R.id.btnHomeCreate);
        Button btnHomeMyPage =
                findViewById(R.id.btnHomeMyPage);

        // 목록 한 칸 누르면 상세 화면으로 (postId 전달)
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

        // 외부 API: 현재 날씨로 식사 추천 문구 표시
        WeatherApi.fetch(message -> tvHomeWeather.setText(message));

        loadPosts();
    }

    // Firestore 실시간 목록: 누가 새 글을 쓰거나 참여하면 새로고침 없이 바로 반영
    private void loadPosts() {
        FirebaseFirestore.getInstance()
                .collection(Keys.COL_POSTS)
                .orderBy(Keys.FIELD_CREATED_AT, Query.Direction.DESCENDING)
                .addSnapshotListener(this, (snap, e) -> {
                    if (e != null || snap == null) {
                        Toast.makeText(this,
                                "목록을 불러오지 못했어요",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    List<Post> posts = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Post post = d.toObject(Post.class);
                        if (post != null) {
                            post.id = d.getId();
                            posts.add(post);
                        }
                    }

                    adapter.setPosts(posts);
                    tvHomeEmpty.setVisibility(
                            posts.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }
}
