package com.floatingocrquiz;

import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

public class MediaProjectionActivity extends AppCompatActivity {

    private static final String TAG = "MediaProjectionActivity";
    private static final int REQUEST_CODE_MEDIA_PROJECTION = 1001;

    private MediaProjectionManager mediaProjectionManager;
    private boolean isSettingDefaultRange = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_projection);
        
        mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        
        // 获取从ScreenCaptureService传递的参数
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("SETTING_DEFAULT_RANGE")) {
            isSettingDefaultRange = intent.getBooleanExtra("SETTING_DEFAULT_RANGE", false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // 在onResume中启动媒体投影权限请求，确保Activity已经完全初始化
        Intent screenCaptureIntent = mediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(screenCaptureIntent, REQUEST_CODE_MEDIA_PROJECTION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_CODE_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                Log.d(TAG, "媒体投影权限已获取");
                
                // 启动屏幕捕获服务
                Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
                serviceIntent.putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode);
                serviceIntent.putExtra(ScreenCaptureService.EXTRA_RESULT_INTENT, data);
                // 传递设置默认截图范围的参数
                serviceIntent.putExtra("SETTING_DEFAULT_RANGE", isSettingDefaultRange);
                startService(serviceIntent);
            } else {
                Log.d(TAG, "媒体投影权限被拒绝");
            }
            
            // 关闭当前活动
            finish();
        }
    }
}