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
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        listeners.add(FirebaseFirestore.getInstance()
                .collection(Keys.COL_POSTS)
                .orderBy(Keys.FIELD_CREATED_AT, Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) {
                        // 로그아웃 직후에는 안내 없이 넘어감
                        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
                        Toast.makeText(this,
                                "목록을 불러오지 못했어요",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    long now = System.currentTimeMillis();
                    String myUid = FirebaseAuth.getInstance().getUid();

                    List<Post> openPosts = new ArrayList<>();
                    List<Post> closedPosts = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Post post = d.toObject(Post.class);
                        if (post == null) continue;
                        post.id = d.getId();

                        // 1) 올린 지 24시간이 지난 글: 목록에서 빼고 삭제
                        if (now - post.createdAt >= Keys.POST_EXPIRE_MILLIS) {
                            deleteExpired(d);
                            continue;
                        }

                        boolean closed = Keys.STATUS_CLOSED.equals(post.status);
                        boolean joined = post.participants != null
                                && post.participants.contains(myUid);

                        // 2) 마감된 글: 참여한 사람에게만 보이기
                        if (closed) {
                            if (joined) closedPosts.add(post);
                        } else {
                            openPosts.add(post);
                        }
                    }

                    // 모집 중인 글이 위, 내가 참여한 마감 글은 아래
                    List<Post> posts = new ArrayList<>(openPosts);
                    posts.addAll(closedPosts);

                    adapter.setPosts(posts);
                    tvHomeEmpty.setVisibility(
                            posts.isEmpty() ? View.VISIBLE : View.GONE);
                }));
    }

    // 같은 글을 여러 번 지우려 하지 않도록 기록
    private final Set<String> deleteTried = new HashSet<>();

    // 24시간 지난 글 삭제 (실패해도 목록에서는 이미 숨겨져 있음)
    private void deleteExpired(DocumentSnapshot d) {
        if (!deleteTried.add(d.getId())) return;
        d.getReference().delete();
    }

    // 실시간 연결 목록: 다른 화면에 갔다 와도 끊기지 않게 직접 관리
    private final List<ListenerRegistration> listeners = new ArrayList<>();

    // 화면이 완전히 닫힐 때 실시간 연결 해제
    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (ListenerRegistration r : listeners) r.remove();
        listeners.clear();
    }
}
