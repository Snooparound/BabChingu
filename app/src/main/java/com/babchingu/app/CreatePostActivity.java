package com.babchingu.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.babchingu.app.model.Post;
import com.babchingu.app.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

public class CreatePostActivity extends AppCompatActivity {

    EditText etCreateTime, etCreateMemo;
    TextView tvCreatePlace;
    Button btnCreatePlace, btnCreateSubmit;
    RadioGroup rgCreatePeople;

    final String[] places = {"학생식당", "교직원식당", "학교 앞 식당"};
    int selectedPlace = -1;   // -1 = 아직 선택 안 함

    String myUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_post);
        setTitle("모집하기");
        // 상단 바 왼쪽에 뒤로가기(←) 버튼 표시
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        FirebaseUser fu = FirebaseAuth.getInstance().getCurrentUser();
        if (fu == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        myUid = fu.getUid();

        etCreateTime = findViewById(R.id.etCreateTime);
        etCreateMemo = findViewById(R.id.etCreateMemo);
        tvCreatePlace = findViewById(R.id.tvCreatePlace);
        btnCreatePlace = findViewById(R.id.btnCreatePlace);
        btnCreateSubmit = findViewById(R.id.btnCreateSubmit);
        rgCreatePeople = findViewById(R.id.rgCreatePeople);

        // 7장 setSingleChoiceItems: 장소 하나 고르기
        btnCreatePlace.setOnClickListener(v -> {
            AlertDialog.Builder dlg = new AlertDialog.Builder(CreatePostActivity.this);
            dlg.setTitle("어디서 먹을까요?");
            dlg.setSingleChoiceItems(places, selectedPlace, (dialog, which) -> {
                selectedPlace = which;
                tvCreatePlace.setText(places[which]);
                dialog.dismiss();   // 고르면 바로 닫힘
            });
            dlg.show();
        });

        btnCreateSubmit.setOnClickListener(v -> submit());
    }

    // 입력 검사 (4장 + 7장 토스트)
    private void submit() {
        String time = etCreateTime.getText().toString().trim();
        String memo = etCreateMemo.getText().toString().trim();

        // "12:10" 형식인지 검사
        if (!time.matches("^([01]\\d|2[0-3]):[0-5]\\d$")) {
            toast("시간을 12:10 형식으로 입력해주세요");
            return;
        }
        if (selectedPlace == -1) {
            toast("장소를 선택해주세요");
            return;
        }

        int checked = rgCreatePeople.getCheckedRadioButtonId();
        int maxPeople;
        if (checked == R.id.rbCreate2) maxPeople = 2;
        else if (checked == R.id.rbCreate3) maxPeople = 3;
        else if (checked == R.id.rbCreate4) maxPeople = 4;
        else {
            toast("인원을 선택해주세요");
            return;
        }

        savePost(time, places[selectedPlace], maxPeople, memo);
    }

    private void savePost(String time, String place, int maxPeople, String memo) {
        btnCreateSubmit.setEnabled(false);   // 두 번 눌러서 글 2개 생기는 것 방지

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // 1) 내 닉네임·학과 가져오기 (회원가입 때 users/{uid}에 저장해둠)
        db.collection(Keys.COL_USERS).document(myUid).get()
                .addOnSuccessListener(userDoc -> {
                    User u = userDoc.toObject(User.class);

                    // 2) 모집글 만들기
                    Post p = new Post();
                    p.authorUid = myUid;
                    p.authorName = (u != null) ? u.nickname : "익명";
                    p.authorDept = (u != null) ? u.dept : "";
                    p.mealTime = time;
                    p.place = place;
                    p.maxPeople = maxPeople;
                    p.memo = memo;
                    p.status = Keys.STATUS_OPEN;
                    p.createdAt = System.currentTimeMillis();
                    p.participants.add(myUid);   // 작성자도 참여자에 포함

                    // 3) 문서 ID를 먼저 만들고 그 ID를 글 안에도 저장
                    DocumentReference ref = db.collection(Keys.COL_POSTS).document();
                    p.id = ref.getId();

                    ref.set(p)
                            .addOnSuccessListener(x -> {
                                toast("모집글을 올렸어요!");
                                finish();   // 홈으로 돌아가면 목록에 바로 보임
                            })
                            .addOnFailureListener(e -> {
                                toast("실패: " + e.getMessage());
                                btnCreateSubmit.setEnabled(true);
                            });
                })
                .addOnFailureListener(e -> {
                    toast("사용자 정보를 못 불러왔어요");
                    btnCreateSubmit.setEnabled(true);
                });
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
}
