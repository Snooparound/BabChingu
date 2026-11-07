package com.babchingu.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {

    EditText etLoginEmail, etLoginPassword;
    Button btnLoginSubmit, btnLoginSignUp;
    TextView tvLoginForgot;
    FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        setTitle("밥친구");

        etLoginEmail = findViewById(R.id.etLoginEmail);
        etLoginPassword = findViewById(R.id.etLoginPassword);
        btnLoginSubmit = findViewById(R.id.btnLoginSubmit);
        btnLoginSignUp = findViewById(R.id.btnLoginSignUp);
        tvLoginForgot = findViewById(R.id.tvLoginForgot);

        auth = FirebaseAuth.getInstance();

        btnLoginSubmit.setOnClickListener(v -> login());
        btnLoginSignUp.setOnClickListener(v ->
                startActivity(new Intent(this, SignUpActivity.class)));
        tvLoginForgot.setOnClickListener(v -> showResetDialog());
    }

    // 자동 로그인: 이미 로그인 + 인증 완료 상태면 바로 홈으로
    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser u = auth.getCurrentUser();
        if (u != null && u.isEmailVerified()) {
            goHome();
        }
    }

    private void login() {
        String email = etLoginEmail.getText().toString().trim();
        String pw = etLoginPassword.getText().toString();

        if (email.isEmpty() || pw.isEmpty()) {
            toast("이메일과 비밀번호를 입력해주세요");
            return;
        }

        btnLoginSubmit.setEnabled(false);

        auth.signInWithEmailAndPassword(email, pw)
                .addOnSuccessListener(result -> {
                    FirebaseUser u = result.getUser();
                    if (u == null) return;

                    // 메일 링크를 방금 눌렀을 수도 있으니 최신 상태로 새로고침
                    u.reload().addOnCompleteListener(t -> {
                        btnLoginSubmit.setEnabled(true);
                        if (u.isEmailVerified()) {
                            goHome();
                        } else {
                            showNotVerifiedDialog(u);
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    btnLoginSubmit.setEnabled(true);
                    toast("이메일 또는 비밀번호가 맞지 않아요");
                });
    }

    private void showNotVerifiedDialog(FirebaseUser u) {
        AlertDialog.Builder dlg = new AlertDialog.Builder(LoginActivity.this);
        dlg.setTitle("이메일 인증이 필요해요");
        dlg.setMessage(u.getEmail() + "로 보낸 메일의 링크를 눌러주세요.\n"
                + "(스팸함도 확인해주세요)");
        dlg.setPositiveButton("인증 메일 다시 보내기", (d, w) ->
                u.sendEmailVerification().addOnCompleteListener(t -> {
                    toast(t.isSuccessful() ? "다시 보냈어요" : "잠시 후 다시 시도해주세요");
                    auth.signOut();
                }));
        dlg.setNegativeButton("닫기", (d, w) -> auth.signOut());
        dlg.setCancelable(false);
        dlg.show();
    }

    // 7장 커스텀 뷰 대화상자 (실습 7-3과 같은 방식)
    private void showResetDialog() {
        View dialogView = View.inflate(LoginActivity.this, R.layout.dialog_reset_password, null);
        EditText etResetEmail = dialogView.findViewById(R.id.etResetEmail);
        etResetEmail.setText(etLoginEmail.getText().toString().trim());

        AlertDialog.Builder dlg = new AlertDialog.Builder(LoginActivity.this);
        dlg.setTitle("비밀번호 재설정");
        dlg.setView(dialogView);
        dlg.setPositiveButton("메일 보내기", (d, w) -> {
            String email = etResetEmail.getText().toString().trim();
            if (email.isEmpty()) {
                toast("이메일을 입력해주세요");
                return;
            }
            auth.sendPasswordResetEmail(email)
                    .addOnSuccessListener(x -> toast("재설정 메일을 보냈어요"))
                    .addOnFailureListener(e -> toast("가입된 이메일인지 확인해주세요"));
        });
        dlg.setNegativeButton("취소", null);
        dlg.show();
    }

    private void goHome() {
        startActivity(new Intent(this, HomeActivity.class));
        finish();   // 홈에서 뒤로가기 눌러도 로그인 화면으로 안 돌아오게
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}