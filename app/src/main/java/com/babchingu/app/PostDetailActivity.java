package com.babchingu.app;

import android.content.Intent;
import android.os.Bundle;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PostDetailActivity extends AppCompatActivity {

    TextView tvDetailTime, tvDetailPlace, tvDetailPeople,
            tvDetailAuthor, tvDetailMemo;
    Button btnDetailJoin, btnDetailCancel, btnDetailClose, btnDetailDelete;
    LinearLayout layoutDetailInfo, layoutDetailAuthor;

    FirebaseFirestore db;
    DocumentReference postRef;
    String postId, myUid;
    boolean deleting = false;   // 내가 삭제하는 중이면 "삭제된 글" 안내를 띄우지 않음

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_detail);
        setTitle("모집 상세");
        // 상단 바 왼쪽에 뒤로가기(←) 버튼 표시
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        postId = getIntent().getStringExtra(Keys.EXTRA_POST_ID);
        if (postId == null || postId.trim().isEmpty()) {
            toast("잘못된 접근이에요");
            finish();
            return;
        }

        FirebaseUser fu = FirebaseAuth.getInstance().getCurrentUser();
        if (fu == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        myUid = fu.getUid();

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

        // 참여 인원이 바뀌면 화면도 자동으로 바뀜
        listeners.add(postRef.addSnapshotListener((doc, e) -> {
            if (e != null || doc == null) return;
            if (!doc.exists()) {
                if (!deleting) toast("삭제된 모집글이에요");
                finish();
                return;
            }
            Post p = doc.toObject(Post.class);
            if (p != null) showPost(p);
        }));

        btnDetailJoin.setOnClickListener(v -> confirmJoin());
        btnDetailCancel.setOnClickListener(v -> confirmCancel());
        btnDetailClose.setOnClickListener(v -> confirmClose());
        btnDetailDelete.setOnClickListener(v -> confirmDelete());

        // 정보 영역을 길게 누르면 신고 메뉴 (7장 컨텍스트 메뉴)
        registerForContextMenu(layoutDetailInfo);
    }

    private void showPost(Post p) {
        int people = (p.participants == null) ? 0 : p.participants.size();

        tvDetailTime.setText(p.mealTime);
        tvDetailPlace.setText("📍 " + p.place);
        tvDetailPeople.setText("👥 " + people + " / " + p.maxPeople + "명");
        tvDetailAuthor.setText(p.authorDept + " · " + p.authorName);
        tvDetailMemo.setText(p.memo == null || p.memo.isEmpty()
                ? "만남 메모 없음" : p.memo);

        boolean joined = p.participants != null && p.participants.contains(myUid);
        boolean full = people >= p.maxPeople
                || Keys.STATUS_CLOSED.equals(p.status);

        boolean isAuthor = myUid.equals(p.authorUid);

        if (joined) {
            btnDetailJoin.setText(isAuthor ? "내가 만든 모임" : "참여 완료");
            btnDetailJoin.setEnabled(false);
        } else if (full) {
            btnDetailJoin.setText("마감됐어요");
            btnDetailJoin.setEnabled(false);
        } else {
            btnDetailJoin.setText("참여하기");
            btnDetailJoin.setEnabled(true);
        }

        // 참여 취소는 참여한 사람에게만 보이기 (작성자는 제외)
        btnDetailCancel.setVisibility(joined && !isAuthor ? View.VISIBLE : View.GONE);

        // 작성자에게만 모집 마감 / 삭제 버튼 보이기
        layoutDetailAuthor.setVisibility(isAuthor ? View.VISIBLE : View.GONE);
        boolean closed = Keys.STATUS_CLOSED.equals(p.status);
        btnDetailClose.setText(closed ? "마감된 모임" : "모집 마감");
        btnDetailClose.setEnabled(!closed);
    }

    // 7장 대화상자로 한 번 더 확인
    private void confirmJoin() {
        AlertDialog.Builder dlg = new AlertDialog.Builder(PostDetailActivity.this);
        dlg.setTitle("참여할까요?");
        dlg.setMessage("시간에 맞춰 약속 장소로 가주세요!");
        dlg.setPositiveButton("참여", (dialog, which) -> join());
        dlg.setNegativeButton("취소", null);
        dlg.show();
    }

    // 트랜잭션: 정원 4명에 2명이 동시에 눌러도 5명이 되지 않게 막아줌
    private void join() {
        btnDetailJoin.setEnabled(false);

        db.runTransaction(tx -> {
            DocumentSnapshot snap = tx.get(postRef);
            Post p = snap.toObject(Post.class);
            if (p == null) {
                throw new FirebaseFirestoreException("글이 없어요",
                        FirebaseFirestoreException.Code.NOT_FOUND);
            }
            int people = (p.participants == null) ? 0 : p.participants.size();

            if (p.participants != null && p.participants.contains(myUid)) {
                throw new FirebaseFirestoreException("이미 참여했어요",
                        FirebaseFirestoreException.Code.ABORTED);
            }
            if (people >= p.maxPeople || Keys.STATUS_CLOSED.equals(p.status)) {
                throw new FirebaseFirestoreException("정원이 찼어요",
                        FirebaseFirestoreException.Code.ABORTED);
            }

            tx.update(postRef, Keys.FIELD_PARTICIPANTS, FieldValue.arrayUnion(myUid));
            // 내가 마지막 자리면 마감 처리
            if (people + 1 >= p.maxPeople) {
                tx.update(postRef, Keys.FIELD_STATUS, Keys.STATUS_CLOSED);
            }
            return null;
        }).addOnSuccessListener(r ->
                toast("참여 완료! 맛있게 드세요 🍚")
        ).addOnFailureListener(e -> {
            btnDetailJoin.setEnabled(true);
            toast(e.getMessage() != null ? e.getMessage() : "참여하지 못했어요");
        });
    }

    // 7장 대화상자: 예 / 아니오로 한 번 더 확인
    private void confirmCancel() {
        AlertDialog.Builder dlg = new AlertDialog.Builder(PostDetailActivity.this);
        dlg.setTitle("참여 취소");
        dlg.setMessage("참여를 취소하시겠습니까?");
        dlg.setPositiveButton("예", (dialog, which) -> cancelJoin());
        dlg.setNegativeButton("아니오", null);
        dlg.show();
    }

    // 트랜잭션: 참여자 목록에서 내 uid를 빼고, 마감됐던 모임은 다시 모집 중으로
    private void cancelJoin() {
        btnDetailCancel.setEnabled(false);

        db.runTransaction(tx -> {
            DocumentSnapshot snap = tx.get(postRef);
            Post p = snap.toObject(Post.class);
            if (p == null) {
                throw new FirebaseFirestoreException("글이 없어요",
                        FirebaseFirestoreException.Code.NOT_FOUND);
            }
            if (p.participants == null || !p.participants.contains(myUid)) {
                throw new FirebaseFirestoreException("참여하지 않은 모임이에요",
                        FirebaseFirestoreException.Code.ABORTED);
            }
            if (myUid.equals(p.authorUid)) {
                throw new FirebaseFirestoreException("내가 만든 모임은 취소할 수 없어요",
                        FirebaseFirestoreException.Code.ABORTED);
            }

            tx.update(postRef, Keys.FIELD_PARTICIPANTS, FieldValue.arrayRemove(myUid));
            // 정원이 차서 자동 마감됐던 모임이면 한 자리가 비었으니 다시 모집 중으로
            // (작성자가 직접 마감한 모임은 마감 그대로 유지)
            if (p.participants.size() >= p.maxPeople) {
                tx.update(postRef, Keys.FIELD_STATUS, Keys.STATUS_OPEN);
            }
            return null;
        }).addOnSuccessListener(r -> {
            btnDetailCancel.setEnabled(true);
            toast("참여를 취소했어요");
        }).addOnFailureListener(e -> {
            btnDetailCancel.setEnabled(true);
            toast(e.getMessage() != null ? e.getMessage() : "취소하지 못했어요");
        });
    }

    // 작성자: 모집 마감 (마감하면 참여한 사람에게만 보임)
    private void confirmClose() {
        AlertDialog.Builder dlg = new AlertDialog.Builder(PostDetailActivity.this);
        dlg.setTitle("모집 마감");
        dlg.setMessage("모집을 마감하시겠습니까?\n마감하면 참여한 사람에게만 보여요.");
        dlg.setPositiveButton("예", (dialog, which) -> closePost());
        dlg.setNegativeButton("아니오", null);
        dlg.show();
    }

    private void closePost() {
        btnDetailClose.setEnabled(false);
        postRef.update(Keys.FIELD_STATUS, Keys.STATUS_CLOSED)
                .addOnSuccessListener(x -> toast("모집을 마감했어요"))
                .addOnFailureListener(e -> {
                    btnDetailClose.setEnabled(true);
                    toast("마감하지 못했어요");
                });
    }

    // 작성자: 모집글 삭제 (되돌릴 수 없음)
    private void confirmDelete() {
        AlertDialog.Builder dlg = new AlertDialog.Builder(PostDetailActivity.this);
        dlg.setTitle("모집글 삭제");
        dlg.setMessage("모집글을 삭제하시겠습니까?\n삭제하면 되돌릴 수 없어요.");
        dlg.setPositiveButton("예", (dialog, which) -> deletePost());
        dlg.setNegativeButton("아니오", null);
        dlg.show();
    }

    private void deletePost() {
        deleting = true;
        btnDetailDelete.setEnabled(false);
        postRef.delete()
                .addOnSuccessListener(x -> {
                    toast("모집글을 삭제했어요");
                    finish();
                })
                .addOnFailureListener(e -> {
                    deleting = false;
                    btnDetailDelete.setEnabled(true);
                    toast("삭제하지 못했어요");
                });
    }

    // ── 7장 컨텍스트 메뉴: 신고하기 ──
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v == layoutDetailInfo) {
            getMenuInflater().inflate(R.menu.menu_detail, menu);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        // 요즘 버전은 R.id가 상수가 아니라서 switch 대신 if문 사용
        if (item.getItemId() == R.id.menuDetailReport) {
            AlertDialog.Builder dlg = new AlertDialog.Builder(this);
            dlg.setTitle("신고할까요?");
            dlg.setMessage("부적절한 글은 검토 후 삭제돼요.");
            dlg.setPositiveButton("신고", (d, w) -> report());
            dlg.setNegativeButton("취소", null);
            dlg.show();
            return true;
        }
        return super.onContextItemSelected(item);
    }

    private void report() {
        Map<String, Object> r = new HashMap<>();
        r.put("postId", postId);
        r.put("reporterUid", myUid);
        r.put("createdAt", System.currentTimeMillis());
        db.collection(Keys.COL_REPORTS).add(r)
                .addOnSuccessListener(d -> toast("신고가 접수됐어요"))
                .addOnFailureListener(e -> toast("신고하지 못했어요"));
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    // 상단 바의 ← 버튼을 누르면 이전 화면으로 돌아감
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
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
