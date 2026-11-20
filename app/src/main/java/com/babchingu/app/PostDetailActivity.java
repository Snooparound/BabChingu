package com.babchingu.app;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class PostDetailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_detail);
        setTitle("모집 상세");

        String postId =
                getIntent().getStringExtra(Keys.EXTRA_POST_ID);

        if (postId == null || postId.trim().isEmpty()) {
            Toast.makeText(
                    this,
                    "잘못된 접근이에요",
                    Toast.LENGTH_SHORT
            ).show();
            finish();
            return;
        }

        Toast.makeText(
                this,
                "받은 postId: " + postId,
                Toast.LENGTH_SHORT
        ).show();
    }
}