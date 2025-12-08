package com.floatingocrquiz;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScreenCaptureService extends Service {

    private static final String TAG = "ScreenCaptureService";
    private static final int VIRTUAL_DISPLAY_FLAGS = DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR;
    private static final String VIRTUAL_DISPLAY_NAME = "ScreenCapture";
    
    // Intent extra constants
    public static final String EXTRA_RESULT_CODE = "RESULT_CODE";
    public static final String EXTRA_RESULT_INTENT = "RESULT_INTENT";
    
    // 前台服务通知常量
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "screen_capture_channel";
    private static final String CHANNEL_NAME = "屏幕捕获服务";
    
    // 默认截图范围相关常量
    private static final String PREFS_NAME = "ScreenshotPrefs";
    private static final String PREF_DEFAULT_RECT = "defaultRect";
    private static final String EXTRA_SETTING_DEFAULT_RANGE = "SETTING_DEFAULT_RANGE";
    
    // 是否正在设置默认截图范围
    private boolean isSettingDefaultRange = false;

    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private HandlerThread handlerThread;
    private Handler handler;
    private WindowManager windowManager;
    private ExecutorService executorService;

    // 覆盖层相关
    private View screenSelectionOverlay;
    
    // 自定义提示消息相关
    private View customToastView;
    private WindowManager customToastWindowManager;
    private static final long CUSTOM_TOAST_DURATION = 2000; // 2秒
    private ScreenSelectionView selectionView;

    private int screenWidth;
    private int screenHeight;
    private int screenDensity;

    @Override
    public void onCreate() {
        super.onCreate();
        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        executorService = Executors.newSingleThreadExecutor();

        // 获取屏幕参数
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;

        // 创建HandlerThread处理图像数据
        handlerThread = new HandlerThread("ImageProcessingThread");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 启动前台服务
        startForegroundService();
        
        // 检查是否是设置默认截图范围
        if (intent != null && intent.hasExtra(EXTRA_SETTING_DEFAULT_RANGE)) {
            isSettingDefaultRange = intent.getBooleanExtra(EXTRA_SETTING_DEFAULT_RANGE, false);
        }
        
        // 首先检查OCRApplication中是否有缓存的MediaProjection实例
        OCRApplication ocrApplication = (OCRApplication) getApplication();
        MediaProjection cachedMediaProjection = ocrApplication.getMediaProjection();
        
        if (cachedMediaProjection != null) {
            // 使用缓存的MediaProjection实例
            mediaProjection = cachedMediaProjection;
            startCaptureWithCachedProjection();
        } else if (intent != null) {
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            Intent data = intent.getParcelableExtra(EXTRA_RESULT_INTENT);

            if (resultCode != 0 && data != null) {
                // 开始截图
                startCapture(resultCode, data);
            } else {
                // 没有有效的权限数据，重新请求权限
                requestMediaProjectionPermission();
            }
        } else {
            // 没有有效的权限数据，重新请求权限
            requestMediaProjectionPermission();
        }

        return START_NOT_STICKY;
    }
    
    /**
     * 启动前台服务
     */
    private void startForegroundService() {
        // 创建通知渠道（Android 8.0+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("用于屏幕截图的前台服务");
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
        
        // 创建通知
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("屏幕捕获")
                .setContentText("正在准备截图")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(Notification.PRIORITY_LOW)
                .build();
        
        // 启动前台服务
        startForeground(NOTIFICATION_ID, notification);
    }

    private void requestMediaProjectionPermission() {
        Intent intent = new Intent(this, MediaProjectionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    /**
     * 使用缓存的MediaProjection实例开始截图
     */
    private void startCaptureWithCachedProjection() {
        try {
            // 创建ImageReader用于捕获屏幕截图
            imageReader = ImageReader.newInstance(
                    screenWidth,
                    screenHeight,
                    android.graphics.PixelFormat.RGBA_8888,
                    1
            );

            // 设置ImageAvailableListener
            setImageAvailableListener();
            
            // 获取OCRApplication实例，检查是否是第一次截图
            OCRApplication ocrApplication = (OCRApplication) getApplication();
            boolean isFirstCapture = ocrApplication.isFirstCapture();
            
            // 设置延时时间：第一次截图3000ms，后续200ms
            long delayTime = isFirstCapture ? 5000 : 200;
            
            // 创建虚拟显示，根据是否是第一次截图设置不同的延迟时间
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        virtualDisplay = mediaProjection.createVirtualDisplay(
                                VIRTUAL_DISPLAY_NAME,
                                screenWidth,
                                screenHeight,
                                screenDensity,
                                VIRTUAL_DISPLAY_FLAGS,
                                imageReader.getSurface(),
                                null,
                                handler
                        );
                        
                        // 将第一次截图标志设置为false
                        ocrApplication.setFirstCapture(false);
                    } catch (Exception e) {
                        Log.e(TAG, "创建虚拟显示失败: " + e.getMessage());
                        // 释放资源
                        releaseMediaProjection();
                        // 重新请求权限
                        requestMediaProjectionPermission();
                    }
                }
            }, delayTime);
        } catch (Exception e) {
            Log.e(TAG, "使用缓存MediaProjection截图失败: " + e.getMessage());
            // 释放资源
            releaseMediaProjection();
            // 重新请求权限
            requestMediaProjectionPermission();
        }
    }
    
    /**
     * 设置ImageAvailableListener
     */
    private void setImageAvailableListener() {
        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    if (image != null) {
                        Bitmap bitmap = imageToBitmap(image);
                        
                        // 检查是否有默认截图范围且不处于设置模式
                        Rect defaultRect = loadDefaultRect();
                        if (defaultRect != null && !isSettingDefaultRange) {
                            // 使用默认截图范围
                            try {
                                Bitmap selectedBitmap = Bitmap.createBitmap(
                                        bitmap,
                                        defaultRect.left,
                                        defaultRect.top,
                                        defaultRect.width(),
                                        defaultRect.height()
                                );
                                processSelectedRegion(selectedBitmap);
                                releaseVirtualDisplay();
                                return;
                            } catch (Exception e) {
                                Log.e(TAG, "使用默认截图范围失败: " + e.getMessage());
                                // 默认范围无效，继续处理
                            }
                        }
                        
                        // 截图识别模式：直接截取全屏，不显示选择框
                        if (!isSettingDefaultRange) {
                            processSelectedRegion(bitmap);
                            releaseVirtualDisplay();
                            return;
                        }
                        
                        // 设置默认范围模式：显示屏幕选择覆盖层
                        showScreenSelectionOverlay(bitmap);
                        
                        // 发送广播通知浮动窗口服务，截图已完成，清除"正在截图"提示
                        Intent intent = new Intent(FloatingWindowService.ACTION_UPDATE_ANSWER);
                        intent.putExtra(FloatingWindowService.EXTRA_ANSWER, "");
                        sendBroadcast(intent);
                        
                        // 停止虚拟显示（只需要一次截图）
                        releaseVirtualDisplay();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "获取或处理截图失败: " + e.getMessage());
                    stopSelf();
                } finally {
                    if (image != null) {
                        image.close();
                    }
                }
            }
        }, handler);
    }
    
    public void startCapture(int resultCode, Intent data) {
        try {
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
            
            // 将MediaProjection实例保存到OCRApplication中
            OCRApplication ocrApplication = (OCRApplication) getApplication();
            ocrApplication.setMediaProjection(mediaProjection);

            // 创建ImageReader用于捕获屏幕截图
            imageReader = ImageReader.newInstance(
                    screenWidth,
                    screenHeight,
                    android.graphics.PixelFormat.RGBA_8888,
                    1
            );

            // 设置ImageAvailableListener
            setImageAvailableListener();

            // 获取OCRApplication实例，检查是否是第一次截图
            boolean isFirstCapture = ocrApplication.isFirstCapture();
            
            // 设置延时时间：第一次截图3000ms，后续200ms
            long delayTime = isFirstCapture ? 3000 : 200;

            // 创建虚拟显示，根据是否是第一次截图设置不同的延迟时间
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        virtualDisplay = mediaProjection.createVirtualDisplay(
                                VIRTUAL_DISPLAY_NAME,
                                screenWidth,
                                screenHeight,
                                screenDensity,
                                VIRTUAL_DISPLAY_FLAGS,
                                imageReader.getSurface(),
                                null,
                                handler
                        );

                        // 添加超时处理，防止截图流程无限等待
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (virtualDisplay != null && imageReader != null) {
                                    Log.e(TAG, "截图超时，释放资源");
                                    releaseVirtualDisplay();
                                }
                            }
                        }, 3000); // 3秒超时
                        
                        // 将第一次截图标志设置为false
                        ocrApplication.setFirstCapture(false);
                    } catch (Exception e) {
                        Log.e(TAG, "创建虚拟显示失败: " + e.getMessage());
                        releaseMediaProjection();
                        stopSelf();
                    }
                }
            }, delayTime);

        } catch (Exception e) {
            Log.e(TAG, "初始化屏幕捕获失败: " + e.getMessage());
            releaseMediaProjection();
            stopSelf();
        }
    }

    private Bitmap imageToBitmap(Image image) {
        try {
            Image.Plane[] planes = image.getPlanes();
            if (planes == null || planes.length == 0) {
                Log.e(TAG, "图像平面为空");
                return null;
            }

            Image.Plane plane = planes[0];
            ByteBuffer buffer = plane.getBuffer();
            if (buffer == null) {
                Log.e(TAG, "图像缓冲区为空");
                return null;
            }

            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int rowPadding = rowStride - pixelStride * image.getWidth();

            // 创建Bitmap
            Bitmap bitmap = Bitmap.createBitmap(
                    image.getWidth() + rowPadding / pixelStride,
                    image.getHeight(),
                    Bitmap.Config.ARGB_8888
            );
            bitmap.copyPixelsFromBuffer(buffer);

            // 裁剪到实际屏幕大小
            bitmap = Bitmap.createBitmap(
                    bitmap,
                    0, 0,
                    image.getWidth(),
                    image.getHeight()
            );

            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "将图像转换为Bitmap失败: " + e.getMessage());
            return null;
        }
    }

    private void showScreenSelectionOverlay(Bitmap screenBitmap) {
        if (screenBitmap == null) {
            Log.e(TAG, "屏幕Bitmap为空");
            return;
        }

        // 检查是否有悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                // 如果没有悬浮窗权限，引导用户开启
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                showCustomToast("请开启悬浮窗权限以使用截图功能");
                return;
            }
        }

        try {
            // 先移除旧的覆盖层（如果存在）
            removeScreenSelectionOverlay();

            // 创建全屏覆盖层
            WindowManager.LayoutParams overlayParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                            WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                            WindowManager.LayoutParams.FLAG_FULLSCREEN |
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    android.graphics.PixelFormat.TRANSLUCENT
            );

            overlayParams.gravity = Gravity.TOP | Gravity.START;

            // 创建覆盖层布局
            FrameLayout overlayLayout = new FrameLayout(this);
            overlayLayout.setBackgroundColor(Color.parseColor("#80000000"));

            // 添加屏幕选择视图
            selectionView = new ScreenSelectionView(this, screenBitmap, new ScreenSelectionView.OnSelectionCompleteListener() {
                @Override
                public void onSelectionComplete(Bitmap selectedBitmap) {
                    try {
                        if (isSettingDefaultRange) {
                            // 保存默认截图范围
                            Rect selectedRect = selectionView.getSelectionRect();
                            if (selectedRect != null) {
                                saveDefaultRect(selectedRect);
                                
                                // 通知浮动窗口设置成功
                                Intent intent = new Intent(FloatingWindowService.ACTION_UPDATE_ANSWER);
                                intent.putExtra(FloatingWindowService.EXTRA_ANSWER, "默认截图范围已设置");
                                sendBroadcast(intent);
                                
                                isSettingDefaultRange = false;
                            } else {
                                // 通知浮动窗口设置失败
                                Intent intent = new Intent(FloatingWindowService.ACTION_UPDATE_ANSWER);
                                intent.putExtra(FloatingWindowService.EXTRA_ANSWER, "默认截图范围设置失败");
                                sendBroadcast(intent);
                            }
                        } else {
                            // 正常处理选中的截图区域
                            processSelectedRegion(selectedBitmap);
                        }
                    } catch (Exception e) {
                Log.e(TAG, "处理选择区域失败: " + e.getMessage());
                showCustomToast("截图处理失败，请重试");
            } finally {
                        // 移除覆盖层
                        removeScreenSelectionOverlay();
                    }
                }

                @Override
                public void onSelectionError(String errorMessage) {
                    showCustomToast(errorMessage);
                }
            });

            overlayLayout.addView(selectionView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));

            // 添加覆盖层到系统
            windowManager.addView(overlayLayout, overlayParams);
            screenSelectionOverlay = overlayLayout;
        } catch (Exception e) {
            Log.e(TAG, "显示屏幕选择覆盖层失败: " + e.getMessage());
            showCustomToast("截图功能启动失败，请重试");
            // 确保资源被释放
            if (screenBitmap != null && !screenBitmap.isRecycled()) {
                screenBitmap.recycle();
            }
        }
    }

    private void processSelectedRegion(Bitmap selectedBitmap) {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "开始处理选中区域，Bitmap尺寸: " + selectedBitmap.getWidth() + "x" + selectedBitmap.getHeight());
                
                // 调用OCR进行文字识别（使用单例模式）
                OCRHelper ocrHelper = OCRHelper.getInstance(this);
                String recognizedText = ocrHelper.recognizeText(selectedBitmap);

                String displayText;
                if (!recognizedText.isEmpty()) {
                    // 显示识别到的文字（调试用）
                    Log.d(TAG, "OCR识别到的文字: " + recognizedText);
                    
                    // 检查识别结果是否包含错误信息
                    if (recognizedText.startsWith("[ERROR]")) {
                        // OCR识别失败
                        Log.e(TAG, "OCR识别失败: " + recognizedText);
                        displayText = "处理图片失败，请重新截图";
                    } else {
                        // 使用QuestionBankHelper查询题库
                        QuestionBankHelper questionBankHelper = QuestionBankHelper.getInstance(this);
                        Log.d(TAG, "开始查询题库");
                        String answer = questionBankHelper.queryAnswer(recognizedText);
                        Log.d(TAG, "题库查询结果: " + answer);
                        
                        // 组合显示识别结果和答案（调试用）
                        displayText = "识别到的文字:\n" + recognizedText + "\n\n" + answer;
                    }
                } else {
                    // OCR识别失败或没有识别到文字
                    Log.e(TAG, "OCR识别失败或没有识别到文字");
                    displayText = "无法识别文字，请重新截图";
                }

                // 更新浮动窗口显示识别结果
                Log.d(TAG, "准备更新浮动窗口，显示内容: " + displayText);
                Intent intent = new Intent(FloatingWindowService.ACTION_UPDATE_ANSWER);
                intent.putExtra(FloatingWindowService.EXTRA_ANSWER, displayText);
                sendBroadcast(intent);
                Log.d(TAG, "浮动窗口更新广播已发送");
            } catch (Exception e) {
                Log.e(TAG, "处理选中区域失败: " + e.getMessage());
                Log.e(TAG, "异常堆栈: " + android.util.Log.getStackTraceString(e));
                // 发送错误信息
                Intent intent = new Intent(FloatingWindowService.ACTION_UPDATE_ANSWER);
                intent.putExtra(FloatingWindowService.EXTRA_ANSWER, "处理图片失败，请重新截图");
                sendBroadcast(intent);
            } finally {
                // 释放Bitmap资源
                if (selectedBitmap != null && !selectedBitmap.isRecycled()) {
                    selectedBitmap.recycle();
                    Log.d(TAG, "Bitmap资源已释放");
                }
            }
        });
    }

    private void releaseVirtualDisplay() {
        try {
            if (virtualDisplay != null) {
                virtualDisplay.release();
                virtualDisplay = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "释放虚拟显示失败: " + e.getMessage());
        }
        try {
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "释放ImageReader失败: " + e.getMessage());
        }
    }
    
    /**
     * 显示自定义提示消息
     * @param message 提示文本
     */
    private void showCustomToast(String message) {
        // 如果在后台或没有悬浮窗权限，不显示提示
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            return;
        }
        
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                // 移除旧的提示
                hideCustomToast();
                
                // 初始化WindowManager
                if (customToastWindowManager == null) {
                    customToastWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
                }
                
                // 创建提示视图
                if (customToastView == null) {
                    LayoutInflater inflater = LayoutInflater.from(this);
                    customToastView = inflater.inflate(R.layout.custom_toast, null);
                }
                
                // 设置提示文本
                TextView textView = customToastView.findViewById(R.id.toast_text);
                textView.setText(message);
                
                // 创建WindowManager参数
                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                                WindowManager.LayoutParams.TYPE_PHONE,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        android.graphics.PixelFormat.TRANSLUCENT
                );
                
                // 设置位置为屏幕底部中央
                params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                params.y = 100; // 距离底部的距离
                
                // 添加视图到WindowManager
                customToastWindowManager.addView(customToastView, params);
                
                // 定时移除提示
                new Handler().postDelayed(this::hideCustomToast, CUSTOM_TOAST_DURATION);
            } catch (Exception e) {
                Log.e(TAG, "显示自定义提示失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * 隐藏自定义提示消息
     */
    private void hideCustomToast() {
        if (customToastView != null && customToastWindowManager != null) {
            try {
                customToastWindowManager.removeView(customToastView);
            } catch (Exception e) {
                Log.e(TAG, "隐藏自定义提示失败: " + e.getMessage());
            }
            customToastView = null;
        }
    }


    
    private void releaseMediaProjection() {
        releaseVirtualDisplay();

        try {
            if (mediaProjection != null) {
                mediaProjection.stop();
                mediaProjection = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "停止媒体投影失败: " + e.getMessage());
        }
    }
    
    /**
     * 保存默认截图范围到SharedPreferences
     */
    private void saveDefaultRect(Rect rect) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        String rectString = rect.left + "," + rect.top + "," + rect.right + "," + rect.bottom;
        editor.putString(PREF_DEFAULT_RECT, rectString);
        editor.apply();
        Log.i(TAG, "默认截图范围已保存: " + rectString);
    }
    
    /**
     * 从SharedPreferences加载默认截图范围
     */
    private Rect loadDefaultRect() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String rectString = prefs.getString(PREF_DEFAULT_RECT, null);
        if (rectString != null) {
            String[] parts = rectString.split(",");
            if (parts.length == 4) {
                try {
                    int left = Integer.parseInt(parts[0]);
                    int top = Integer.parseInt(parts[1]);
                    int right = Integer.parseInt(parts[2]);
                    int bottom = Integer.parseInt(parts[3]);
                    return new Rect(left, top, right, bottom);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "解析默认截图范围失败: " + e.getMessage());
                }
            }
        }
        return null;
    }

    private void removeScreenSelectionOverlay() {
        try {
            if (screenSelectionOverlay != null) {
                windowManager.removeView(screenSelectionOverlay);
                screenSelectionOverlay = null;
                Log.i(TAG, "屏幕选择覆盖层已移除");
            }
            if (selectionView != null) {
                selectionView = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "移除屏幕选择覆盖层失败: " + e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        
        // 停止前台服务
        stopForeground(true);
        
        // 移除覆盖层
        removeScreenSelectionOverlay();
        
        // 释放资源
        releaseMediaProjection();
        
        // 停止线程
        if (handlerThread != null) {
            handlerThread.quitSafely();
        }
        
        // 关闭线程池
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}