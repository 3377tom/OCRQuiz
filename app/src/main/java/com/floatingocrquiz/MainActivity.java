package com.floatingocrquiz;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.view.LayoutInflater;
import androidx.appcompat.app.AlertDialog;

import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_OVERLAY_PERMISSION = 1;
    private static final int REQUEST_MEDIA_PROJECTION = 2;
    private static final int REQUEST_SELECT_JSON_FILE = 3;

    private Button startButton;
    private Button stopButton;
    private Button importButton;
    private Button deleteAllButton;
    private QuestionBankHelper questionBankHelper;
    private View customToastView;
    private ViewGroup customToastWindowManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startButton = findViewById(R.id.start_button);
        stopButton = findViewById(R.id.stop_button);
        importButton = findViewById(R.id.import_button);
        deleteAllButton = findViewById(R.id.delete_all_button);
        
        // 初始化题库助手（使用单例模式）
        questionBankHelper = QuestionBankHelper.getInstance(this);

        startButton.setOnClickListener(v -> startFloatingWindow());
        stopButton.setOnClickListener(v -> stopFloatingWindow());
        importButton.setOnClickListener(v -> importQuestionBank());
        deleteAllButton.setOnClickListener(v -> deleteAllQuestions());

        // 检查并申请必要权限
        checkPermissions();
    }

    private void checkPermissions() {
        // 检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
        }
    }

    private void startFloatingWindow() {
        // 再次检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.overlay_permission_denied, Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
            return;
        }

        // 启动浮动窗口服务
        Intent serviceIntent = new Intent(this, FloatingWindowService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
        Toast.makeText(this, "浮动窗口已启动", Toast.LENGTH_SHORT).show();
    }

    private void stopFloatingWindow() {
        // 停止浮动窗口服务
        Intent serviceIntent = new Intent(this, FloatingWindowService.class);
        stopService(serviceIntent);
        Toast.makeText(this, "浮动窗口已停止", Toast.LENGTH_SHORT).show();
    }

    /**
     * 导入题库
     */
    private void importQuestionBank() {
        // 选择JSON文件
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.select_json_file)), REQUEST_SELECT_JSON_FILE);
    }
    
    /**
     * 删除所有题目
     */
    private void deleteAllQuestions() {
        new AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage("确定要删除所有题目吗？此操作不可恢复。")
                .setPositiveButton("确定", (dialog, which) -> {
                    // 获取当前题目数量
                    int currentCount = questionBankHelper.getQuestionCount();
                    if (currentCount == 0) {
                        Toast.makeText(MainActivity.this, "当前没有题目可以删除", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    // 删除所有题目
                    boolean success = questionBankHelper.deleteAllQuestions();
                    if (success) {
                        // 使用正确的字符串资源和参数
                        Toast.makeText(MainActivity.this, 
                                getString(R.string.delete_success, currentCount), 
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, R.string.delete_failed, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, R.string.overlay_permission_denied, Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_MEDIA_PROJECTION) {
            // 处理屏幕录制权限结果
            if (resultCode == RESULT_OK && data != null) {
                // 将权限结果传递给ScreenCaptureService
                Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
                serviceIntent.putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode);
                serviceIntent.putExtra(ScreenCaptureService.EXTRA_RESULT_INTENT, data);
                ContextCompat.startForegroundService(this, serviceIntent);
            } else {
                Toast.makeText(this, R.string.media_projection_permission_denied, Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_SELECT_JSON_FILE && resultCode == RESULT_OK) {
            // 处理导入题库文件
            if (data != null && data.getData() != null) {
                Uri uri = data.getData();
                try {
                    InputStream inputStream = getContentResolver().openInputStream(uri);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    StringBuilder stringBuilder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stringBuilder.append(line);
                    }
                    reader.close();
                    inputStream.close();
                    
                    // 导入题库
                    int importedCount = questionBankHelper.importQuestionBank(stringBuilder.toString());
                    if (importedCount > 0) {
                        Toast.makeText(this, 
                                getString(R.string.import_success, importedCount), 
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, R.string.import_failed, Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, R.string.import_failed, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
}