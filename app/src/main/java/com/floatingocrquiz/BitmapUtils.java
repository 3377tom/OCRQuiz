package com.floatingocrquiz;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Bitmap工具类，用于处理Bitmap相关操作
 */
public class BitmapUtils {
    private static final String TAG = "BitmapUtils";
    private static final String TEMP_IMAGE_PREFIX = "ocr_temp_";
    private static final String TEMP_IMAGE_SUFFIX = ".jpg";

    /**
     * 将Bitmap保存为临时文件
     * @param bitmap 要保存的Bitmap
     * @param context 上下文
     * @return 临时文件路径，如果保存失败则返回null
     */
    public static String saveBitmapToTempFile(Bitmap bitmap, Context context) {
        if (bitmap == null) {
            Log.e(TAG, "保存失败：Bitmap为null");
            return null;
        }

        File tempFile = null;
        FileOutputStream fos = null;

        try {
            // 创建临时文件
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(new Date());
            String tempFileName = TEMP_IMAGE_PREFIX + timeStamp + TEMP_IMAGE_SUFFIX;
            
            // 使用应用的缓存目录
            File cacheDir = context.getExternalCacheDir();
            if (cacheDir == null) {
                // 如果外部缓存目录不可用，使用内部缓存目录
                cacheDir = context.getCacheDir();
            }
            
            tempFile = new File(cacheDir, tempFileName);
            
            // 保存Bitmap到文件
            fos = new FileOutputStream(tempFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.flush();
            
            Log.i(TAG, "Bitmap保存为临时文件成功：" + tempFile.getAbsolutePath());
            return tempFile.getAbsolutePath();
        } catch (IOException e) {
            Log.e(TAG, "保存Bitmap到临时文件失败：" + e.getMessage(), e);
            // 如果保存失败，删除临时文件
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
            return null;
        } finally {
            // 关闭流
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    Log.e(TAG, "关闭FileOutputStream失败：" + e.getMessage(), e);
                }
            }
        }
    }

    /**
     * 删除临时文件
     * @param filePath 文件路径
     * @return 是否删除成功
     */
    public static boolean deleteTempFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return false;
        }

        File file = new File(filePath);
        if (file.exists() && file.delete()) {
            Log.i(TAG, "临时文件删除成功：" + filePath);
            return true;
        } else {
            Log.w(TAG, "临时文件删除失败：" + filePath);
            return false;
        }
    }
}