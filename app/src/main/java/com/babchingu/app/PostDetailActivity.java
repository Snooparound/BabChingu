package com.babchingu.app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.babchingu.app.model.Post;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;

import java.util.HashMap;
import java.util.Map;

public class PostDetailActivity extends AppCompatActivity {

    private static final String TAG = "PostDetailActivity";

    private TextView tvDetailTime;
    private TextView tvDetailPlace;
    private TextView tvDetailPeople;
    private TextView tvDetailAuthor;
    private TextView tvDetailMemo;
    private Button btnDetailJoin;
    private LinearLayout layoutDetailInfo;

    private FirebaseFirestore db;
    private DocumentReference postRef;
    private ListenerRegistration postListener;
    private String postId;
    private String myUid;
    private Post currentPost;
    private boolean joining;
    private boolean joinedSuccessfully;
    private boolean reporting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_detail);
        setTitle("모집 상세");

        postId = getIntent().getStringExtra(Keys.EXTRA_POST_ID);
        if (postId == null || postId.trim().isEmpty()
                || postId.contains("/")) {
            toast("잘못된 접근이에요");
            finish();
            return;
        }

        if (!checkLogin()) {
            return;
        }

        tvDetailTime = findViewById(R.id.tvDetailTime);
        tvDetailPlace = findViewById(R.id.tvDetailPlace);
        tvDetailPeople = findViewById(R.id.tvDetailPeople);
        tvDetailAuthor = findViewById(R.id.tvDetailAuthor);
        tvDetailMemo = findViewById(R.id.tvDetailMemo);
        btnDetailJoin = findViewById(R.id.btnDetailJoin);
        layoutDetailInfo = findViewById(R.id.layoutDetailInfo);

        db = FirebaseFirestore.getInstance();
        postRef = db.collection(Keys.COL_POSTS).document(postId);

        btnDetailJoin.setOnClickListener(view -> confirmJoin());
        registerForContextMenu(layoutDetailInfo);
        showLoading();
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (isFinishing() || postRef == null || postListener != null) {
            return;
        }
        if (!checkLogin()) {
            return;
        }

        showLoading();
        postListener = postRef.addSnapshotListener(
                MetadataChanges.INCLUDE,
                (document, error) -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    if (error != null) {
                        Log.e(TAG, "상세 조회 실패", error);
                        showUnavailable("모집글을 불러오지 못했어요");
                        toast("모집글을 불러오지 못했어요");
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
                        toast("삭제되었거나 존재하지 않는 모집글이에요");
                        finish();
                        return;
                    }

                    try {
                        Post post = document.toObject(Post.class);
                        if (post == null) {
                            showUnavailable("모집글 정보를 확인할 수 없어요");
                            toast("모집글 정보를 확인할 수 없어요");
                            return;
                        }
                        post.id = document.getId();
                        showPost(post);
                    } catch (RuntimeException exception) {
                        Log.e(TAG, "상세 데이터 변환 실패", exception);
                        showUnavailable("모집글 데이터 형식을 확인해 주세요");
                        toast("모집글 데이터 형식을 확인해 주세요");
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

    private boolean checkLogin() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return false;
        }
        if (myUid != null && !myUid.equals(user.getUid())) {
            toast("로그인 계정이 바뀌었어요. 홈에서 다시 열어 주세요");
            finish();
            return false;
        }
        myUid = user.getUid();
        return true;
    }

    private void showPost(Post post) {
        currentPost = post;
        int people = post.participants == null
                ? 0 : post.participants.size();
        boolean full = Keys.STATUS_CLOSED.equals(post.status)
                || people >= post.maxPeople;

        tvDetailTime.setText(textOrDefault(post.mealTime));
        tvDetailPlace.setText(textOrDefault(post.place));
        tvDetailPeople.setText(
                people + " / " + post.maxPeople + "명"
                        + (full ? " · 마감" : ""));
        tvDetailAuthor.setText(
                textOrDefault(post.authorDept)
                        + " · " + textOrDefault(post.authorName));
        tvDetailMemo.setText(
                post.memo == null || post.memo.trim().isEmpty()
                        ? "메모가 없어요" : post.memo);
        updateJoinButton();
    }

    private void updateJoinButton() {
        btnDetailJoin.setEnabled(false);
        if (joining) {
            btnDetailJoin.setText("참여 중...");
        } else if (currentPost == null) {
            btnDetailJoin.setText("참여하기");
        } else if (joinedSuccessfully
                || (currentPost.participants != null
                && currentPost.participants.contains(myUid))) {
            btnDetailJoin.setText("참여 완료");
        } else {
            int people = currentPost.participants == null
                    ? 0 : currentPost.participants.size();
            if (Keys.STATUS_CLOSED.equals(currentPost.status)
                    || people >= currentPost.maxPeople) {
                btnDetailJoin.setText("마감됐어요");
            } else if (!Keys.STATUS_OPEN.equals(currentPost.status)
                    || currentPost.maxPeople < 2
                    || currentPost.maxPeople > 4) {
                btnDetailJoin.setText("참여할 수 없는 모집글이에요");
            } else {
                btnDetailJoin.setText("참여하기");
                btnDetailJoin.setEnabled(true);
            }
        }
    }

    private void confirmJoin() {
        if (joining || currentPost == null || !btnDetailJoin.isEnabled()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("참여할까요?")
                .setMessage("시간에 맞춰 약속 장소로 가주세요!")
                .setPositiveButton("참여", (dialog, which) -> join())
                .setNegativeButton("취소", null)
                .show();
    }

    private void join() {
        if (joining || currentPost == null || !checkLogin()) {
            return;
        }
        joining = true;
        updateJoinButton();
        final String joiningUid = myUid;

        // 최신 인원과 모집 상태를 같은 트랜잭션 안에서 확인한 뒤 갱신
        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(postRef);
            Post post = snapshot.toObject(Post.class);
            if (post == null) {
                throw new FirebaseFirestoreException(
                        "삭제되었거나 존재하지 않는 모집글이에요",
                        FirebaseFirestoreException.Code.NOT_FOUND);
            }
            if (post.participants != null
                    && post.participants.contains(joiningUid)) {
                throw new FirebaseFirestoreException(
                        "이미 참여했어요",
                        FirebaseFirestoreException.Code.ALREADY_EXISTS);
            }
            if (Keys.STATUS_CLOSED.equals(post.status)) {
                throw new FirebaseFirestoreException(
                        "이미 마감된 모집글이에요",
                        FirebaseFirestoreException.Code.FAILED_PRECONDITION);
            }
            if (!Keys.STATUS_OPEN.equals(post.status)
                    || post.maxPeople < 2 || post.maxPeople > 4) {
                throw new FirebaseFirestoreException(
                        "모집글 데이터 형식을 확인해 주세요",
                        FirebaseFirestoreException.Code.FAILED_PRECONDITION);
            }

            int people = post.participants == null
                    ? 0 : post.participants.size();
            if (people >= post.maxPeople) {
                throw new FirebaseFirestoreException(
                        "정원이 찼어요",
                        FirebaseFirestoreException.Code.FAILED_PRECONDITION);
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put(Keys.FIELD_PARTICIPANTS, FieldValue.arrayUnion(joiningUid));
            if (people + 1 >= post.maxPeople) {
                updates.put(Keys.FIELD_STATUS, Keys.STATUS_CLOSED);
            }
            transaction.update(postRef, updates);
            return null;
        }).addOnSuccessListener(result -> {
            joining = false;
            joinedSuccessfully = true;
            if (isFinishing() || isDestroyed()) {
                return;
            }
            updateJoinButton();
            toast("참여 완료! 맛있게 드세요");
        }).addOnFailureListener(error -> {
            joining = false;
            Log.e(TAG, "참여 실패", error);
            if (isFinishing() || isDestroyed()) {
                return;
            }
            // 무조건 활성화하지 않고, 최신 글의 상태로 버튼을 다시 판단
            updateJoinButton();
            toast(error.getMessage() == null
                    ? "참여하지 못했어요" : error.getMessage());
        });
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view,
                                    ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, view, menuInfo);
        if (view == layoutDetailInfo && currentPost != null) {
            getMenuInflater().inflate(R.menu.menu_detail, menu);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menuDetailReport) {
            if (currentPost == null || reporting) {
                return true;
            }
            new AlertDialog.Builder(this)
                    .setTitle("신고할까요?")
                    .setMessage("이 모집글을 부적절한 글로 신고해요.")
                    .setPositiveButton("신고", (dialog, which) -> report())
                    .setNegativeButton("취소", null)
                    .show();
            return true;
        }
        return super.onContextItemSelected(item);
    }

    private void report() {
        if (reporting || currentPost == null || !checkLogin()) {
            return;
        }
        reporting = true;

        Map<String, Object> reportData = new HashMap<>();
        reportData.put(Keys.FIELD_POST_ID, postId);
        reportData.put(Keys.FIELD_REPORTER_UID, myUid);
        reportData.put(Keys.FIELD_CREATED_AT, System.currentTimeMillis());

        db.collection(Keys.COL_REPORTS).add(reportData)
                .addOnSuccessListener(reference -> {
                    reporting = false;
                    if (!isFinishing() && !isDestroyed()) {
                        toast("신고가 접수됐어요");
                    }
                })
                .addOnFailureListener(error -> {
                    reporting = false;
                    Log.e(TAG, "신고 실패", error);
                    if (!isFinishing() && !isDestroyed()) {
                        toast("신고하지 못했어요. 다시 시도해 주세요");
                    }
                });
    }

    private void showLoading() {
        showUnavailable("모집글을 불러오는 중...");
    }

    private void showUnavailable(String message) {
        currentPost = null;
        tvDetailTime.setText(message);
        tvDetailPlace.setText("");
        tvDetailPeople.setText("");
        tvDetailAuthor.setText("");
        tvDetailMemo.setText("");
        btnDetailJoin.setText("참여하기");
        btnDetailJoin.setEnabled(false);
    }

    private String textOrDefault(String value) {
        return value == null || value.trim().isEmpty()
                ? "정보 없음" : value;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
