package com.floatingocrquiz;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.baidu.ocr.sdk.OCR;
import com.baidu.ocr.sdk.OnResultListener;
import com.baidu.ocr.sdk.exception.OCRError;
import com.baidu.ocr.sdk.model.GeneralBasicParams;
import com.baidu.ocr.sdk.model.GeneralResult;
import com.baidu.ocr.sdk.model.WordSimple;

import java.util.List;

public class OCRHelper {

    private static final String TAG = "OCRHelper";
    private Context context;
    
    /**
     * 初始化OCRHelper
     * @param context 上下文
     */
    public OCRHelper(Context context) {
        this.context = context;
        
        // 初始化百度OCR SDK
        initBaiduOCR();
    }
    
    /**
     * 初始化百度OCR SDK
     * 使用License文件进行授权，避免直接暴露API Key/Secret Key
     */
    private void initBaiduOCR() {
        // 使用自定义License文件名：aip-ocr.license
        // 确保该文件已放置在assets目录下
        OCR.getInstance(context).initAccessTokenWithFile("aip-ocr.license", new OnResultListener<String>() {
            @Override
            public void onResult(String accessToken) {
                // 初始化成功，accessToken会自动管理，无需手动维护
                Log.d(TAG, "百度OCR SDK初始化成功");
            }
            
            @Override
            public void onError(OCRError error) {
                // 初始化失败
                Log.e(TAG, "百度OCR SDK初始化失败: " + error.getMessage());
                Log.e(TAG, "错误码: " + error.getErrorCode());
                Log.e(TAG, "错误描述: " + error.getErrorMessage());
            }
        }, context);
    }
    
    /**
     * 识别图片中的文字（同步方法）
     * @param bitmap 图片
     * @return 识别结果
     */
    public String recognizeText(Bitmap bitmap) {
        try {
            // 构建通用文字识别参数
            GeneralBasicParams params = new GeneralBasicParams();
            params.setDetectDirection(true);
            params.setLanguageType(GeneralBasicParams.LANGUAGE_TYPE_CHN_ENG);
            params.setRecognizeGranularity(GeneralBasicParams.GRANULARITY_SMALL);
            params.setImage(bitmap);
            
            // 同步调用识别接口
            GeneralResult result = OCR.getInstance(context).recognizeGeneralBasicSync(params);
            
            if (result != null && result.getWordList() != null) {
                return formatResult(result);
            } else {
                Log.e(TAG, "OCR识别结果为空");
                return "";
            }
        } catch (Exception e) {
            Log.e(TAG, "OCR识别失败: " + e.getMessage());
            return "";
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
            params.setLanguageType(GeneralBasicParams.LANGUAGE_TYPE_CHN_ENG);
            params.setRecognizeGranularity(GeneralBasicParams.GRANULARITY_SMALL);
            params.setImage(bitmap);
            
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
                    Log.e(TAG, "错误描述: " + error.getErrorMessage());
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
        List<WordSimple> wordList = result.getWordList();
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