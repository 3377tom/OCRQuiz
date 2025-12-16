package com.floatingocrquiz;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class SettingsActivity extends AppCompatActivity {

    private SeekBar opacitySeekBar;
    private TextView opacityValue;
    private SeekBar fontSizeSeekBar;
    private SeekBar questionLengthSeekBar;
    private TextView questionLengthValue;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // 设置工具栏
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // 显示返回按钮
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        // 初始化SharedPreferences
        sharedPreferences = getSharedPreferences("app_settings", MODE_PRIVATE);

        // 初始化透明度设置
        opacitySeekBar = findViewById(R.id.opacity_seekbar);
        opacityValue = findViewById(R.id.opacity_value);

        // 从SharedPreferences加载保存的透明度值
        int savedOpacity = sharedPreferences.getInt("window_opacity", 80);
        opacitySeekBar.setProgress(savedOpacity);
        opacityValue.setText(savedOpacity + "%");

        // 设置透明度SeekBar的监听事件
        opacitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // 更新透明度显示值
                opacityValue.setText(progress + "%");
                // 保存透明度设置
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt("window_opacity", progress);
                editor.apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 初始化字体大小设置
        fontSizeSeekBar = findViewById(R.id.font_size_seekbar);

        // 从SharedPreferences加载保存的字体大小值（0-4对应小到大）
        int savedFontSize = sharedPreferences.getInt("font_size", 2); // 默认中等大小
        fontSizeSeekBar.setProgress(savedFontSize);

        // 设置字体大小SeekBar的监听事件
        fontSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // 保存字体大小设置
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt("font_size", progress);
                editor.apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 初始化题干字数限制设置
        questionLengthSeekBar = findViewById(R.id.question_length_seekbar);
        questionLengthValue = findViewById(R.id.question_length_value);

        // 从SharedPreferences加载保存的题干字数限制值（0-200，0表示无限制）
        int savedQuestionLength = sharedPreferences.getInt("question_length_limit", 50); // 默认50字
        questionLengthSeekBar.setProgress(savedQuestionLength);
        questionLengthValue.setText(savedQuestionLength > 0 ? savedQuestionLength + "字" : "无限制");

        // 设置题干字数限制SeekBar的监听事件
        questionLengthSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // 更新题干字数限制显示值
                questionLengthValue.setText(progress > 0 ? progress + "字" : "无限制");
                // 保存题干字数限制设置
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt("question_length_limit", progress);
                editor.apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }
}