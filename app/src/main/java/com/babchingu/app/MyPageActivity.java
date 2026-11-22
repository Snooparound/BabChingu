package com.babchingu.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.babchingu.app.model.Post;
import com.babchingu.app.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class MyPageActivity extends AppCompatActivity {

    TextView tvMyNickname, tvMyInfo, tvMyEmpty;
    RadioGroup rgMyTab;
    ListView lvMyPosts;

    FirebaseFirestore db;
    String myUid;

    // 내가 만든 모임 / 참여한 모임
    List<Post> createdPosts = new ArrayList<>();
    List<Post> joinedPosts = new ArrayList<>();
    // 지금 화면에 보이는 목록 (누르면 이 순서로 상세 화면 이동)
    List<Post> shownPosts = new ArrayList<>();

    ArrayAdapter<String> adapter;
    List<String> rows = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_page);
        setTitle("마이페이지");
        // 상단 바 왼쪽에 뒤로가기(←) 버튼 표시
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // 로그인이 풀려 있으면 로그인 화면으로
        FirebaseUser fu = FirebaseAuth.getInstance().getCurrentUser();
        if (fu == null) {
            goLogin();
            return;
        }
        myUid = fu.getUid();
        db = FirebaseFirestore.getInstance();

        tvMyNickname = findViewById(R.id.tvMyNickname);
        tvMyInfo = findViewById(R.id.tvMyInfo);
        tvMyEmpty = findViewById(R.id.tvMyEmpty);
        rgMyTab = findViewById(R.id.rgMyTab);
        lvMyPosts = findViewById(R.id.lvMyPosts);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows);
        lvMyPosts.setAdapter(adapter);

        // 목록 한 칸 누르면 모집 상세 화면으로 (postId 전달)
        lvMyPosts.setOnItemClickListener((parent, view, position, id) -> {
            Post p = shownPosts.get(position);
            Intent intent = new Intent(MyPageActivity.this, PostDetailActivity.class);
            intent.putExtra(Keys.EXTRA_POST_ID, p.id);
            startActivity(intent);
        });

        rgMyTab.setOnCheckedChangeListener((group, checkedId) -> showTab());

        loadProfile();
        loadMyPosts();
    }

    // 내 정보 (닉네임을 바꾸면 자동으로 다시 표시됨)
    private void loadProfile() {
        listeners.add(db.collection(Keys.COL_USERS).document(myUid)
                .addSnapshotListener((doc, e) -> {
                    if (e != null || doc == null) return;
                    User u = doc.toObject(User.class);
                    if (u == null) {
                        tvMyNickname.setText("프로필 정보가 없어요");
                        return;
                    }
                    tvMyNickname.setText(u.nickname + " 님");
                    tvMyInfo.setText(u.dept + " · " + u.email);
                }));
    }

    // 내가 참여자에 들어간 글 전부 가져오기
    // → 작성자가 나면 "내가 만든 모임", 아니면 "참여한 모임"
    // (모집글 작성자도 participants에 들어가므로 쿼리 하나로 둘 다 해결)
    private void loadMyPosts() {
        listeners.add(db.collection(Keys.COL_POSTS)
                .whereArrayContains(Keys.FIELD_PARTICIPANTS, myUid)
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) {
                        // 로그아웃 직후에는 안내 없이 넘어감
                        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
                        Toast.makeText(this, "목록을 불러오지 못했어요", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    createdPosts.clear();
                    joinedPosts.clear();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Post p = d.toObject(Post.class);
                        if (p == null) continue;
                        p.id = d.getId();
                        // 올린 지 24시간이 지난 글은 숨김 (홈 화면에서 삭제됨)
                        if (System.currentTimeMillis() - p.createdAt >= Keys.POST_EXPIRE_MILLIS) continue;
                        if (myUid.equals(p.authorUid)) createdPosts.add(p);
                        else joinedPosts.add(p);
                    }
                    // 최신순 정렬 (앱에서 정렬해서 Firestore 인덱스가 필요 없음)
                    createdPosts.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
                    joinedPosts.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
                    showTab();
                }));
    }

    private void showTab() {
        List<Post> list = (rgMyTab.getCheckedRadioButtonId() == R.id.rbMyCreated)
                ? createdPosts : joinedPosts;

        shownPosts = new ArrayList<>(list);
        rows.clear();
        for (Post p : shownPosts) {
            int people = (p.participants == null) ? 0 : p.participants.size();
            String state = Keys.STATUS_CLOSED.equals(p.status)
                    ? "마감" : people + "/" + p.maxPeople + "명";
            rows.add(p.mealTime + "  ·  " + p.place + "  ·  " + state);
        }
        adapter.notifyDataSetChanged();

        // 목록이 비었을 때만 안내 문구 보이기 (4장 visibility)
        tvMyEmpty.setVisibility(shownPosts.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ── 7장 옵션 메뉴 ──
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.menu_my_page, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // 요즘 버전은 R.id가 상수가 아니라서 switch 대신 if문 사용
        if (item.getItemId() == R.id.menuMyEditNickname) {
            showEditNicknameDialog();
            return true;
        }
        if (item.getItemId() == R.id.menuMyLogout) {
            confirmLogout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // 7장 커스텀 뷰 대화상자
    private void showEditNicknameDialog() {
        View dialogView = View.inflate(MyPageActivity.this, R.layout.dialog_edit_nickname, null);
        EditText etEditNickname = dialogView.findViewById(R.id.etEditNickname);

        AlertDialog.Builder dlg = new AlertDialog.Builder(MyPageActivity.this);
        dlg.setTitle("닉네임 변경");
        dlg.setView(dialogView);
        dlg.setPositiveButton("변경", (d, w) -> {
            String name = etEditNickname.getText().toString().trim();
            if (name.length() < 2 || name.length() > 10) {
                Toast.makeText(this, "닉네임은 2~10자로 입력해주세요", Toast.LENGTH_SHORT).show();
                return;
            }
            db.collection(Keys.COL_USERS).document(myUid)
                    .update(Keys.FIELD_NICKNAME, name)
                    .addOnSuccessListener(x ->
                            Toast.makeText(this, "변경했어요", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "변경 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        });
        dlg.setNegativeButton("취소", null);
        dlg.show();
    }

    private void confirmLogout() {
        AlertDialog.Builder dlg = new AlertDialog.Builder(MyPageActivity.this);
        dlg.setTitle("로그아웃할까요?");
        dlg.setPositiveButton("로그아웃", (d, w) -> {
            FirebaseAuth.getInstance().signOut();
            goLogin();
        });
        dlg.setNegativeButton("취소", null);
        dlg.show();
    }

    // 로그인 화면으로 가면서 그동안 열린 화면 전부 닫기 (뒤로가기로 홈에 못 돌아오게)
    private void goLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
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
