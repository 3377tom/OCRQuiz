package com.floatingocrquiz;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
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
    private ScreenSelectionView selectionView;

    private int screenWidth;
    private int screenHeight;
    private int screenDensity;

    // 保存媒体投影权限结果
    private int savedResultCode = 0;
    private Intent savedResultData = null;

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
        if (intent != null) {
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            Intent data = intent.getParcelableExtra(EXTRA_RESULT_INTENT);

            if (resultCode != 0 && data != null) {
                // 保存权限结果
                savedResultCode = resultCode;
                savedResultData = data;
                // 开始截图
                startCapture(resultCode, data);
            } else {
                // 检查是否已有保存的权限
                if (savedResultCode != 0 && savedResultData != null) {
                    // 使用已保存的权限开始截图
                    startCapture(savedResultCode, savedResultData);
                } else {
                    // 请求屏幕录制权限
                    requestMediaProjectionPermission();
                }
            }
        } else {
            // 检查是否已有保存的权限
            if (savedResultCode != 0 && savedResultData != null) {
                // 使用已保存的权限开始截图
                startCapture(savedResultCode, savedResultData);
            } else {
                // 请求屏幕录制权限
                requestMediaProjectionPermission();
            }
        }

        return START_NOT_STICKY;
    }

    private void requestMediaProjectionPermission() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setClass(this, MediaProjectionActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    public void startCapture(int resultCode, Intent data) {
        try {
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);

            // 创建ImageReader用于捕获屏幕截图
            imageReader = ImageReader.newInstance(
                    screenWidth,
                    screenHeight,
                    android.graphics.PixelFormat.RGBA_8888,
                    1
            );

            // 创建虚拟显示
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

            // 设置ImageAvailableListener
            imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = null;
                    try {
                        image = reader.acquireLatestImage();
                        if (image != null) {
                            Bitmap bitmap = imageToBitmap(image);
                            if (bitmap != null) {
                                showScreenSelectionOverlay(bitmap);
                            } else {
                                Log.e(TAG, "图像转换为Bitmap失败");
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "处理图像失败: " + e.getMessage());
                    } finally {
                        if (image != null) {
                            image.close();
                        }
                        // 只释放虚拟显示和ImageReader，保留媒体投影
                        releaseVirtualDisplay();
                    }
                }
            }, handler);

            // 延迟一下确保截图完成
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (imageReader != null) {
                        Image image = imageReader.acquireLatestImage();
                        if (image != null) {
                            try {
                                Bitmap bitmap = imageToBitmap(image);
                                if (bitmap != null) {
                                    showScreenSelectionOverlay(bitmap);
                                } else {
                                    Log.e(TAG, "图像转换为Bitmap失败");
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "处理图像失败: " + e.getMessage());
                            } finally {
                                image.close();
                                // 只释放虚拟显示和ImageReader，保留媒体投影
                                releaseVirtualDisplay();
                            }
                        }
                    }
                }
            }, 100);

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
                Toast.makeText(this, "请开启悬浮窗权限以使用截图功能", Toast.LENGTH_SHORT).show();
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
                        // 处理选中的截图区域
                        processSelectedRegion(selectedBitmap);
                    } catch (Exception e) {
                        Log.e(TAG, "处理选择区域失败: " + e.getMessage());
                        Toast.makeText(ScreenCaptureService.this, "截图处理失败，请重试", Toast.LENGTH_SHORT).show();
                    } finally {
                        // 移除覆盖层
                        removeScreenSelectionOverlay();
                    }
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
            Toast.makeText(this, "截图功能启动失败，请重试", Toast.LENGTH_SHORT).show();
            // 确保资源被释放
            if (screenBitmap != null && !screenBitmap.isRecycled()) {
                screenBitmap.recycle();
            }
        }
    }

    private void processSelectedRegion(Bitmap selectedBitmap) {
        executorService.execute(() -> {
            try {
                // 调用OCR进行文字识别（使用单例模式）
                OCRHelper ocrHelper = OCRHelper.getInstance(this);
                String recognizedText = ocrHelper.recognizeText(selectedBitmap);

                String displayText;
                if (!recognizedText.isEmpty()) {
                    // 直接使用OCR识别的原始文字，不查询题库
                    displayText = "百度OCR识别结果：\n" + recognizedText;
                } else {
                    // OCR识别失败或没有识别到文字
                    displayText = "无法识别文字，请重新截图";
                }

                // 更新浮动窗口显示识别结果
                Intent intent = new Intent(FloatingWindowService.ACTION_UPDATE_ANSWER);
                intent.putExtra(FloatingWindowService.EXTRA_ANSWER, displayText);
                sendBroadcast(intent);
            } catch (Exception e) {
                Log.e(TAG, "处理选中区域失败: " + e.getMessage());
                // 发送错误信息
                Intent intent = new Intent(FloatingWindowService.ACTION_UPDATE_ANSWER);
                intent.putExtra(FloatingWindowService.EXTRA_ANSWER, "处理图片失败，请重新截图");
                sendBroadcast(intent);
            } finally {
                // 释放Bitmap资源
                if (selectedBitmap != null && !selectedBitmap.isRecycled()) {
                    selectedBitmap.recycle();
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

    private void releaseMediaProjection() {
        releaseVirtualDisplay();

        try {
            if (mediaProjection != null) {
                mediaProjection.stop();
                mediaProjection = null;
                // 清除保存的权限
                savedResultCode = 0;
                savedResultData = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "停止媒体投影失败: " + e.getMessage());
        }
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