package com.floatingocrquiz;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

public class FloatingWindowService extends Service {

    public static final String ACTION_UPDATE_ANSWER = "com.floatingocrquiz.UPDATE_ANSWER";
    public static final String EXTRA_ANSWER = "answer";

    private static final String TAG = "FloatingWindowService";
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "FloatingWindowChannel";

    private BroadcastReceiver answerReceiver;


    private WindowManager windowManager;
    private WindowManager.LayoutParams layoutParams;
    private View floatingView;
    private TextView answerTextView;
    private Button captureButton;

    private boolean isWindowShowing = false;

    // 拖拽相关变量
    private int initialX;
    private int initialY;
    private float initialTouchX;
    private float initialTouchY;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
        
        // 注册广播接收器
        answerReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_UPDATE_ANSWER.equals(intent.getAction())) {
                    String answer = intent.getStringExtra(EXTRA_ANSWER);
                    updateAnswer(answer);
                }
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_UPDATE_ANSWER);
        registerReceiver(answerReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isWindowShowing) {
            showFloatingWindow();
        }
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Floating Window Service",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("浮动窗口正在运行")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        return builder.build();
    }

    private void showFloatingWindow() {
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

        // 创建浮动窗口布局参数
        layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        // 设置窗口位置
        layoutParams.gravity = Gravity.TOP | Gravity.START;
        layoutParams.x = 100;
        layoutParams.y = 200;

        // 加载浮动窗口布局
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_window, null);
        answerTextView = floatingView.findViewById(R.id.answer_text);
        captureButton = floatingView.findViewById(R.id.capture_button);

        // 设置按钮点击事件
        captureButton.setOnClickListener(v -> {
            answerTextView.setText(R.string.capturing);
            // 启动屏幕捕获功能
            startScreenCapture();
        });

        // 设置拖拽功能
        floatingView.findViewById(R.id.floating_window_container).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = layoutParams.x;
                        initialY = layoutParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initialTouchX);
                        int dy = (int) (event.getRawY() - initialTouchY);
                        layoutParams.x = initialX + dx;
                        layoutParams.y = initialY + dy;
                        windowManager.updateViewLayout(floatingView, layoutParams);
                        return true;

                    default:
                        return false;
                }
            }
        });

        // 添加浮动窗口到系统
        windowManager.addView(floatingView, layoutParams);
        isWindowShowing = true;
    }

    private void startScreenCapture() {
        // 检查OCRApplication中是否已经有MediaProjection实例
        OCRApplication ocrApplication = (OCRApplication) getApplication();
        if (ocrApplication.getMediaProjection() != null) {
            // 如果已经有权限，直接启动ScreenCaptureService
            Intent intent = new Intent(this, ScreenCaptureService.class);
            startService(intent);
        } else {
            // 否则请求权限
            Intent intent = new Intent(this, MediaProjectionActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }

    public void updateAnswer(String answer) {
        if (answerTextView != null) {
            answerTextView.setText(getString(R.string.answer_prefix) + answer);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 取消注册广播接收器
        if (answerReceiver != null) {
            unregisterReceiver(answerReceiver);
        }
        if (isWindowShowing && floatingView != null && windowManager != null) {
            windowManager.removeView(floatingView);
            isWindowShowing = false;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}