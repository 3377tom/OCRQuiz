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
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

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
        if (intent != null) {
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            Intent data = intent.getParcelableExtra(EXTRA_RESULT_INTENT);

            if (resultCode != 0 && data != null) {
                // 已有权限，直接开始截图
                startCapture(resultCode, data);
            } else {
                // 请求屏幕录制权限
                requestMediaProjectionPermission();
            }
        } else {
            // 请求屏幕录制权限
            requestMediaProjectionPermission();
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
                    0x1,
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
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "处理图像失败: " + e.getMessage());
                    } finally {
                        if (image != null) {
                            image.close();
                        }
                        // 释放资源
                        releaseMediaProjection();
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
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "处理图像失败: " + e.getMessage());
                            } finally {
                                image.close();
                                releaseMediaProjection();
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
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
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
    }

    private void showScreenSelectionOverlay(Bitmap screenBitmap) {
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
        ScreenSelectionView selectionView = new ScreenSelectionView(this, screenBitmap, new ScreenSelectionView.OnSelectionCompleteListener() {
            @Override
            public void onSelectionComplete(Bitmap selectedBitmap) {
                // 处理选中的截图区域
                processSelectedRegion(selectedBitmap);
                // 移除覆盖层
                windowManager.removeView(overlayLayout);
            }
        });

        overlayLayout.addView(selectionView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // 添加覆盖层到系统
        windowManager.addView(overlayLayout, overlayParams);
    }

    private void processSelectedRegion(Bitmap selectedBitmap) {
        executorService.execute(() -> {
            OCRHelper ocrHelper = null;
            try {
                // 调用OCR进行文字识别
                ocrHelper = new OCRHelper(this);
                String recognizedText = ocrHelper.recognizeText(selectedBitmap);

                if (!recognizedText.isEmpty()) {
                    // 查询题库
                    QuestionBankHelper questionBankHelper = new QuestionBankHelper(this);
                    String answer = questionBankHelper.queryAnswer(recognizedText);

                    // 更新浮动窗口显示答案
                    Intent intent = new Intent(FloatingWindowService.ACTION_UPDATE_ANSWER);
                    intent.putExtra(FloatingWindowService.EXTRA_ANSWER, answer);
                    sendBroadcast(intent);
                }
            } catch (Exception e) {
                Log.e(TAG, "处理选中区域失败: " + e.getMessage());
            } finally {
                // 释放OCR资源
                if (ocrHelper != null) {
                    ocrHelper.release();
                }
            }
        });
    }

    private void releaseMediaProjection() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releaseMediaProjection();
        if (handlerThread != null) {
            handlerThread.quitSafely();
        }
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