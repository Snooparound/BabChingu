package com.babchingu.app;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import android.content.Intent;

import android.widget.Button;

public class LoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        Button btnTemp = findViewById(R.id.btnTempHome);
        btnTemp.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, HomeActivity.class)));
    }
}