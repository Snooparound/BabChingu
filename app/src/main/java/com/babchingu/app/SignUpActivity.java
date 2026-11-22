package com.babchingu.app;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.babchingu.app.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class SignUpActivity extends AppCompatActivity {

    EditText etSignUpEmail, etSignUpPassword, etSignUpPasswordCheck,
            etSignUpNickname, etSignUpDept;
    Button btnSignUpSubmit;
    FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);
        setTitle("회원가입");
        // 상단 바 왼쪽에 뒤로가기(←) 버튼 표시
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        etSignUpEmail = findViewById(R.id.etSignUpEmail);
        etSignUpPassword = findViewById(R.id.etSignUpPassword);
        etSignUpPasswordCheck = findViewById(R.id.etSignUpPasswordCheck);
        etSignUpNickname = findViewById(R.id.etSignUpNickname);
        etSignUpDept = findViewById(R.id.etSignUpDept);
        btnSignUpSubmit = findViewById(R.id.btnSignUpSubmit);

        auth = FirebaseAuth.getInstance();

        btnSignUpSubmit.setOnClickListener(v -> signUp());
    }

    private void signUp() {
        String email = etSignUpEmail.getText().toString().trim();
        String pw = etSignUpPassword.getText().toString();
        String pwCheck = etSignUpPasswordCheck.getText().toString();
        String nickname = etSignUpNickname.getText().toString().trim();
        String dept = etSignUpDept.getText().toString().trim();

        // ── 입력 검사 ──
        if (!email.endsWith(Keys.SCHOOL_DOMAIN)) {
            toast("학교 이메일(" + Keys.SCHOOL_DOMAIN + ")만 가입할 수 있어요");
            return;
        }
        if (pw.length() < 6) {          // Firebase 최소 길이가 6자
            toast("비밀번호는 6자 이상이어야 해요");
            return;
        }
        if (!pw.equals(pwCheck)) {
            toast("비밀번호가 서로 달라요");
            return;
        }
        if (nickname.length() < 2 || nickname.length() > 10) {
            toast("닉네임은 2~10자로 입력해주세요");
            return;
        }
        if (dept.isEmpty()) {
            toast("학과를 입력해주세요");
            return;
        }

        btnSignUpSubmit.setEnabled(false);   // 중복 클릭 방지

        // ── 1) 계정 생성 ──
        auth.createUserWithEmailAndPassword(email, pw)
                .addOnSuccessListener(result -> {
                    FirebaseUser fu = result.getUser();
                    if (fu == null) return;

                    // ── 2) users/{uid}에 정보 저장 (C가 이 경로로 닉네임을 읽어감) ──
                    User user = new User(fu.getUid(), email, nickname, dept);
                    FirebaseFirestore.getInstance()
                            .collection(Keys.COL_USERS)
                            .document(fu.getUid())
                            .set(user)
                            .addOnSuccessListener(x ->
                                    // ── 3) 인증 메일 발송 → 로그아웃 ──
                                    fu.sendEmailVerification().addOnCompleteListener(t -> {
                                        auth.signOut();
                                        showDoneDialog(email);
                                    }))
                            .addOnFailureListener(e -> {
                                btnSignUpSubmit.setEnabled(true);
                                toast("정보 저장 실패: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    btnSignUpSubmit.setEnabled(true);
                    if (e instanceof FirebaseAuthUserCollisionException) {
                        toast("이미 가입된 이메일이에요");
                    } else if (e instanceof FirebaseAuthWeakPasswordException) {
                        toast("비밀번호가 너무 쉬워요");
                    } else {
                        toast("가입 실패: " + e.getMessage());
                    }
                });
    }

    // 7장 대화상자: 뒤로가기로 못 닫게 setCancelable(false)
    private void showDoneDialog(String email) {
        AlertDialog.Builder dlg = new AlertDialog.Builder(SignUpActivity.this);
        dlg.setTitle("가입 완료! 🎉");
        dlg.setMessage(email + "로 인증 메일을 보냈어요.\n"
                + "메일의 링크를 누른 뒤 로그인해주세요.\n"
                + "(메일이 안 보이면 스팸함도 확인해주세요)");
        dlg.setPositiveButton("로그인하러 가기", (d, w) -> finish());
        dlg.setCancelable(false);
        dlg.show();
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
