package com.floatingocrquiz;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;

public class ScreenSelectionView extends View {

    private Bitmap screenBitmap;
    private OnSelectionCompleteListener onSelectionCompleteListener;
    
    private Paint paint;
    private Paint textPaint;
    
    private boolean isDrawing;
    private Point startPoint;
    private Point endPoint;
    
    private int screenWidth;
    private int screenHeight;

    public interface OnSelectionCompleteListener {
        void onSelectionComplete(Bitmap selectedBitmap);
        void onSelectionError(String errorMessage);
    }

    public ScreenSelectionView(Context context, Bitmap bitmap, OnSelectionCompleteListener listener) {
        super(context);
        this.screenBitmap = bitmap;
        this.onSelectionCompleteListener = listener;
        
        if (bitmap != null) {
            this.screenWidth = bitmap.getWidth();
            this.screenHeight = bitmap.getHeight();
        } else {
            this.screenWidth = 0;
            this.screenHeight = 0;
        }
        
        initPaints();
    }

    public ScreenSelectionView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initPaints();
    }

    public ScreenSelectionView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initPaints();
    }

    private void initPaints() {
        // 选择区域画笔
        paint = new Paint();
        paint.setColor(Color.GREEN);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setAlpha(255);
        
        // 选择区域填充画笔
        Paint fillPaint = new Paint();
        fillPaint.setColor(Color.GREEN);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setAlpha(50);
        
        // 文字画笔
        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(24f);
        textPaint.setStrokeWidth(2f);
        textPaint.setShadowColor(Color.BLACK);
        textPaint.setShadowDx(2f);
        textPaint.setShadowDy(2f);
        textPaint.setShadowRadius(3f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (screenBitmap != null) {
            // 绘制屏幕截图
            canvas.drawBitmap(screenBitmap, 0, 0, null);
        }
        
        // 绘制选择区域
        if (isDrawing && startPoint != null && endPoint != null) {
            int left = Math.min(startPoint.x, endPoint.x);
            int top = Math.min(startPoint.y, endPoint.y);
            int right = Math.max(startPoint.x, endPoint.x);
            int bottom = Math.max(startPoint.y, endPoint.y);
            
            Rect rect = new Rect(left, top, right, bottom);
            
            // 创建填充画笔
            Paint fillPaint = new Paint();
            fillPaint.setColor(Color.GREEN);
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setAlpha(50);
            
            // 绘制半透明填充
            canvas.drawRect(rect, fillPaint);
            
            // 绘制边框
            canvas.drawRect(rect, paint);
            
            // 绘制选择区域大小
            String sizeText = String.format("%dx%d", right - left, bottom - top);
            canvas.drawText(sizeText, left + 10, top - 10, textPaint);
            
            // 绘制辅助线（十字线）
            Paint crossPaint = new Paint();
            crossPaint.setColor(Color.GREEN);
            crossPaint.setStyle(Paint.Style.STROKE);
            crossPaint.setStrokeWidth(1f);
            crossPaint.setAlpha(150);
            
            // 绘制水平辅助线
            canvas.drawLine(left, (top + bottom) / 2, right, (top + bottom) / 2, crossPaint);
            // 绘制垂直辅助线
            canvas.drawLine((left + right) / 2, top, (left + right) / 2, bottom, crossPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 如果没有截图，不处理触摸事件
        if (screenBitmap == null) {
            return super.onTouchEvent(event);
        }
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isDrawing = true;
                startPoint = new Point((int) event.getX(), (int) event.getY());
                endPoint = new Point((int) event.getX(), (int) event.getY());
                invalidate();
                return true;
                
            case MotionEvent.ACTION_MOVE:
                if (isDrawing) {
                    endPoint = new Point((int) event.getX(), (int) event.getY());
                    invalidate();
                }
                return true;
                
            case MotionEvent.ACTION_UP:
                isDrawing = false;
                if (startPoint != null && endPoint != null) {
                    int left = Math.min(startPoint.x, endPoint.x);
                    int top = Math.min(startPoint.y, endPoint.y);
                    int right = Math.max(startPoint.x, endPoint.x);
                    int bottom = Math.max(startPoint.y, endPoint.y);
                    
                    // 确保选择区域有效
                    int width = right - left;
                    int height = bottom - top;
                    
                    if (width > 50 && height > 50) {
                        try {
                            // 确保选择区域在截图范围内
                            left = Math.max(0, left);
                            top = Math.max(0, top);
                            right = Math.min(screenWidth, right);
                            bottom = Math.min(screenHeight, bottom);
                            
                            width = right - left;
                            height = bottom - top;
                            
                            // 再次检查调整后的区域是否有效
                            if (width > 0 && height > 0) {
                                // 创建选择区域的Bitmap
                                Bitmap selectedBitmap = Bitmap.createBitmap(
                                        screenBitmap,
                                        left, top,
                                        width, height
                                );
                                
                                // 通知选择完成
                                if (onSelectionCompleteListener != null) {
                                    onSelectionCompleteListener.onSelectionComplete(selectedBitmap);
                                }
                            } else {
                                // 选择区域太小
                                if (onSelectionCompleteListener != null) {
                                    onSelectionCompleteListener.onSelectionError("选择区域太小，请重新选择");
                                }
                            }
                        } catch (Exception e) {
                            // 处理异常
                            if (onSelectionCompleteListener != null) {
                                onSelectionCompleteListener.onSelectionError("截图处理失败，请重试");
                            }
                        }
                    } else {
                        // 选择区域太小
                        if (onSelectionCompleteListener != null) {
                            onSelectionCompleteListener.onSelectionError("选择区域太小，请重新选择");
                        }
                    }
                }
                return true;
                
            default:
                return super.onTouchEvent(event);
        }
    }
    
    /**
     * 获取选择的矩形区域
     * @return 选择的矩形区域，如果没有选择则返回null
     */
    public Rect getSelectionRect() {
        if (startPoint != null && endPoint != null) {
            int left = Math.min(startPoint.x, endPoint.x);
            int top = Math.min(startPoint.y, endPoint.y);
            int right = Math.max(startPoint.x, endPoint.x);
            int bottom = Math.max(startPoint.y, endPoint.y);
            
            // 确保选择区域在截图范围内
            left = Math.max(0, left);
            top = Math.max(0, top);
            right = Math.min(screenWidth, right);
            bottom = Math.min(screenHeight, bottom);
            
            return new Rect(left, top, right, bottom);
        }
        return null;
    }
}