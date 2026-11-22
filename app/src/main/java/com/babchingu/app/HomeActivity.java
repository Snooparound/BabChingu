package com.babchingu.app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
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
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "HomeActivity";

    private PostAdapter adapter;
    private TextView tvHomeWeather;
    private TextView tvHomeEmpty;
    private ListenerRegistration postsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        setTitle("밥친구");

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            openLogin();
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

        WeatherApi.fetch(message -> runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) {
                tvHomeWeather.setText(message);
            }
        }));
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (isFinishing()) {
            return;
        }

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            openLogin();
            return;
        }

        if (adapter != null) {
            loadPosts();
        }
    }

    @Override
    protected void onStop() {
        if (postsListener != null) {
            postsListener.remove();
            postsListener = null;
        }
        super.onStop();
    }

    private void openLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private void loadPosts() {
        if (postsListener != null) {
            return;
        }

        Log.d(TAG, "모집 목록 조회 시작");

        postsListener = FirebaseFirestore.getInstance()
                .collection(Keys.COL_POSTS)
                .orderBy(
                        Keys.FIELD_CREATED_AT,
                        Query.Direction.DESCENDING)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "모집 목록 조회 실패", error);
                        Toast.makeText(
                                HomeActivity.this,
                                "목록을 불러오지 못했어요",
                                Toast.LENGTH_SHORT
                        ).show();
                        return;
                    }

                    if (snapshot == null) {
                        return;
                    }

                    Log.d(TAG, "조회 결과: " + snapshot.size()
                            + "개, 캐시="
                            + snapshot.getMetadata().isFromCache());

                    List<Post> posts = new ArrayList<>();

                    try {
                        for (DocumentSnapshot document
                                : snapshot.getDocuments()) {
                            Post post = document.toObject(Post.class);

                            if (post != null) {
                                post.id = document.getId();

                                if (post.participants == null) {
                                    post.participants = new ArrayList<>();
                                }

                                posts.add(post);
                            }
                        }
                    } catch (RuntimeException exception) {
                        Log.e(TAG, "모집글 데이터 변환 실패", exception);
                        Toast.makeText(
                                HomeActivity.this,
                                "모집글 데이터 형식을 확인해 주세요",
                                Toast.LENGTH_SHORT
                        ).show();
                        return;
                    }

                    adapter.setPosts(posts);
                    tvHomeEmpty.setVisibility(
                            posts.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }
}