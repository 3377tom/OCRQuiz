package com.floatingocrquiz;

import android.content.Context;
import android.content.SharedPreferences;
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
    
    // OCR接口类型枚举
    public enum OCRInterfaceType {
        BAIDU_OCR,
        PADDLE_OCR
    }
    
    // 默认使用百度OCR
    private OCRInterfaceType currentOCRInterface = OCRInterfaceType.BAIDU_OCR;
    
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
            // 等待初始化完成，最多等待5秒
            try {
                long startTime = System.currentTimeMillis();
                while (!instance.isInitialized && System.currentTimeMillis() - startTime < 5000) {
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "初始化等待被中断: " + e.getMessage());
            }
        }
        // 从SharedPreferences读取默认OCR接口设置
        instance.loadOCRInterfaceSetting();
        return instance;
    }
    
    /**
     * 获取当前使用的OCR接口类型
     * @return 当前OCR接口类型
     */
    public OCRInterfaceType getCurrentOCRInterface() {
        return currentOCRInterface;
    }
    
    /**
     * 设置当前使用的OCR接口类型
     * @param ocrInterfaceType OCR接口类型
     */
    public void setCurrentOCRInterface(OCRInterfaceType ocrInterfaceType) {
        currentOCRInterface = ocrInterfaceType;
        // 保存设置到SharedPreferences
        saveOCRInterfaceSetting();
    }
    
    /**
     * 从SharedPreferences加载OCR接口设置
     */
    private void loadOCRInterfaceSetting() {
        try {
            SharedPreferences prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
            String interfaceType = prefs.getString("ocr_interface", "BAIDU_OCR");
            currentOCRInterface = OCRInterfaceType.valueOf(interfaceType);
        } catch (Exception e) {
            Log.e(TAG, "加载OCR接口设置失败: " + e.getMessage());
            currentOCRInterface = OCRInterfaceType.BAIDU_OCR;
        }
    }
    
    /**
     * 保存OCR接口设置到SharedPreferences
     */
    private void saveOCRInterfaceSetting() {
        try {
            SharedPreferences prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("ocr_interface", currentOCRInterface.name());
            editor.apply();
        } catch (Exception e) {
            Log.e(TAG, "保存OCR接口设置失败: " + e.getMessage());
        }
    }
    
    /**
     * 初始化百度OCR SDK
     * 使用License文件进行授权，避免直接暴露API Key/Secret Key
     */
    private void initBaiduOCR() {
        try {
            // 检查上下文对象是否为空
            if (context == null) {
                Log.e(TAG, "上下文对象为空，无法初始化百度OCR SDK");
                isInitialized = false;
                return;
            }
            
            Log.d(TAG, "开始初始化百度OCR SDK");
            
            // 使用自定义License文件名：aip-ocr.license
            // 确保该文件已放置在assets目录下
            OCR.getInstance(context).initAccessToken(new OnResultListener<AccessToken>() {
                @Override
                public void onResult(AccessToken accessToken) {
                    if (accessToken != null) {
                        // 初始化成功，accessToken会自动管理，无需手动维护
                        Log.d(TAG, "百度OCR SDK初始化成功，获取到访问令牌");
                        Log.d(TAG, "访问令牌有效期: " + accessToken.getExpiresTime() + "毫秒");
                        isInitialized = true;
                    } else {
                        Log.e(TAG, "百度OCR SDK初始化失败：访问令牌为空");
                        isInitialized = false;
                    }
                }
                
                @Override
                public void onError(OCRError error) {
                    // 初始化失败
                    Log.e(TAG, "百度OCR SDK初始化失败: " + error.getMessage());
                    if (error != null) {
                        Log.e(TAG, "错误码: " + error.getErrorCode());
                        Log.e(TAG, "错误详细信息: " + error.toString());
                    }
                    isInitialized = false;
                }
            }, "aip-ocr.license", context);
        } catch (Exception e) {
            Log.e(TAG, "初始化百度OCR SDK时发生异常: " + e.getMessage());
            Log.e(TAG, "异常堆栈: " + android.util.Log.getStackTraceString(e));
            isInitialized = false;
        }
    }
    
    /**
     * 识别图片中的文字（同步方法）
     * @param bitmap 图片
     * @return 识别结果
     */
    public String recognizeText(Bitmap bitmap) {
        switch (currentOCRInterface) {
            case BAIDU_OCR:
                return recognizeTextWithBaiduOCR(bitmap);
            case PADDLE_OCR:
                return recognizeTextWithPaddleOCR(bitmap);
            default:
                return "[ERROR] 不支持的OCR接口类型";
        }
    }
    
    /**
     * 使用百度OCR识别图片中的文字（同步方法）
     * @param bitmap 图片
     * @return 识别结果
     */
    private String recognizeTextWithBaiduOCR(Bitmap bitmap) {
        String tempFilePath = null;
        try {
            Log.d(TAG, "开始百度OCR识别，Bitmap尺寸: " + bitmap.getWidth() + "x" + bitmap.getHeight());
            
            // 检查上下文对象是否为空
            if (context == null) {
                Log.e(TAG, "上下文对象为空，无法进行OCR识别");
                return "[ERROR] 应用上下文为空";
            }
            
            // 检查OCR SDK实例是否为空
            if (OCR.getInstance(context) == null) {
                Log.e(TAG, "OCR SDK实例为空，无法进行OCR识别");
                return "[ERROR] OCR SDK实例为空";
            }
            
            // 等待OCR SDK初始化完成，最多等待5秒
            if (!isInitialized) {
                Log.d(TAG, "百度OCR SDK尚未初始化完成，等待初始化...");
                long startTime = System.currentTimeMillis();
                while (!isInitialized && System.currentTimeMillis() - startTime < 5000) {
                    Thread.sleep(100);
                }
                if (!isInitialized) {
                    Log.e(TAG, "百度OCR SDK初始化超时");
                    return "[ERROR] OCR SDK初始化超时";
                } else {
                    Log.d(TAG, "百度OCR SDK初始化完成");
                }
            }
            
            // 再次检查初始化状态，确保SDK已完全初始化
            if (!isInitialized) {
                Log.e(TAG, "百度OCR SDK初始化未完成，无法进行识别");
                return "[ERROR] OCR SDK初始化未完成";
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
            final String finalTempFilePath = tempFilePath;
            
            // 异步调用识别接口
            OCR.getInstance(context).recognizeGeneralBasic(params, new OnResultListener<GeneralResult>() {
                @Override
                public void onResult(GeneralResult result) {
                    try {
                        if (result != null) {
                            Log.d(TAG, "百度OCR识别成功，结果不为null");
                            
                            if (result.getWordList() != null) {
                                Log.d(TAG, "识别到的单词数量: " + result.getWordList().size());
                                resultText[0] = formatResult(result);
                                Log.d(TAG, "格式化后的识别结果: " + resultText[0]);
                                
                                // 检查识别结果是否为空
                                if (resultText[0].isEmpty()) {
                                    Log.e(TAG, "百度OCR识别结果为空");
                                    resultText[0] = "[ERROR] 识别结果为空";
                                }
                            } else {
                                Log.e(TAG, "百度OCR识别结果的WordList为空");
                                resultText[0] = "[ERROR] 识别结果为空";
                            }
                        } else {
                            Log.e(TAG, "百度OCR识别结果为空");
                            resultText[0] = "[ERROR] 识别结果为空";
                        }
                    } finally {
                        // 识别完成后删除临时文件
                        if (finalTempFilePath != null) {
                            Log.d(TAG, "准备删除临时文件: " + finalTempFilePath);
                            BitmapUtils.deleteTempFile(finalTempFilePath);
                            Log.d(TAG, "临时文件删除成功");
                        }
                        latch.countDown();
                    }
                }
                
                @Override
                public void onError(OCRError error) {
                    try {
                        Log.e(TAG, "百度OCR识别失败: " + error.getMessage());
                        Log.e(TAG, "错误码: " + error.getErrorCode());
                        Log.e(TAG, "错误详细信息: " + error.toString());
                        resultText[0] = "[ERROR] 识别失败: " + error.getMessage();
                    } finally {
                        // 识别完成后删除临时文件
                        if (finalTempFilePath != null) {
                            Log.d(TAG, "准备删除临时文件: " + finalTempFilePath);
                            BitmapUtils.deleteTempFile(finalTempFilePath);
                            Log.d(TAG, "临时文件删除成功");
                        }
                        latch.countDown();
                    }
                }
            });
            
            // 等待异步调用完成
            boolean waitResult = latch.await(10, TimeUnit.SECONDS);
            if (!waitResult) {
                Log.e(TAG, "百度OCR识别超时");
                // 超时也要删除临时文件
                if (tempFilePath != null) {
                    BitmapUtils.deleteTempFile(tempFilePath);
                }
                return "[ERROR] 识别超时";
            }
            
            Log.d(TAG, "百度OCR识别完成，返回结果长度: " + resultText[0].length());
            Log.d(TAG, "返回的识别结果: " + resultText[0]);
            return resultText[0];
        } catch (NullPointerException e) {
            Log.e(TAG, "百度OCR识别发生空指针异常，可能是SDK内部错误: " + e.getMessage());
            Log.e(TAG, "异常堆栈: " + android.util.Log.getStackTraceString(e));
            // 发生异常也要删除临时文件
            if (tempFilePath != null) {
                BitmapUtils.deleteTempFile(tempFilePath);
            }
            return "[ERROR] OCR服务内部错误，请重试";
        } catch (Exception e) {
            Log.e(TAG, "百度OCR识别失败: " + e.getMessage());
            Log.e(TAG, "异常堆栈: " + android.util.Log.getStackTraceString(e));
            // 发生异常也要删除临时文件
            if (tempFilePath != null) {
                BitmapUtils.deleteTempFile(tempFilePath);
            }
            return "[ERROR] 识别失败: " + e.getMessage();
        }
    }
    
    /**
     * 使用PaddleOCR识别图片中的文字（同步方法）
     * @param bitmap 图片
     * @return 识别结果
     */
    private String recognizeTextWithPaddleOCR(Bitmap bitmap) {
        try {
            Log.d(TAG, "开始PaddleOCR识别，Bitmap尺寸: " + bitmap.getWidth() + "x" + bitmap.getHeight());
            
            // 这里是PaddleOCR的实现，由于PaddleOCR Android SDK需要额外集成
            // 目前暂时返回模拟结果，实际项目中需要替换为真实的PaddleOCR调用
            // TODO: 集成PaddleOCR Android SDK并实现真实的识别逻辑
            
            // 模拟PaddleOCR识别结果
            String mockResult = "[PaddleOCR模拟结果] 这是一段测试文本，用于演示PaddleOCR识别功能。";
            Log.d(TAG, "PaddleOCR识别完成，返回模拟结果: " + mockResult);
            
            // 应用题干字数限制
            return applyQuestionLengthLimit(mockResult);
        } catch (Exception e) {
            Log.e(TAG, "PaddleOCR识别失败: " + e.getMessage());
            Log.e(TAG, "异常堆栈: " + android.util.Log.getStackTraceString(e));
            return "[ERROR] PaddleOCR识别失败: " + e.getMessage();
        }
    }
    
    /**
     * 识别图片中的文字（异步方法）
     * @param bitmap 图片
     * @param callback 回调
     */
    public void recognizeTextAsync(Bitmap bitmap, final OcrCallback callback) {
        switch (currentOCRInterface) {
            case BAIDU_OCR:
                recognizeTextWithBaiduOCRAsync(bitmap, callback);
                break;
            case PADDLE_OCR:
                recognizeTextWithPaddleOCRAsync(bitmap, callback);
                break;
            default:
                callback.onOcrComplete("[ERROR] 不支持的OCR接口类型");
                break;
        }
    }
    
    /**
     * 使用百度OCR识别图片中的文字（异步方法）
     * @param bitmap 图片
     * @param callback 回调
     */
    private void recognizeTextWithBaiduOCRAsync(Bitmap bitmap, final OcrCallback callback) {
        try {
            Log.d(TAG, "开始百度OCR异步识别，Bitmap尺寸: " + bitmap.getWidth() + "x" + bitmap.getHeight());
            
            // 检查上下文对象是否为空
            if (context == null) {
                Log.e(TAG, "上下文对象为空，无法进行OCR识别");
                callback.onOcrComplete("[ERROR] 应用上下文为空");
                return;
            }
            
            // 检查OCR SDK实例是否为空
            if (OCR.getInstance(context) == null) {
                Log.e(TAG, "OCR SDK实例为空，无法进行OCR识别");
                callback.onOcrComplete("[ERROR] OCR SDK实例为空");
                return;
            }
            
            // 检查初始化状态
            if (!isInitialized) {
                Log.e(TAG, "百度OCR SDK未初始化完成，无法进行识别");
                callback.onOcrComplete("[ERROR] OCR SDK未初始化完成");
                return;
            }
            
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
            
            final String finalTempFilePath = tempFilePath;
            // 异步调用识别接口
            OCR.getInstance(context).recognizeGeneralBasic(params, new OnResultListener<GeneralResult>() {
                @Override
                public void onResult(GeneralResult result) {
                    try {
                        if (result != null && result.getWordList() != null) {
                            String formattedResult = formatResult(result);
                            if (formattedResult.isEmpty()) {
                                Log.e(TAG, "百度OCR识别结果为空");
                                callback.onOcrComplete("[ERROR] 识别结果为空");
                            } else {
                                callback.onOcrComplete(formattedResult);
                            }
                        } else {
                            Log.e(TAG, "百度OCR识别结果为空");
                            callback.onOcrComplete("[ERROR] 识别结果为空");
                        }
                    } finally {
                        // 识别完成后删除临时文件
                        if (finalTempFilePath != null) {
                            Log.d(TAG, "准备删除临时文件: " + finalTempFilePath);
                            BitmapUtils.deleteTempFile(finalTempFilePath);
                            Log.d(TAG, "临时文件删除成功");
                        }
                    }
                }
                
                @Override
                public void onError(OCRError error) {
                    try {
                        Log.e(TAG, "百度OCR识别失败: " + error.getMessage());
                        Log.e(TAG, "错误码: " + error.getErrorCode());
                        callback.onOcrComplete("");
                    } finally {
                        // 识别完成后删除临时文件
                        if (finalTempFilePath != null) {
                            Log.d(TAG, "准备删除临时文件: " + finalTempFilePath);
                            BitmapUtils.deleteTempFile(finalTempFilePath);
                            Log.d(TAG, "临时文件删除成功");
                        }
                    }
                }
            });
        } catch (NullPointerException e) {
            Log.e(TAG, "百度OCR识别发生空指针异常，可能是SDK内部getAccessToken()方法错误: " + e.getMessage());
            Log.e(TAG, "异常堆栈: " + android.util.Log.getStackTraceString(e));
            // 发生异常也要删除临时文件
            if (tempFilePath != null) {
                BitmapUtils.deleteTempFile(tempFilePath);
            }
            callback.onOcrComplete("[ERROR] OCR服务内部错误，请重试");
        } catch (Exception e) {
            Log.e(TAG, "百度OCR识别失败: " + e.getMessage());
            Log.e(TAG, "异常堆栈: " + android.util.Log.getStackTraceString(e));
            // 发生异常也要删除临时文件
            if (tempFilePath != null) {
                BitmapUtils.deleteTempFile(tempFilePath);
            }
            callback.onOcrComplete("[ERROR] 识别失败: " + e.getMessage());
        }
    }
    
    /**
     * 使用PaddleOCR识别图片中的文字（异步方法）
     * @param bitmap 图片
     * @param callback 回调
     */
    private void recognizeTextWithPaddleOCRAsync(Bitmap bitmap, final OcrCallback callback) {
        try {
            Log.d(TAG, "开始PaddleOCR异步识别，Bitmap尺寸: " + bitmap.getWidth() + "x" + bitmap.getHeight());
            
            // 这里是PaddleOCR的实现，由于PaddleOCR Android SDK需要额外集成
            // 目前暂时返回模拟结果，实际项目中需要替换为真实的PaddleOCR调用
            // TODO: 集成PaddleOCR Android SDK并实现真实的识别逻辑
            
            // 模拟PaddleOCR识别结果
            String mockResult = "[PaddleOCR模拟结果] 这是一段测试文本，用于演示PaddleOCR异步识别功能。";
            Log.d(TAG, "PaddleOCR异步识别完成，返回模拟结果: " + mockResult);
            
            // 应用题干字数限制
            String resultWithLimit = applyQuestionLengthLimit(mockResult);
            callback.onOcrComplete(resultWithLimit);
        } catch (Exception e) {
            Log.e(TAG, "PaddleOCR识别失败: " + e.getMessage());
            callback.onOcrComplete("[ERROR] PaddleOCR识别失败: " + e.getMessage());
        }
    }
    
    /**
     * 格式化识别结果
     * @param result 识别结果
     * @return 格式化后的文本，已应用题干字数限制
     */
    private String formatResult(GeneralResult result) {
        // 使用泛型避免类型转换错误
        List<? extends WordSimple> wordList = result.getWordList();
        StringBuilder sb = new StringBuilder();
        
        for (WordSimple word : wordList) {
            sb.append(word.getWords()).append("\n");
        }
        
        String fullText = sb.toString().trim();
        
        // 应用题干字数限制
        return applyQuestionLengthLimit(fullText);
    }
    
    /**
     * 应用题干字数限制
     * @param text 原始文本
     * @return 应用限制后的文本
     */
    private String applyQuestionLengthLimit(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        try {
            // 从SharedPreferences获取题干字数限制设置
            SharedPreferences prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
            int limit = prefs.getInt("question_length_limit", 50); // 默认50字
            
            // 如果限制大于0且文本长度超过限制，则截断
            if (limit > 0 && text.length() > limit) {
                return text.substring(0, limit) + "...";
            }
        } catch (Exception e) {
            Log.e(TAG, "读取题干字数限制设置失败: " + e.getMessage());
        }
        
        return text;
    }
    
    /**
     * 释放OCR资源
     */
    public void release() {
        try {
            // 检查上下文对象是否为空
            if (context == null) {
                Log.e(TAG, "上下文对象为空，无法释放OCR资源");
                return;
            }
            
            // 检查OCR SDK实例是否为空
            if (OCR.getInstance(context) == null) {
                Log.e(TAG, "OCR SDK实例为空，无法释放资源");
                return;
            }
            
            // 释放OCR资源
            OCR.getInstance(context).release();
            Log.d(TAG, "OCR资源释放成功");
        } catch (NullPointerException e) {
            Log.e(TAG, "释放OCR资源时发生空指针异常: " + e.getMessage());
            Log.e(TAG, "异常堆栈: " + android.util.Log.getStackTraceString(e));
        } catch (Exception e) {
            Log.e(TAG, "释放OCR资源失败: " + e.getMessage());
            Log.e(TAG, "异常堆栈: " + android.util.Log.getStackTraceString(e));
        }
    }
    
    /**
     * OCR回调接口
     */
    public interface OcrCallback {
        void onOcrComplete(String result);
    }
}