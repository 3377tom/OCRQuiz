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
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.provider.Settings;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.text.SpannableStringBuilder;
import android.widget.TextView;
import android.widget.Toast;
import android.text.SpannableString;
import android.text.Spannable;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;
import android.text.style.CharacterStyle;
import android.text.style.UpdateAppearance;

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
    
    // 防抖机制
    private Handler colorUpdateHandler;
    private Runnable colorUpdateRunnable;
    private static final long COLOR_UPDATE_DELAY = 200; // 200ms防抖延迟，提高灵敏度

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
        
        // 初始化防抖handler
        colorUpdateHandler = new Handler();
        
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
        
        // 设置窗口透明度（0.0-完全透明，1.0-完全不透明）
        // 窗口透明度由背景drawable控制，这里设置为完全不透明
        layoutParams.alpha = 1.0f;

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
        View.OnTouchListener touchListener = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // 获取初始位置和触摸点
                        initialX = layoutParams.x;
                        initialY = layoutParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        // 计算移动距离
                        int dx = (int) (event.getRawX() - initialTouchX);
                        int dy = (int) (event.getRawY() - initialTouchY);
                        
                        // 更新窗口位置
                        layoutParams.x = initialX + dx;
                        layoutParams.y = initialY + dy;
                        
                        // 立即更新窗口布局
                        windowManager.updateViewLayout(floatingView, layoutParams);
                        return true;
                    
                    case MotionEvent.ACTION_UP:
                        // 拖拽结束后更新文字颜色以适应新背景，添加防抖
                        // 移除之前的延迟任务
                        if (colorUpdateRunnable != null) {
                            colorUpdateHandler.removeCallbacks(colorUpdateRunnable);
                        }
                        
                        // 创建新的延迟任务
                        colorUpdateRunnable = new Runnable() {
                            @Override
                            public void run() {
                                updateTextColorBasedOnBackground();
                            }
                        };
                        
                        // 延迟执行
                        colorUpdateHandler.postDelayed(colorUpdateRunnable, COLOR_UPDATE_DELAY);
                        return true;

                    default:
                        return false;
                }
            }
        };
        
        // 为整个悬浮窗容器设置触摸事件监听器
        floatingView.findViewById(R.id.floating_window_container).setOnTouchListener(touchListener);
        
        // 为ScrollView设置触摸事件监听器，将事件传递给父容器
        ScrollView scrollView = floatingView.findViewById(R.id.scroll_view);
        scrollView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // 将触摸事件传递给父容器的触摸事件监听器
                return touchListener.onTouch(v, event);
            }
        });

        // 添加浮动窗口到系统
        windowManager.addView(floatingView, layoutParams);
        isWindowShowing = true;
        
        // 初始化文字颜色，使其适应当前背景
        updateTextColorBasedOnBackground();
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
        
        // 获取悬浮窗当前位置和尺寸
        int x = layoutParams.x;
        int y = layoutParams.y;
        int windowWidth = floatingView.getWidth();
        int windowHeight = floatingView.getHeight();
        
        // 创建一个更大的矩形区域，用于采样背景色（悬浮窗中心位置，扩大采样范围以提高准确性）
        int sampleSize = 60;
        int centerX = x + windowWidth / 2;
        int centerY = y + windowHeight / 2;
        Rect rect = new Rect(centerX - sampleSize/2, centerY - sampleSize/2, centerX + sampleSize/2, centerY + sampleSize/2);
        
        // 确保采样区域在屏幕范围内
        Point screenSize = new Point();
        windowManager.getDefaultDisplay().getSize(screenSize);
        rect.left = Math.max(0, rect.left);
        rect.top = Math.max(0, rect.top);
        rect.right = Math.min(screenSize.x, rect.right);
        rect.bottom = Math.min(screenSize.y, rect.bottom);
        
        // 只有当采样区域有效时才进行处理
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        
        // 获取屏幕截图
        MediaProjection mediaProjection = ((OCRApplication) getApplication()).getMediaProjection();
        if (mediaProjection == null) {
            Log.i(TAG, "MediaProjection为空，无法获取背景色");
            return;
        }
        
        // 创建ImageReader获取屏幕像素
        ImageReader imageReader = ImageReader.newInstance(
                        rect.width(), rect.height(), android.graphics.PixelFormat.RGBA_8888, 1);
        
        // 创建虚拟显示，使用VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY标志确保只获取背景内容，不包括悬浮窗本身
        int flags = android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR 
                | android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
        
        android.hardware.display.VirtualDisplay virtualDisplay = mediaProjection.createVirtualDisplay(
                "BackgroundCapture",
                rect.width(), rect.height(), 160,
                flags,
                imageReader.getSurface(), null, null);
        
        // 设置ImageAvailableListener，当有图像可用时处理
        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                try {
                    // 获取图像
                    Image image = reader.acquireLatestImage();
                    if (image != null) {
                        // 转换为Bitmap
                        Bitmap bitmap = imageToBitmap(image);
                        if (bitmap != null) {
                            // 计算平均背景色，而不是只取一个像素
                            int averageColor = calculateAverageColor(bitmap);
                            
                            Log.i(TAG, "获取到背景色: " + averageColor + ", 位置: (" + centerX + ", " + centerY + ")");
                            
                            // 计算背景色亮度
                            int luminance = calculateLuminance(averageColor);
                            Log.i(TAG, "背景色亮度: " + luminance);
                            
                            // 根据亮度调整文字颜色：亮度>128使用黑色文字，否则使用白色文字
                            // 减小亮度阈值缓冲，提高颜色切换的灵敏度
                            int newTextColor;
                            if (currentTextColor == Color.BLACK) {
                                // 当前是黑色文字，当亮度较低时切换为白色
                                newTextColor = luminance < 120 ? Color.WHITE : Color.BLACK;
                            } else {
                                // 当前是白色文字，当亮度较高时切换为黑色
                                newTextColor = luminance > 130 ? Color.BLACK : Color.WHITE;
                            }
                            
                            // 优化阴影设置：黑色文字使用淡灰色阴影，白色文字使用深灰色阴影，增强可读性
                            int newShadowColor;
                            float shadowRadius;
                            if (newTextColor == Color.BLACK) {
                                // 黑色文字使用淡灰色阴影，增大模糊半径以增强效果
                                newShadowColor = Color.GRAY;
                                shadowRadius = 2f;
                            } else {
                                // 白色文字使用深灰色阴影，保持适度模糊
                                newShadowColor = Color.DKGRAY;
                                shadowRadius = 1.5f;
                            }
                            
                            Log.i(TAG, "当前文字颜色: " + currentTextColor + ", 新文字颜色: " + newTextColor + ", 新阴影颜色: " + newShadowColor);
                            
                            // 更新文字颜色和阴影
                            if (newTextColor != currentTextColor) {
                                answerTextView.setTextColor(newTextColor);
                                // 清除旧阴影
                                answerTextView.setShadowLayer(0, 0, 0, Color.TRANSPARENT);
                                // 设置新阴影：模糊半径，X偏移，Y偏移，阴影颜色
                                answerTextView.setShadowLayer(shadowRadius, 1, 1, newShadowColor);
                                currentTextColor = newTextColor;
                                currentShadowColor = newShadowColor;
                                Log.d(TAG, "文字颜色已更新为: " + newTextColor + ", 阴影颜色: " + newShadowColor);
                            }
                            
                            bitmap.recycle();
                        }
                        image.close();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "处理图像失败: " + e.getMessage());
                } finally {
                    // 释放资源
                    virtualDisplay.release();
                    reader.close();
                }
            }
        }, null);
    }
    
    /**
     * 计算Bitmap的平均颜色
     */
    private int calculateAverageColor(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int totalPixels = width * height;
        long redSum = 0;
        long greenSum = 0;
        long blueSum = 0;
        
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int pixel = bitmap.getPixel(x, y);
                redSum += Color.red(pixel);
                greenSum += Color.green(pixel);
                blueSum += Color.blue(pixel);
            }
        }
        
        int avgRed = (int) (redSum / totalPixels);
        int avgGreen = (int) (greenSum / totalPixels);
        int avgBlue = (int) (blueSum / totalPixels);
        
        return Color.rgb(avgRed, avgGreen, avgBlue);
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
                // 直接使用答案文本，不再添加前缀，因为formatAnswer方法已经包含了完整内容
                answerTextView.setText(formatHighlightedText(answer), TextView.BufferType.SPANNABLE);
            }
        }
        
        // 限制悬浮窗最大尺寸，避免撑得过大
        if (windowManager != null && layoutParams != null && floatingView != null) {
            // 获取屏幕尺寸，限制悬浮窗最大为屏幕的60%
            Point screenSize = new Point();
            windowManager.getDefaultDisplay().getSize(screenSize);
            int maxWidth = (int) (screenSize.x * 0.6);
            int maxHeight = (int) (screenSize.y * 0.6);
            
            // 调整窗口大小
            if (layoutParams.width > maxWidth) {
                layoutParams.width = maxWidth;
                windowManager.updateViewLayout(floatingView, layoutParams);
            }
            if (layoutParams.height > maxHeight) {
                layoutParams.height = maxHeight;
                windowManager.updateViewLayout(floatingView, layoutParams);
            }
        }
    }
    
    /**
     * 解析带有高亮标记的文本，将正确选项显示为红色
     */
    private SpannableString formatHighlightedText(String text) {
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(text);
        
        // 查找所有[CORRECT]标签，不再使用闭标签
        int startIndex = 0;
        while (startIndex < spannableStringBuilder.length()) {
            String tempString = spannableStringBuilder.toString();
            int correctStart = tempString.indexOf("[CORRECT]", startIndex);
            if (correctStart == -1) {
                break;
            }
            
            // 计算标签结束位置
            int tagEnd = correctStart + "[CORRECT]".length();
            
            // 查找当前选项的结束位置（换行符或字符串结束）
            tempString = spannableStringBuilder.toString();
            int optionEnd = tempString.indexOf("\n", tagEnd);
            if (optionEnd == -1) {
                optionEnd = spannableStringBuilder.length();
            }
            
            // 查找选项标签的起始位置（选项编号，如A. B. 等）
            tempString = spannableStringBuilder.toString();
            int optionStart = tempString.lastIndexOf("\n", correctStart);
            if (optionStart == -1) {
                optionStart = 0; // 如果是第一行，则从字符串开始位置
            } else {
                optionStart++; // 跳过换行符
            }
            
            // 真正删除[CORRECT]标签文本，而不是仅仅设置为透明
            spannableStringBuilder.delete(correctStart, tagEnd);
            
            // 调整选项结束位置，因为我们删除了[CORRECT]标签
            optionEnd -= (tagEnd - correctStart);
            
            // 设置红色文字颜色（从选项编号开始到选项结束，包括选项编号和内容）
            spannableStringBuilder.setSpan(
                    new ForegroundColorSpan(Color.RED),
                    optionStart,
                    optionEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            
            // 为红色文字添加合适的阴影效果，增强可读性
            spannableStringBuilder.setSpan(
                    new ShadowSpan(Color.DKGRAY, 1, 1, 2f),
                    optionStart,
                    optionEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            
            // 继续查找下一个标签，由于删除了标签，需要调整startIndex
            startIndex = optionEnd;
        }
        
        return new SpannableString(spannableStringBuilder);
    }
    
    /**
     * 自定义ShadowSpan类，用于为特定文本添加阴影效果
     */
    private static class ShadowSpan extends CharacterStyle implements UpdateAppearance {
        private final int color;
        private final float dx;
        private final float dy;
        private final float radius;
        
        public ShadowSpan(int color, float dx, float dy, float radius) {
            this.color = color;
            this.dx = dx;
            this.dy = dy;
            this.radius = radius;
        }
        
        @Override
        public void updateDrawState(TextPaint tp) {
            tp.setShadowLayer(radius, dx, dy, color);
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