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
    }

    public ScreenSelectionView(Context context, Bitmap bitmap, OnSelectionCompleteListener listener) {
        super(context);
        this.screenBitmap = bitmap;
        this.onSelectionCompleteListener = listener;
        
        this.screenWidth = bitmap.getWidth();
        this.screenHeight = bitmap.getHeight();
        
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
        paint.setAlpha(200);
        
        // 文字画笔
        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(24f);
        textPaint.setStrokeWidth(2f);
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
            canvas.drawRect(rect, paint);
            
            // 绘制选择区域大小
            String sizeText = String.format("%dx%d", right - left, bottom - top);
            canvas.drawText(sizeText, left + 10, top - 10, textPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
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
                        Toast.makeText(getContext(), "选择区域太小，请重新选择", Toast.LENGTH_SHORT).show();
                    }
                }
                return true;
                
            default:
                return super.onTouchEvent(event);
        }
    }
}