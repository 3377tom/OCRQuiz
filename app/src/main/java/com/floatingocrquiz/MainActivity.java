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

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_OVERLAY_PERMISSION = 1;
    private static final int REQUEST_MEDIA_PROJECTION = 2;

    private Button startButton;
    private Button stopButton;
    private View customToastView;
    private ViewGroup customToastWindowManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startButton = findViewById(R.id.start_button);
        stopButton = findViewById(R.id.stop_button);

        startButton.setOnClickListener(v -> startFloatingWindow());
        stopButton.setOnClickListener(v -> stopFloatingWindow());

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
        }
    }
    
    // 由于MainActivity是Activity，Toast通常不会被拦截，所以暂时保留使用Toast
    // 如果需要，后续可以实现与ScreenCaptureService类似的自定义提示消息功能
}