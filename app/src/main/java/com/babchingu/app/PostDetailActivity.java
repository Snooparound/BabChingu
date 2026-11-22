package com.babchingu.app;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.babchingu.app.model.Post;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;

public class PostDetailActivity extends AppCompatActivity {

    private static final String TAG = "PostDetailActivity";

    private TextView tvDetailTime;
    private TextView tvDetailPlace;
    private TextView tvDetailPeople;
    private TextView tvDetailAuthor;
    private TextView tvDetailMemo;
    private Button btnDetailJoin;

    private DocumentReference postRef;
    private ListenerRegistration postListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_detail);
        setTitle("모집 상세");

        tvDetailTime = findViewById(R.id.tvDetailTime);
        tvDetailPlace = findViewById(R.id.tvDetailPlace);
        tvDetailPeople = findViewById(R.id.tvDetailPeople);
        tvDetailAuthor = findViewById(R.id.tvDetailAuthor);
        tvDetailMemo = findViewById(R.id.tvDetailMemo);
        btnDetailJoin = findViewById(R.id.btnDetailJoin);

        btnDetailJoin.setEnabled(false);

        String postId =
                getIntent().getStringExtra(Keys.EXTRA_POST_ID);

        if (postId == null || postId.trim().isEmpty()
                || postId.contains("/")) {
            Toast.makeText(
                    this, "잘못된 접근이에요",
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        postRef = FirebaseFirestore.getInstance()
                .collection(Keys.COL_POSTS)
                .document(postId);

        showLoading();
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (postRef == null || postListener != null) {
            return;
        }

        postListener = postRef.addSnapshotListener(
                MetadataChanges.INCLUDE,
                (document, error) -> {
                    if (error != null) {
                        Log.e(TAG, "상세 조회 실패", error);
                        showUnavailable("모집글을 불러오지 못했어요");
                        Toast.makeText(
                                this,
                                "모집글을 불러오지 못했어요",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (document == null) {
                        return;
                    }

                    if (!document.exists()) {
                        // 캐시에 없다는 이유만으로 삭제된 글로 판단하지 않음
                        if (document.getMetadata().isFromCache()) {
                            showLoading();
                            return;
                        }

                        showUnavailable("존재하지 않는 모집글이에요");
                        Toast.makeText(
                                this,
                                "삭제되었거나 존재하지 않는 모집글이에요",
                                Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    try {
                        Post post = document.toObject(Post.class);

                        if (post == null) {
                            showUnavailable("모집글 정보를 확인할 수 없어요");
                            return;
                        }

                        post.id = document.getId();
                        showPost(post);
                    } catch (RuntimeException exception) {
                        Log.e(TAG, "상세 데이터 변환 실패", exception);
                        showUnavailable("모집글 데이터 형식을 확인해 주세요");
                        Toast.makeText(
                                this,
                                "모집글 데이터 형식을 확인해 주세요",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    protected void onStop() {
        if (postListener != null) {
            postListener.remove();
            postListener = null;
        }
        super.onStop();
    }

    private void showPost(Post post) {
        int people = post.participants == null
                ? 0 : post.participants.size();

        tvDetailTime.setText(textOrDefault(post.mealTime));
        tvDetailPlace.setText(textOrDefault(post.place));
        tvDetailPeople.setText(
                people + " / " + post.maxPeople + "명"
                        + (Keys.STATUS_CLOSED.equals(post.status)
                        ? " · 마감" : ""));
        tvDetailAuthor.setText(
                textOrDefault(post.authorDept)
                        + " · " + textOrDefault(post.authorName));
        tvDetailMemo.setText(
                post.memo == null || post.memo.trim().isEmpty()
                        ? "메모가 없어요" : post.memo);

        // 참여 기능은 다음 단계에서 연결
        btnDetailJoin.setText("참여하기");
        btnDetailJoin.setEnabled(false);
    }

    private void showLoading() {
        showUnavailable("모집글을 불러오는 중...");
    }

    private void showUnavailable(String message) {
        tvDetailTime.setText(message);
        tvDetailPlace.setText("");
        tvDetailPeople.setText("");
        tvDetailAuthor.setText("");
        tvDetailMemo.setText("");
        btnDetailJoin.setEnabled(false);
    }

    private String textOrDefault(String value) {
        return value == null || value.trim().isEmpty()
                ? "정보 없음" : value;
    }
}