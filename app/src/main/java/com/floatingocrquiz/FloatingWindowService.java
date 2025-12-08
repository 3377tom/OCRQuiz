package com.floatingocrquiz;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.projection.MediaProjection;
import android.net.Uri;
import android.provider.Settings;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
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
    private ImageButton captureButton;
    private ImageButton settingsButton;
    
    // 默认截图范围设置
    private static final String PREFS_NAME = "ScreenshotPrefs";
    private static final String PREF_DEFAULT_RECT = "defaultRect";
    private boolean isSettingDefaultRange = false;

    private boolean isWindowShowing = false;

    // 拖拽相关变量
    private int initialX;
    private int initialY;
    private float initialTouchX;
    private float initialTouchY;
    
    // 文字颜色相关
    private int currentTextColor = Color.WHITE;
    private int currentShadowColor = Color.BLACK;

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
        // 检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show();
            // 打开权限设置页面
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return;
        }
        
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
        settingsButton = floatingView.findViewById(R.id.settings_button);

        // 设置截图按钮点击事件
        captureButton.setOnClickListener(v -> {
            answerTextView.setText(R.string.capturing);
            // 重置为截图识别模式
            isSettingDefaultRange = false;
            // 启动屏幕捕获功能
            startScreenCapture();
        });
        
        // 设置截图范围按钮点击事件
        settingsButton.setOnClickListener(v -> {
            answerTextView.setText("请选择默认截图范围");
            // 设置为截图范围设置模式
            isSettingDefaultRange = true;
            // 启动屏幕捕获功能用于设置默认范围
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
                    
                    case MotionEvent.ACTION_UP:
                        // 拖拽结束后更新文字颜色以适应新背景
                        updateTextColorBasedOnBackground();
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
            intent.putExtra("SETTING_DEFAULT_RANGE", isSettingDefaultRange);
            startService(intent);
        } else {
            // 否则请求权限
            Intent intent = new Intent(this, MediaProjectionActivity.class);
            intent.putExtra("SETTING_DEFAULT_RANGE", isSettingDefaultRange);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }

    /**
     * 计算颜色亮度，0-255，值越大越亮
     */
    private int calculateLuminance(int color) {
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return (int) Math.sqrt(0.299 * red * red + 0.587 * green * green + 0.114 * blue * blue);
    }
    
    /**
     * 更新文字颜色以适应背景色
     */
    private void updateTextColorBasedOnBackground() {
        if (floatingView == null || windowManager == null) {
            return;
        }
        
        try {
            // 获取悬浮窗当前位置
            int x = layoutParams.x;
            int y = layoutParams.y;
            
            // 创建一个小矩形区域，用于采样背景色（悬浮窗左上角位置）
            Rect rect = new Rect(x, y, x + 10, y + 10);
            
            // 获取屏幕截图
            MediaProjection mediaProjection = ((OCRApplication) getApplication()).getMediaProjection();
            if (mediaProjection != null) {
                // 创建ImageReader获取屏幕像素
                android.media.ImageReader imageReader = android.media.ImageReader.newInstance(
                        rect.width(), rect.height(), android.graphics.PixelFormat.RGBA_8888, 1);
                
                // 创建虚拟显示
                android.hardware.display.VirtualDisplay virtualDisplay = mediaProjection.createVirtualDisplay(
                        "BackgroundCapture",
                        rect.width(), rect.height(), 160,
                        android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        imageReader.getSurface(), null, null);
                
                // 获取图像
                android.media.Image image = imageReader.acquireLatestImage();
                if (image != null) {
                    // 转换为Bitmap
                    Bitmap bitmap = imageToBitmap(image);
                    if (bitmap != null) {
                        // 获取像素颜色（取中心像素）
                        int centerX = bitmap.getWidth() / 2;
                        int centerY = bitmap.getHeight() / 2;
                        int backgroundColor = bitmap.getPixel(centerX, centerY);
                        
                        // 计算背景色亮度
                        int luminance = calculateLuminance(backgroundColor);
                        
                        // 根据亮度调整文字颜色：亮度>128使用黑色文字，否则使用白色文字
                        int newTextColor = luminance > 128 ? Color.BLACK : Color.WHITE;
                        int newShadowColor = luminance > 128 ? Color.WHITE : Color.BLACK;
                        
                        // 更新文字颜色和阴影
                        if (newTextColor != currentTextColor) {
                            answerTextView.setTextColor(newTextColor);
                            answerTextView.setShadowColor(newShadowColor);
                            currentTextColor = newTextColor;
                            currentShadowColor = newShadowColor;
                        }
                        
                        bitmap.recycle();
                    }
                    image.close();
                }
                
                // 释放资源
                virtualDisplay.release();
                imageReader.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "更新文字颜色失败: " + e.getMessage());
        }
    }
    
    /**
     * 将Image转换为Bitmap
     */
    private Bitmap imageToBitmap(android.media.Image image) {
        try {
            android.media.Image.Plane[] planes = image.getPlanes();
            if (planes == null || planes.length == 0) {
                return null;
            }
            
            android.media.Image.Plane plane = planes[0];
            java.nio.ByteBuffer buffer = plane.getBuffer();
            if (buffer == null) {
                return null;
            }
            
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int rowPadding = rowStride - pixelStride * image.getWidth();
            
            // 创建Bitmap
            Bitmap bitmap = Bitmap.createBitmap(
                    image.getWidth() + rowPadding / pixelStride,
                    image.getHeight(),
                    Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);
            
            // 裁剪到实际大小
            bitmap = Bitmap.createBitmap(
                    bitmap,
                    0, 0,
                    image.getWidth(),
                    image.getHeight());
            
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "将Image转换为Bitmap失败: " + e.getMessage());
            return null;
        }
    }
    
    public void updateAnswer(String answer) {
        if (answerTextView != null) {
            if (answer == null || answer.isEmpty()) {
                // 清除"正在截图"提示
                answerTextView.setText("");
            } else {
                answerTextView.setText(getString(R.string.answer_prefix) + answer);
            }
            
            // 更新文字颜色以适应背景
            updateTextColorBasedOnBackground();
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