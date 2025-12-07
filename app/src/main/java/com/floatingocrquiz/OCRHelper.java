package com.floatingocrquiz;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.baidu.ocr.sdk.OCR;
import com.baidu.ocr.sdk.OnResultListener;
import com.baidu.ocr.sdk.exception.OCRError;
import com.baidu.ocr.sdk.model.AccessToken;
import com.baidu.ocr.sdk.model.GeneralBasicParams;
import com.baidu.ocr.sdk.model.GeneralResult;
import com.baidu.ocr.sdk.model.WordSimple;

import java.io.File;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class OCRHelper {

    private static final String TAG = "OCRHelper";
    private static OCRHelper instance;
    private Context context;
    private boolean isInitialized = false;
    
    /**
     * 私有构造函数，防止外部实例化
     * @param context 上下文
     */
    private OCRHelper(Context context) {
        this.context = context.getApplicationContext();
        
        // 初始化百度OCR SDK
        initBaiduOCR();
    }
    
    /**
     * 获取OCRHelper实例（单例模式）
     * @param context 上下文
     * @return OCRHelper实例
     */
    public static synchronized OCRHelper getInstance(Context context) {
        if (instance == null) {
            instance = new OCRHelper(context);
        }
        return instance;
    }
    
    /**
     * 初始化百度OCR SDK
     * 使用License文件进行授权，避免直接暴露API Key/Secret Key
     */
    private void initBaiduOCR() {
        // 使用自定义License文件名：aip-ocr.license
        // 确保该文件已放置在assets目录下
        OCR.getInstance(context).initAccessToken(new OnResultListener<AccessToken>() {
            @Override
            public void onResult(AccessToken accessToken) {
                // 初始化成功，accessToken会自动管理，无需手动维护
                Log.d(TAG, "百度OCR SDK初始化成功");
                isInitialized = true;
            }
            
            @Override
            public void onError(OCRError error) {
                // 初始化失败
                Log.e(TAG, "百度OCR SDK初始化失败: " + error.getMessage());
                Log.e(TAG, "错误码: " + error.getErrorCode());
                isInitialized = false;
            }
        }, "aip-ocr.license", context);
    }
    
    /**
     * 识别图片中的文字（同步方法）
     * @param bitmap 图片
     * @return 识别结果
     */
    public String recognizeText(Bitmap bitmap) {
        String tempFilePath = null;
        try {
            Log.d(TAG, "开始OCR识别，Bitmap尺寸: " + bitmap.getWidth() + "x" + bitmap.getHeight());
            
            if (!isInitialized) {
                Log.e(TAG, "OCR SDK尚未初始化完成");
                return "[ERROR] OCR SDK尚未初始化完成";
            }
            
            // 构建通用文字识别参数
            GeneralBasicParams params = new GeneralBasicParams();
            params.setDetectDirection(true);
            // LANGUAGE_TYPE_CHN_ENG常量不存在，直接使用字符串值
            params.setLanguageType("CHN_ENG");
            
            // 先将Bitmap保存为临时文件
            tempFilePath = BitmapUtils.saveBitmapToTempFile(bitmap, context);
            if (tempFilePath != null) {
                Log.d(TAG, "临时文件保存成功: " + tempFilePath);
                // 将String转换为File对象
                File imageFile = new File(tempFilePath);
                params.setImageFile(imageFile);
            } else {
                Log.e(TAG, "无法保存临时文件");
                return "[ERROR] 无法保存临时文件";
            }
            
            // 移除同步调用，使用异步方法
            final String[] resultText = {""};
            final CountDownLatch latch = new CountDownLatch(1);
            
            // 异步调用识别接口
            OCR.getInstance(context).recognizeGeneralBasic(params, new OnResultListener<GeneralResult>() {
                @Override
                public void onResult(GeneralResult result) {
                    if (result != null) {
                        Log.d(TAG, "OCR识别成功，结果不为null");
                        
                        if (result.getWordList() != null) {
                            Log.d(TAG, "识别到的单词数量: " + result.getWordList().size());
                            resultText[0] = formatResult(result);
                            Log.d(TAG, "格式化后的识别结果: " + resultText[0]);
                        } else {
                            Log.e(TAG, "OCR识别结果的WordList为空");
                            resultText[0] = "[ERROR] 识别结果为空";
                        }
                    } else {
                        Log.e(TAG, "OCR识别结果为空");
                        resultText[0] = "[ERROR] 识别结果为空";
                    }
                    latch.countDown();
                }
                
                @Override
                public void onError(OCRError error) {
                    Log.e(TAG, "OCR识别失败: " + error.getMessage());
                    Log.e(TAG, "错误码: " + error.getErrorCode());
                    Log.e(TAG, "错误详细信息: " + error.toString());
                    resultText[0] = "[ERROR] 识别失败: " + error.getMessage();
                    latch.countDown();
                }
            });
            
            // 等待异步调用完成
            boolean waitResult = latch.await(10, TimeUnit.SECONDS);
            if (!waitResult) {
                Log.e(TAG, "OCR识别超时");
                return "[ERROR] 识别超时";
            }
            
            Log.d(TAG, "OCR识别完成，返回结果长度: " + resultText[0].length());
            Log.d(TAG, "返回的识别结果: " + resultText[0]);
            return resultText[0];
        } catch (Exception e) {
            Log.e(TAG, "OCR识别失败: " + e.getMessage());
            Log.e(TAG, "异常堆栈: " + android.util.Log.getStackTraceString(e));
            return "[ERROR] 识别失败: " + e.getMessage();
        } finally {
            // 识别完成后删除临时文件
            if (tempFilePath != null) {
                Log.d(TAG, "准备删除临时文件: " + tempFilePath);
                BitmapUtils.deleteTempFile(tempFilePath);
                Log.d(TAG, "临时文件删除成功");
            }
        }
    }
    
    /**
     * 识别图片中的文字（异步方法）
     * @param bitmap 图片
     * @param callback 回调
     */
    public void recognizeTextAsync(Bitmap bitmap, final OcrCallback callback) {
        try {
            // 构建通用文字识别参数
            GeneralBasicParams params = new GeneralBasicParams();
            params.setDetectDirection(true);
            // LANGUAGE_TYPE_CHN_ENG常量不存在，直接使用字符串值
            params.setLanguageType("CHN_ENG");
            // 移除setRecognizeGranularity方法，该方法不存在
            // 先将Bitmap保存为临时文件
            String tempFilePath = BitmapUtils.saveBitmapToTempFile(bitmap, context);
            if (tempFilePath != null) {
                // 将String转换为File对象
                File imageFile = new File(tempFilePath);
                params.setImageFile(imageFile);
            } else {
                Log.e(TAG, "无法保存临时文件");
                callback.onOcrComplete("[ERROR] 无法保存临时文件");
                return;
            }
            
            // 异步调用识别接口
            OCR.getInstance(context).recognizeGeneralBasic(params, new OnResultListener<GeneralResult>() {
                @Override
                public void onResult(GeneralResult result) {
                    if (result != null && result.getWordList() != null) {
                        callback.onOcrComplete(formatResult(result));
                    } else {
                        callback.onOcrComplete("");
                    }
                }
                
                @Override
                public void onError(OCRError error) {
                    Log.e(TAG, "OCR识别失败: " + error.getMessage());
                    Log.e(TAG, "错误码: " + error.getErrorCode());
                    callback.onOcrComplete("");
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "OCR识别失败: " + e.getMessage());
            callback.onOcrComplete("");
        }
    }
    
    /**
     * 格式化识别结果
     * @param result 识别结果
     * @return 格式化后的文本
     */
    private String formatResult(GeneralResult result) {
        // 使用泛型避免类型转换错误
        List<? extends WordSimple> wordList = result.getWordList();
        StringBuilder sb = new StringBuilder();
        
        for (WordSimple word : wordList) {
            sb.append(word.getWords()).append("\n");
        }
        
        return sb.toString().trim();
    }
    
    /**
     * 释放OCR资源
     */
    public void release() {
        OCR.getInstance(context).release();
    }
    
    /**
     * OCR回调接口
     */
    public interface OcrCallback {
        void onOcrComplete(String result);
    }
}