package com.babchingu.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.google.firebase.auth.FirebaseUser;
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
    private List<Post> latestPosts = new ArrayList<>();
    private boolean observing;
    private final Handler expiryHandler = new Handler(Looper.getMainLooper());
    private final Runnable expiryRefresh = () -> {
        if (observing) {
            renderPosts();
        }
    };

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
            observing = true;
            if (!latestPosts.isEmpty()) {
                renderPosts();
            }
            loadPosts();
        }
    }

    @Override
    protected void onStop() {
        observing = false;
        expiryHandler.removeCallbacks(expiryRefresh);
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
        FirebaseUser listeningUser = FirebaseAuth.getInstance().getCurrentUser();
        if (listeningUser == null) {
            return;
        }
        final String listeningUid = listeningUser.getUid();

        Log.d(TAG, "모집 목록 조회 시작");

        postsListener = FirebaseFirestore.getInstance()
                .collection(Keys.COL_POSTS)
                .orderBy(
                        Keys.FIELD_CREATED_AT,
                        Query.Direction.DESCENDING)
                .addSnapshotListener((snapshot, error) -> {
                    if (!observing || isFinishing() || isDestroyed()) {
                        return;
                    }
                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    // 로그아웃 직후의 콜백과 이전 계정의 콜백은 표시하지 않음
                    if (user == null || !listeningUid.equals(user.getUid())) {
                        return;
                    }
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

                    latestPosts = posts;
                    renderPosts();
                });
    }

    private void renderPosts() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (!observing || user == null || adapter == null) {
            return;
        }
        long now = System.currentTimeMillis();
        List<Post> visible = HomePolicy.visiblePosts(latestPosts, user.getUid(), now);
        adapter.setPosts(visible);
        tvHomeEmpty.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);

        // 홈을 계속 보고 있어도 만료 시점에 표시를 갱신. Firestore 데이터는 삭제하지 않음.
        expiryHandler.removeCallbacks(expiryRefresh);
        long nextDelay = Long.MAX_VALUE;
        for (Post post : latestPosts) {
            if (post == null || HomePolicy.isExpired(post, now)
                    || post.createdAt > Long.MAX_VALUE - Keys.POST_EXPIRE_MILLIS) {
                continue;
            }
            long delay = post.createdAt + Keys.POST_EXPIRE_MILLIS - now;
            if (delay > 0 && delay < nextDelay) {
                nextDelay = delay;
            }
        }
        if (nextDelay != Long.MAX_VALUE) {
            expiryHandler.postDelayed(expiryRefresh, Math.min(nextDelay, 60_000L));
        }
    }

    static final class HomePolicy {
        static boolean isExpired(Post post, long now) {
            return post == null || post.createdAt <= now - Keys.POST_EXPIRE_MILLIS;
        }

        static List<Post> visiblePosts(List<Post> source, String uid, long now) {
            List<Post> openPosts = new ArrayList<>();
            List<Post> closedPosts = new ArrayList<>();
            if (source == null || uid == null) {
                return openPosts;
            }
            for (Post post : source) {
                if (isExpired(post, now)) {
                    continue;
                }
                if (Keys.STATUS_CLOSED.equals(post.status)) {
                    if (post.participants != null && post.participants.contains(uid)) {
                        closedPosts.add(post);
                    }
                } else {
                    openPosts.add(post);
                }
            }
            // Firestore가 준 최신순을 각 묶음 안에서는 그대로 유지
            openPosts.addAll(closedPosts);
            return openPosts;
        }
    }
}
