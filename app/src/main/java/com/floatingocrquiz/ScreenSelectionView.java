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

    private static final int HANDLE_SIZE = 20;
    private static final int EDGE_TOLERANCE = 30;
    
    private Bitmap screenBitmap;
    private OnSelectionCompleteListener onSelectionCompleteListener;
    
    private Paint paint;
    private Paint textPaint;
    private Paint handlePaint;
    
    private boolean isDrawing;
    private boolean isMoving;
    private boolean isResizing;
    private ResizeDirection resizeDirection;
    
    private Point startPoint;
    private Point endPoint;
    private Point lastTouchPoint;
    
    private int screenWidth;
    private int screenHeight;
    
    // 调整大小的方向
    private enum ResizeDirection {
        NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
        LEFT, RIGHT, TOP, BOTTOM
    }

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
        // 选择区域边框画笔
        paint = new Paint();
        paint.setColor(Color.GREEN);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setAlpha(255);
        
        // 文字画笔
        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(24f);
        textPaint.setStrokeWidth(2f);
        textPaint.setShadowLayer(3f, 2f, 2f, Color.BLACK);
        
        // 调整大小控制点画笔
        handlePaint = new Paint();
        handlePaint.setColor(Color.WHITE);
        handlePaint.setStyle(Paint.Style.STROKE);
        handlePaint.setStrokeWidth(2f);
        handlePaint.setAlpha(255);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (screenBitmap != null) {
            // 绘制屏幕截图
            canvas.drawBitmap(screenBitmap, 0, 0, null);
        }
        
        // 绘制选择区域（始终显示，无论是否正在绘制）
        if (startPoint != null && endPoint != null) {
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
            
            // 绘制调整大小的控制点
            drawResizeHandles(canvas, rect);
        }
    }
    
    /**
     * 绘制调整大小的控制点
     */
    private void drawResizeHandles(Canvas canvas, Rect rect) {
        // 绘制八个控制点
        drawHandle(canvas, rect.left - HANDLE_SIZE / 2, rect.top - HANDLE_SIZE / 2); // 左上角
        drawHandle(canvas, rect.right - HANDLE_SIZE / 2, rect.top - HANDLE_SIZE / 2); // 右上角
        drawHandle(canvas, rect.left - HANDLE_SIZE / 2, rect.bottom - HANDLE_SIZE / 2); // 左下角
        drawHandle(canvas, rect.right - HANDLE_SIZE / 2, rect.bottom - HANDLE_SIZE / 2); // 右下角
        drawHandle(canvas, rect.left - HANDLE_SIZE / 2, (rect.top + rect.bottom) / 2 - HANDLE_SIZE / 2); // 左边
        drawHandle(canvas, rect.right - HANDLE_SIZE / 2, (rect.top + rect.bottom) / 2 - HANDLE_SIZE / 2); // 右边
        drawHandle(canvas, (rect.left + rect.right) / 2 - HANDLE_SIZE / 2, rect.top - HANDLE_SIZE / 2); // 上边
        drawHandle(canvas, (rect.left + rect.right) / 2 - HANDLE_SIZE / 2, rect.bottom - HANDLE_SIZE / 2); // 下边
    }
    
    /**
     * 绘制单个控制点
     */
    private void drawHandle(Canvas canvas, float x, float y) {
        RectF handleRect = new RectF(x, y, x + HANDLE_SIZE, y + HANDLE_SIZE);
        canvas.drawRect(handleRect, handlePaint);
    }
    
    /**
     * 检查触摸点是否在选择区域内
     */
    private boolean isPointInSelection(Point point) {
        if (startPoint == null || endPoint == null) {
            return false;
        }
        
        int left = Math.min(startPoint.x, endPoint.x);
        int top = Math.min(startPoint.y, endPoint.y);
        int right = Math.max(startPoint.x, endPoint.x);
        int bottom = Math.max(startPoint.y, endPoint.y);
        
        return point.x >= left && point.x <= right && point.y >= top && point.y <= bottom;
    }
    
    /**
     * 获取调整大小的方向
     */
    private ResizeDirection getResizeDirection(Point point) {
        if (startPoint == null || endPoint == null) {
            return ResizeDirection.NONE;
        }
        
        int left = Math.min(startPoint.x, endPoint.x);
        int top = Math.min(startPoint.y, endPoint.y);
        int right = Math.max(startPoint.x, endPoint.x);
        int bottom = Math.max(startPoint.y, endPoint.y);
        
        // 检查是否在边角
        if (isPointNearPoint(point, new Point(left, top))) {
            return ResizeDirection.TOP_LEFT;
        } else if (isPointNearPoint(point, new Point(right, top))) {
            return ResizeDirection.TOP_RIGHT;
        } else if (isPointNearPoint(point, new Point(left, bottom))) {
            return ResizeDirection.BOTTOM_LEFT;
        } else if (isPointNearPoint(point, new Point(right, bottom))) {
            return ResizeDirection.BOTTOM_RIGHT;
        } 
        // 检查是否在边缘
        else if (isPointNearEdge(point, left, top, right, bottom)) {
            if (Math.abs(point.x - left) < EDGE_TOLERANCE) {
                return ResizeDirection.LEFT;
            } else if (Math.abs(point.x - right) < EDGE_TOLERANCE) {
                return ResizeDirection.RIGHT;
            } else if (Math.abs(point.y - top) < EDGE_TOLERANCE) {
                return ResizeDirection.TOP;
            } else if (Math.abs(point.y - bottom) < EDGE_TOLERANCE) {
                return ResizeDirection.BOTTOM;
            }
        }
        
        return ResizeDirection.NONE;
    }
    
    /**
     * 检查点是否靠近另一个点
     */
    private boolean isPointNearPoint(Point point, Point target) {
        return Math.abs(point.x - target.x) < EDGE_TOLERANCE && Math.abs(point.y - target.y) < EDGE_TOLERANCE;
    }
    
    /**
     * 检查点是否靠近选择区域的边缘
     */
    private boolean isPointNearEdge(Point point, int left, int top, int right, int bottom) {
        return (Math.abs(point.x - left) < EDGE_TOLERANCE || Math.abs(point.x - right) < EDGE_TOLERANCE ||
                Math.abs(point.y - top) < EDGE_TOLERANCE || Math.abs(point.y - bottom) < EDGE_TOLERANCE) &&
                point.x >= left - EDGE_TOLERANCE && point.x <= right + EDGE_TOLERANCE &&
                point.y >= top - EDGE_TOLERANCE && point.y <= bottom + EDGE_TOLERANCE;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 如果没有截图，不处理触摸事件
        if (screenBitmap == null) {
            return super.onTouchEvent(event);
        }
        
        Point touchPoint = new Point((int) event.getX(), (int) event.getY());
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (startPoint == null || endPoint == null) {
                    // 首次绘制
                    isDrawing = true;
                    startPoint = new Point(touchPoint);
                    endPoint = new Point(touchPoint);
                } else {
                    // 检查是否在选择区域内（移动）
                    if (isPointInSelection(touchPoint)) {
                        isMoving = true;
                        lastTouchPoint = new Point(touchPoint);
                    } 
                    // 检查是否在边缘（调整大小）
                    else {
                        resizeDirection = getResizeDirection(touchPoint);
                        if (resizeDirection != ResizeDirection.NONE) {
                            isResizing = true;
                            lastTouchPoint = new Point(touchPoint);
                        } else {
                            // 开始新的绘制
                            isDrawing = true;
                            startPoint = new Point(touchPoint);
                            endPoint = new Point(touchPoint);
                        }
                    }
                }
                invalidate();
                return true;
                
            case MotionEvent.ACTION_MOVE:
                if (isDrawing) {
                    // 正在绘制新的选择区域
                    endPoint = new Point(touchPoint);
                } else if (isMoving) {
                    // 正在移动选择区域
                    int dx = touchPoint.x - lastTouchPoint.x;
                    int dy = touchPoint.y - lastTouchPoint.y;
                    
                    // 移动选择区域
                    int left = Math.min(startPoint.x, endPoint.x);
                    int top = Math.min(startPoint.y, endPoint.y);
                    int right = Math.max(startPoint.x, endPoint.x);
                    int bottom = Math.max(startPoint.y, endPoint.y);
                    
                    // 计算新位置
                    left += dx;
                    top += dy;
                    right += dx;
                    bottom += dy;
                    
                    // 确保在屏幕范围内
                    if (left >= 0 && top >= 0 && right <= screenWidth && bottom <= screenHeight) {
                        startPoint = new Point(left, top);
                        endPoint = new Point(right, bottom);
                        lastTouchPoint = new Point(touchPoint);
                    }
                } else if (isResizing) {
                    // 正在调整选择区域大小
                    int dx = touchPoint.x - lastTouchPoint.x;
                    int dy = touchPoint.y - lastTouchPoint.y;
                    
                    int left = Math.min(startPoint.x, endPoint.x);
                    int top = Math.min(startPoint.y, endPoint.y);
                    int right = Math.max(startPoint.x, endPoint.x);
                    int bottom = Math.max(startPoint.y, endPoint.y);
                    
                    // 根据调整方向修改选择区域
                    switch (resizeDirection) {
                        case TOP_LEFT:
                            left += dx;
                            top += dy;
                            break;
                        case TOP_RIGHT:
                            right += dx;
                            top += dy;
                            break;
                        case BOTTOM_LEFT:
                            left += dx;
                            bottom += dy;
                            break;
                        case BOTTOM_RIGHT:
                            right += dx;
                            bottom += dy;
                            break;
                        case LEFT:
                            left += dx;
                            break;
                        case RIGHT:
                            right += dx;
                            break;
                        case TOP:
                            top += dy;
                            break;
                        case BOTTOM:
                            bottom += dy;
                            break;
                    }
                    
                    // 确保区域有效且在屏幕范围内
                    if (left >= 0 && top >= 0 && right <= screenWidth && bottom <= screenHeight &&
                            right - left > 50 && bottom - top > 50) {
                        startPoint = new Point(left, top);
                        endPoint = new Point(right, bottom);
                        lastTouchPoint = new Point(touchPoint);
                    }
                }
                invalidate();
                return true;
                
            case MotionEvent.ACTION_UP:
                isDrawing = false;
                isMoving = false;
                isResizing = false;
                resizeDirection = ResizeDirection.NONE;
                
                // 只有在完成绘制时才处理选择
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
                    } 
                    // 不处理太小的区域，保持显示以便用户调整
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