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

import androidx.appcompat.app.ActionBar;
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
    private Button btnDetailCancel;
    private Button btnDetailClose;
    private Button btnDetailDelete;
    private LinearLayout layoutDetailInfo;
    private LinearLayout layoutDetailAuthor;

    private FirebaseFirestore db;
    private DocumentReference postRef;
    private ListenerRegistration postListener;
    private String postId;
    private String myUid;
    private Post currentPost;
    private DetailAction activeAction;
    private boolean awaitingMembership;
    private boolean expectedMembership;
    private boolean awaitingClose;
    private boolean reporting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_detail);
        setTitle("모집 상세");
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

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
        btnDetailCancel = findViewById(R.id.btnDetailCancel);
        btnDetailClose = findViewById(R.id.btnDetailClose);
        btnDetailDelete = findViewById(R.id.btnDetailDelete);
        layoutDetailAuthor = findViewById(R.id.layoutDetailAuthor);
        layoutDetailInfo = findViewById(R.id.layoutDetailInfo);

        db = FirebaseFirestore.getInstance();
        postRef = db.collection(Keys.COL_POSTS).document(postId);

        btnDetailJoin.setOnClickListener(view -> confirmCurrentAction());
        btnDetailCancel.setOnClickListener(view -> confirmCurrentAction());
        btnDetailClose.setOnClickListener(view -> confirmClose());
        btnDetailDelete.setOnClickListener(view -> confirmDelete());
        registerForContextMenu(layoutDetailInfo);
        showLoading();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
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
                        // 취소 처리 결과는 해당 트랜잭션의 완료 콜백에서 안내
                        if (activeAction == DetailAction.CANCEL_POST) {
                            showUnavailable("모집을 취소하는 중...");
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
        btnDetailCancel.setEnabled(false);
        btnDetailCancel.setVisibility(View.GONE);
        btnDetailClose.setEnabled(false);
        btnDetailDelete.setEnabled(false);
        layoutDetailAuthor.setVisibility(View.GONE);
        if (currentPost != null && awaitingMembership
                && DetailPolicy.isParticipant(currentPost, myUid)
                == expectedMembership) {
            awaitingMembership = false;
        }
        if (currentPost != null && awaitingClose
                && Keys.STATUS_CLOSED.equals(currentPost.status)) {
            awaitingClose = false;
        }
        if (currentPost != null && DetailPolicy.isOwner(currentPost, myUid)) {
            layoutDetailAuthor.setVisibility(View.VISIBLE);
            boolean closed = Keys.STATUS_CLOSED.equals(currentPost.status);
            boolean idle = activeAction == null && !awaitingMembership && !awaitingClose;
            btnDetailClose.setText(closed ? "마감된 모임" : "모집 마감");
            btnDetailClose.setEnabled(idle && Keys.STATUS_OPEN.equals(currentPost.status));
            btnDetailDelete.setText("모집글 삭제");
            btnDetailDelete.setEnabled(idle);
        }

        if (activeAction != null) {
            if (activeAction == DetailAction.JOIN) {
                btnDetailJoin.setText("참여 중...");
            } else {
                btnDetailJoin.setText(activeAction == DetailAction.CANCEL_POST
                        || activeAction == DetailAction.CLOSE_POST
                        ? "내가 만든 모임" : "참여 완료");
                if (activeAction == DetailAction.LEAVE) {
                    btnDetailCancel.setText("취소 중...");
                    btnDetailCancel.setVisibility(View.VISIBLE);
                } else if (activeAction == DetailAction.CANCEL_POST) {
                    btnDetailDelete.setText("삭제 중...");
                } else {
                    btnDetailClose.setText("마감 중...");
                }
            }
            return;
        }
        if (currentPost == null) {
            btnDetailJoin.setText("참여하기");
            return;
        }
        if (awaitingMembership || awaitingClose) {
            btnDetailJoin.setText("변경 반영 중...");
            return;
        }

        DetailAction action = DetailPolicy.actionFor(currentPost, myUid);
        if (action == DetailAction.CANCEL_POST) {
            btnDetailJoin.setText("내가 만든 모임");
        } else if (action == DetailAction.LEAVE) {
            btnDetailJoin.setText("참여 완료");
            btnDetailCancel.setText("참여 취소");
            btnDetailCancel.setVisibility(View.VISIBLE);
            btnDetailCancel.setEnabled(true);
        } else if (action == DetailAction.JOIN) {
            if (DetailPolicy.hasExpired(currentPost, System.currentTimeMillis())) {
                btnDetailJoin.setText("모집 기간이 지났어요");
                return;
            }
            btnDetailJoin.setText("참여하기");
            btnDetailJoin.setEnabled(true);
        } else if (action == DetailAction.CLOSED) {
            btnDetailJoin.setText("마감됐어요");
            return;
        } else {
            btnDetailJoin.setText("참여할 수 없는 모집글이에요");
            return;
        }
    }

    private void confirmCurrentAction() {
        if (activeAction != null || awaitingMembership || awaitingClose
                || currentPost == null) {
            return;
        }
        DetailAction action = DetailPolicy.actionFor(currentPost, myUid);
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        if (action == DetailAction.LEAVE) {
            dialog.setTitle("참여를 취소할까요?");
            dialog.setMessage("이 모집의 참여 인원에서 빠지게 돼요.");
            dialog.setPositiveButton("참여 취소", (d, which) -> leave());
        } else if (action == DetailAction.JOIN) {
            dialog.setTitle("참여할까요?");
            dialog.setMessage("시간에 맞춰 약속 장소로 가주세요!");
            dialog.setPositiveButton("참여", (d, which) -> join());
        } else {
            return;
        }
        dialog.setNegativeButton("아니요", null).show();
    }

    private boolean beginAction(DetailAction action) {
        if (activeAction != null || awaitingMembership || awaitingClose
                || currentPost == null || !checkLogin()) {
            return false;
        }
        activeAction = action;
        updateJoinButton();
        return true;
    }

    private void join() {
        if (!beginAction(DetailAction.JOIN)) {
            return;
        }
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
            if (DetailPolicy.hasExpired(post, System.currentTimeMillis())) {
                throw new FirebaseFirestoreException(
                        "모집 기간이 지난 글이에요",
                        FirebaseFirestoreException.Code.FAILED_PRECONDITION);
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
            completeMembershipChange(true, "참여 완료! 맛있게 드세요");
        }).addOnFailureListener(error -> failAction(error, "참여하지 못했어요"));
    }

    private void leave() {
        if (!beginAction(DetailAction.LEAVE)) {
            return;
        }
        final String leavingUid = myUid;

        db.runTransaction(transaction -> {
            Post post = transaction.get(postRef).toObject(Post.class);
            String nextStatus;
            try {
                nextStatus = DetailPolicy.statusAfterLeaving(post, leavingUid);
            } catch (IllegalStateException exception) {
                throw new FirebaseFirestoreException(exception.getMessage(),
                        FirebaseFirestoreException.Code.FAILED_PRECONDITION);
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put(Keys.FIELD_PARTICIPANTS, FieldValue.arrayRemove(leavingUid));
            if (!nextStatus.equals(post.status)) {
                updates.put(Keys.FIELD_STATUS, nextStatus);
            }
            transaction.update(postRef, updates);
            return null;
        }).addOnSuccessListener(result -> {
            completeMembershipChange(false, "참여를 취소했어요");
        }).addOnFailureListener(error -> failAction(error, "참여를 취소하지 못했어요"));
    }

    private void confirmClose() {
        if (activeAction != null || awaitingMembership || awaitingClose
                || !DetailPolicy.isOwner(currentPost, myUid)
                || !btnDetailClose.isEnabled()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("모집 마감")
                .setMessage("모집을 마감할까요? 마감하면 참여한 사람에게만 보여요.")
                .setPositiveButton("마감", (dialog, which) -> closePost())
                .setNegativeButton("아니요", null)
                .show();
    }

    private void closePost() {
        if (!beginAction(DetailAction.CLOSE_POST)) {
            return;
        }
        final String closingUid = myUid;
        db.runTransaction(transaction -> {
            Post post = transaction.get(postRef).toObject(Post.class);
            String nextStatus;
            try {
                nextStatus = DetailPolicy.statusAfterClosing(post, closingUid);
            } catch (IllegalStateException exception) {
                throw new FirebaseFirestoreException(exception.getMessage(),
                        FirebaseFirestoreException.Code.FAILED_PRECONDITION);
            }
            transaction.update(postRef, Keys.FIELD_STATUS, nextStatus);
            return null;
        }).addOnSuccessListener(result -> {
            activeAction = null;
            awaitingClose = true;
            if (!isFinishing() && !isDestroyed()) {
                updateJoinButton();
                toast("모집을 마감했어요");
            }
        }).addOnFailureListener(error -> failAction(error, "마감하지 못했어요"));
    }

    private void confirmDelete() {
        if (activeAction != null || awaitingMembership || awaitingClose
                || !DetailPolicy.isOwner(currentPost, myUid)) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("모집글 삭제")
                .setMessage("모집글을 삭제할까요? 삭제하면 목록에서 사라지고 되돌릴 수 없어요.")
                .setPositiveButton("삭제", (dialog, which) -> cancelPost())
                .setNegativeButton("아니요", null)
                .show();
    }

    private void cancelPost() {
        if (!beginAction(DetailAction.CANCEL_POST)) {
            return;
        }
        final String cancellingUid = myUid;

        db.runTransaction(transaction -> {
            Post post = transaction.get(postRef).toObject(Post.class);
            try {
                DetailPolicy.requireOwner(post, cancellingUid);
            } catch (IllegalStateException exception) {
                throw new FirebaseFirestoreException(exception.getMessage(),
                        FirebaseFirestoreException.Code.FAILED_PRECONDITION);
            }
            transaction.delete(postRef);
            return null;
        }).addOnSuccessListener(result -> {
            activeAction = null;
            if (!isFinishing() && !isDestroyed()) {
                toast("모집글을 삭제했어요");
                finish();
            }
        }).addOnFailureListener(error -> failAction(error, "모집을 취소하지 못했어요"));
    }

    private void completeMembershipChange(boolean joined, String message) {
        activeAction = null;
        expectedMembership = joined;
        awaitingMembership = true;
        if (!isFinishing() && !isDestroyed()) {
            updateJoinButton();
            toast(message);
        }
    }

    private void failAction(Exception error, String fallback) {
        activeAction = null;
        awaitingMembership = false;
        awaitingClose = false;
        Log.e(TAG, fallback, error);
        if (!isFinishing() && !isDestroyed()) {
            updateJoinButton();
            toast(error.getMessage() == null ? fallback : error.getMessage());
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view,
                                    ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, view, menuInfo);
        if (view == layoutDetailInfo && currentPost != null
                && activeAction == null && !awaitingMembership && !awaitingClose) {
            getMenuInflater().inflate(R.menu.menu_detail, menu);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menuDetailReport) {
            if (currentPost == null || reporting
                    || activeAction != null || awaitingMembership || awaitingClose) {
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
        if (reporting || currentPost == null || activeAction != null
                || awaitingMembership || awaitingClose || !checkLogin()) {
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
        btnDetailCancel.setEnabled(false);
        btnDetailCancel.setVisibility(View.GONE);
        btnDetailClose.setEnabled(false);
        btnDetailDelete.setEnabled(false);
        layoutDetailAuthor.setVisibility(View.GONE);
    }

    private String textOrDefault(String value) {
        return value == null || value.trim().isEmpty()
                ? "정보 없음" : value;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    enum DetailAction {
        JOIN, LEAVE, CANCEL_POST, CLOSE_POST, CLOSED, UNAVAILABLE
    }

    // 화면 표시와 트랜잭션이 같은 참여·취소 기준을 사용
    static final class DetailPolicy {

        static boolean hasExpired(Post post, long now) {
            return post == null || post.createdAt <= now - Keys.POST_EXPIRE_MILLIS;
        }

        static boolean isParticipant(Post post, String uid) {
            return post != null && uid != null && post.participants != null
                    && post.participants.contains(uid);
        }

        static boolean isOwner(Post post, String uid) {
            return post != null && uid != null && !uid.trim().isEmpty()
                    && uid.equals(post.authorUid);
        }

        static DetailAction actionFor(Post post, String uid) {
            if (post == null || uid == null || uid.trim().isEmpty()) {
                return DetailAction.UNAVAILABLE;
            }
            if (isOwner(post, uid)) {
                return DetailAction.CANCEL_POST;
            }
            if (isParticipant(post, uid)) {
                return DetailAction.LEAVE;
            }
            if (post.maxPeople < 2 || post.maxPeople > 4
                    || (!Keys.STATUS_OPEN.equals(post.status)
                    && !Keys.STATUS_CLOSED.equals(post.status))) {
                return DetailAction.UNAVAILABLE;
            }
            int people = post.participants == null ? 0 : post.participants.size();
            if (Keys.STATUS_CLOSED.equals(post.status) || people >= post.maxPeople) {
                return DetailAction.CLOSED;
            }
            return DetailAction.JOIN;
        }

        static String statusAfterLeaving(Post post, String uid) {
            if (post == null) {
                throw new IllegalStateException("삭제되었거나 존재하지 않는 모집글이에요");
            }
            if (isOwner(post, uid)) {
                throw new IllegalStateException("작성자는 모집 취소를 선택해 주세요");
            }
            if (!isParticipant(post, uid)) {
                throw new IllegalStateException("이미 참여가 취소됐어요");
            }
            if (!Keys.STATUS_OPEN.equals(post.status)
                    && !Keys.STATUS_CLOSED.equals(post.status)) {
                throw new IllegalStateException("모집글 상태를 확인해 주세요");
            }

            int remaining = 0;
            for (String participant : post.participants) {
                if (!uid.equals(participant)) {
                    remaining++;
                }
            }
            // 정원 도달로 마감된 모집만, 취소로 자리가 생기면 다시 모집
            if (Keys.STATUS_CLOSED.equals(post.status)
                    && post.maxPeople >= 2 && post.maxPeople <= 4
                    && post.participants.size() >= post.maxPeople
                    && remaining < post.maxPeople) {
                return Keys.STATUS_OPEN;
            }
            return post.status;
        }

        static String statusAfterClosing(Post post, String uid) {
            requireOwner(post, uid);
            if (!Keys.STATUS_OPEN.equals(post.status)
                    && !Keys.STATUS_CLOSED.equals(post.status)) {
                throw new IllegalStateException("모집글 상태를 확인해 주세요");
            }
            return Keys.STATUS_CLOSED;
        }

        static void requireOwner(Post post, String uid) {
            if (post == null) {
                throw new IllegalStateException("삭제되었거나 존재하지 않는 모집글이에요");
            }
            if (!isOwner(post, uid)) {
                throw new IllegalStateException("작성자만 이 모집글을 변경할 수 있어요");
            }
        }
    }
}
