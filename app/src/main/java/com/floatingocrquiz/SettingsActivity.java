package com.floatingocrquiz;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
    private SeekBar screenshotDelaySeekBar;
    private TextView screenshotDelayValue;
    private RadioGroup ocrInterfaceRadioGroup;
    private RadioButton baiduOcrRadio;
    private RadioButton paddleOcrRadio;
    private RadioGroup ocrLanguageRadioGroup;
    private RadioButton chineseOcrRadio;
    private RadioButton englishOcrRadio;
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

        // 初始化截图延迟设置
        screenshotDelaySeekBar = findViewById(R.id.screenshot_delay_seekbar);
        screenshotDelayValue = findViewById(R.id.screenshot_delay_value);

        // 从SharedPreferences加载保存的截图延迟值（0-118对应200ms-12s）
        int savedDelay = sharedPreferences.getInt("screenshot_delay", 0); // 默认200ms
        screenshotDelaySeekBar.setProgress(savedDelay);
        updateDelayValue(savedDelay);

        // 设置截图延迟SeekBar的监听事件
        screenshotDelaySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // 更新截图延迟显示值
                updateDelayValue(progress);
                // 保存截图延迟设置
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt("screenshot_delay", progress);
                editor.apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 初始化OCR接口选择设置
        ocrInterfaceRadioGroup = findViewById(R.id.ocr_interface_radio_group);
        baiduOcrRadio = findViewById(R.id.baidu_ocr_radio);
        paddleOcrRadio = findViewById(R.id.paddle_ocr_radio);

        // 从SharedPreferences加载保存的OCR接口设置
        String savedOcrInterface = sharedPreferences.getString("ocr_interface", "BAIDU_OCR");
        if (savedOcrInterface.equals("PADDLE_OCR")) {
            paddleOcrRadio.setChecked(true);
        } else {
            baiduOcrRadio.setChecked(true);
        }

        // 设置OCR接口选择的监听事件
        ocrInterfaceRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String selectedInterface;
            if (checkedId == R.id.baidu_ocr_radio) {
                selectedInterface = "BAIDU_OCR";
            } else {
                selectedInterface = "PADDLE_OCR";
            }
            // 保存OCR接口设置
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("ocr_interface", selectedInterface);
            editor.apply();
            
            // 更新OCRHelper中的当前接口
            OCRHelper.getInstance(SettingsActivity.this).setCurrentOCRInterface(
                    OCRHelper.OCRInterfaceType.valueOf(selectedInterface)
            );
        });
        
        // 初始化OCR语言选择设置
        ocrLanguageRadioGroup = findViewById(R.id.ocr_language_radio_group);
        chineseOcrRadio = findViewById(R.id.chinese_ocr_radio);
        englishOcrRadio = findViewById(R.id.english_ocr_radio);
        
        // 从SharedPreferences加载保存的OCR语言设置
        String savedOcrLanguage = sharedPreferences.getString("ocr_language", "CHINESE");
        if (savedOcrLanguage.equals("ENGLISH")) {
            englishOcrRadio.setChecked(true);
        } else {
            chineseOcrRadio.setChecked(true);
        }
        
        // 设置OCR语言选择的监听事件
        ocrLanguageRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String selectedLanguage;
            if (checkedId == R.id.chinese_ocr_radio) {
                selectedLanguage = "CHINESE";
            } else {
                selectedLanguage = "ENGLISH";
            }
            // 保存OCR语言设置
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("ocr_language", selectedLanguage);
            editor.apply();
            
            // 更新OCRHelper中的当前语言
            OCRHelper.getInstance(SettingsActivity.this).setCurrentOCRLanguage(
                    OCRHelper.OCRLanguageType.valueOf(selectedLanguage)
            );
        });
    }

    // 更新截图延迟显示值
    private void updateDelayValue(int progress) {
        // 计算实际延迟时间：200ms + progress * 100ms
        long delayMillis = 200 + progress * 100;
        if (delayMillis < 1000) {
            screenshotDelayValue.setText(delayMillis + "ms");
        } else {
            double delaySeconds = delayMillis / 1000.0;
            screenshotDelayValue.setText(String.format("%.1fs", delaySeconds));
        }
    }
}