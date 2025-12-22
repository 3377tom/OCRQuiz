package com.floatingocrquiz;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;

import com.baidu.ocr.sdk.OCR;
import com.baidu.ocr.sdk.OnResultListener;
import com.baidu.ocr.sdk.exception.OCRError;
import com.baidu.ocr.sdk.model.AccessToken;
import com.baidu.ocr.sdk.model.GeneralBasicParams;
import com.baidu.ocr.sdk.model.GeneralResult;
import com.baidu.ocr.sdk.model.WordSimple;

// PaddleOCR相关导入

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
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
    
    // OCR语言类型枚举
    public enum OCRLanguageType {
        CHINESE,
        ENGLISH
    }
    
    // 默认使用中文识别
    private OCRLanguageType currentOCRLanguage = OCRLanguageType.CHINESE;
    // 英文识别模型路径
    private static final String REC_ENGLISH_MODEL_NAME = "rec_english_common_infer";
    
    // PaddleOCR相关变量
    private OCRPredictorNative paddleOCRPredictor = null;
    private static final String PADDLE_OCR_MODEL_DIR = "paddleocr";
    // 使用目录结构的模型名称，与当前assets中的模型结构一致
    private static final String DET_MODEL_NAME = "det_chinese_db_infer";
    private static final String REC_MODEL_NAME = "rec_chinese_common_infer";
    private static final String CLS_MODEL_NAME = "cls_chinese_infer";
    private static final String LABEL_FILE_NAME = "ppocr_keys_v1.txt";
    
    /**
     * 私有构造函数，防止外部实例化
     * @param context 上下文
     */
    private OCRHelper(Context context) {
        this.context = context.getApplicationContext();
        
        // 检查是否在主线程
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            // 在主线程，直接初始化百度OCR
            initBaiduOCR();
            // 初始化PaddleOCR
            initPaddleOCR();
        } else {
            // 不在主线程，切换到主线程初始化百度OCR SDK
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                initBaiduOCR();
                // 初始化PaddleOCR
                initPaddleOCR();
            });
        }
    }
    
    /**
     * 初始化PaddleOCR
     */
    private void initPaddleOCR() {
        try {
            Log.d(TAG, "开始初始化PaddleOCR，语言: " + currentOCRLanguage.name());
            
            // 检查上下文对象是否为空
            if (context == null) {
                Log.e(TAG, "上下文对象为空，无法初始化PaddleOCR");
                return;
            }
            
            // 关闭之前的预测器（如果存在）
            if (paddleOCRPredictor != null) {
                try {
                    paddleOCRPredictor.destroy();
                    paddleOCRPredictor = null;
                    Log.d(TAG, "已关闭之前的PaddleOCR预测器");
                } catch (Exception e) {
                    Log.e(TAG, "关闭PaddleOCR预测器失败: " + e.getMessage());
                }
            }
            
            // 检查模型文件是否存在，不存在则复制
        String modelPath = context.getCacheDir() + File.separator + PADDLE_OCR_MODEL_DIR;
        
        // 实际的模型文件路径（与assets中的目录结构一致）
        String detModelPath = modelPath + File.separator + DET_MODEL_NAME + File.separator + "model.nb";
        String recModelPath = modelPath + File.separator + 
            (currentOCRLanguage == OCRLanguageType.CHINESE ? REC_MODEL_NAME : REC_ENGLISH_MODEL_NAME) + 
            File.separator + "model.nb";
        String clsModelPath = modelPath + File.separator + CLS_MODEL_NAME + File.separator + "model.nb";
        
        File detModelFile = new File(detModelPath);
        File recModelFile = new File(recModelPath);
        File clsModelFile = new File(clsModelPath);
        
        if (!detModelFile.exists() || !recModelFile.exists() || !clsModelFile.exists()) {
            Log.d(TAG, "PaddleOCR模型文件不存在，尝试从assets复制");
            try {
                copyPaddleOCRModelsFromAssets();
            } catch (Exception e) {
                Log.e(TAG, "复制PaddleOCR模型文件失败: " + e.getMessage());
                return;
            }
        }
        
        // 初始化PaddleOCR预测器
        OCRPredictorNative.Config config = new OCRPredictorNative.Config();
        config.useOpencl = 0; // 不使用OpenCL
        config.cpuThreadNum = 4;
        config.cpuPower = "LITE_POWER_HIGH";
        config.detModelFilename = detModelPath;
        config.recModelFilename = recModelPath;
        config.clsModelFilename = clsModelPath;
            
            try {
                paddleOCRPredictor = new OCRPredictorNative(config);
                Log.d(TAG, "PaddleOCR预测器初始化成功");
            } catch (Exception e) {
                Log.e(TAG, "PaddleOCR预测器初始化失败: " + e.getMessage());
                paddleOCRPredictor = null;
            }
            
        } catch (NullPointerException e) {
            Log.e(TAG, "PaddleOCR初始化发生空指针异常: " + e.getMessage());
            Log.e(TAG, "异常堆栈: " + android.util.Log.getStackTraceString(e));
        } catch (Exception e) {
            Log.e(TAG, "PaddleOCR初始化失败: " + e.getMessage());
            Log.e(TAG, "异常堆栈: " + android.util.Log.getStackTraceString(e));
        }
    }
    
    /**
     * 从assets复制PaddleOCR模型文件到应用目录
     */
    private void copyPaddleOCRModelsFromAssets() throws IOException {
        Log.d(TAG, "开始从assets复制PaddleOCR模型文件");
        
        // 获取目标目录
        String destDirPath = context.getCacheDir() + File.separator + PADDLE_OCR_MODEL_DIR;
        File destDir = new File(destDirPath);
        if (!destDir.exists()) {
            destDir.mkdirs();
        }
        
        // 复制模型目录下的所有文件
        Utils.copyDirectoryFromAssets(context, PADDLE_OCR_MODEL_DIR, destDirPath);
        
        Log.d(TAG, "PaddleOCR模型文件复制完成");
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
        // 从SharedPreferences读取默认设置
        instance.loadOCRInterfaceSetting();
        instance.loadOCRLanguageSetting();
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
     * 获取当前使用的OCR语言类型
     * @return 当前OCR语言类型
     */
    public OCRLanguageType getCurrentOCRLanguage() {
        return currentOCRLanguage;
    }
    
    /**
     * 设置当前使用的OCR语言类型
     * @param languageType OCR语言类型
     */
    public void setCurrentOCRLanguage(OCRLanguageType languageType) {
        if (currentOCRLanguage != languageType) {
            currentOCRLanguage = languageType;
            // 保存设置到SharedPreferences
            saveOCRLanguageSetting();
            // 重新初始化PaddleOCR以切换模型
            if (paddleOCRPredictor != null) {
                try {
                    paddleOCRPredictor.destroy();
                    paddleOCRPredictor = null;
                    Log.d(TAG, "已关闭之前的PaddleOCR预测器");
                } catch (Exception e) {
                    Log.e(TAG, "关闭PaddleOCR预测器失败: " + e.getMessage());
                }
            }
            initPaddleOCR();
        }
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
     * 从SharedPreferences加载OCR语言设置
     */
    private void loadOCRLanguageSetting() {
        try {
            SharedPreferences prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
            String languageType = prefs.getString("ocr_language", "CHINESE");
            currentOCRLanguage = OCRLanguageType.valueOf(languageType);
        } catch (Exception e) {
            Log.e(TAG, "加载OCR语言设置失败: " + e.getMessage());
            currentOCRLanguage = OCRLanguageType.CHINESE;
        }
    }
    
    /**
     * 保存OCR语言设置到SharedPreferences
     */
    private void saveOCRLanguageSetting() {
        try {
            SharedPreferences prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("ocr_language", currentOCRLanguage.name());
            editor.apply();
        } catch (Exception e) {
            Log.e(TAG, "保存OCR语言设置失败: " + e.getMessage());
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
            
            // 检查OCR类是否可用
            if (OCR.class == null) {
                Log.e(TAG, "OCR类不可用，无法初始化百度OCR SDK");
                isInitialized = false;
                return;
            }
            
            // 检查OCR实例是否为空
            OCR ocrInstance = null;
            try {
                ocrInstance = OCR.getInstance(context);
            } catch (Exception e) {
                Log.e(TAG, "获取OCR实例失败: " + e.getMessage());
                isInitialized = false;
                return;
            }
            
            if (ocrInstance == null) {
                Log.e(TAG, "OCR实例为空，无法初始化百度OCR SDK");
                isInitialized = false;
                return;
            }
            
            // 使用自定义License文件名：aip-ocr.license
            // 确保该文件已放置在assets目录下
            ocrInstance.initAccessToken(new OnResultListener<AccessToken>() {
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
                    Log.e(TAG, "百度OCR SDK初始化失败: " + (error != null ? error.getMessage() : "未知错误"));
                    if (error != null) {
                        Log.e(TAG, "错误码: " + error.getErrorCode());
                        Log.e(TAG, "错误详细信息: " + error.toString());
                    }
                    isInitialized = false;
                }
            }, "aip-ocr.license", context);
        } catch (NullPointerException e) {
            Log.e(TAG, "初始化百度OCR SDK时发生空指针异常: " + e.getMessage());
            Log.e(TAG, "异常堆栈: " + android.util.Log.getStackTraceString(e));
            isInitialized = false;
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
            
            // 检查预测器是否已经初始化
            if (paddleOCRPredictor == null) {
                Log.e(TAG, "PaddleOCR预测器尚未初始化，无法执行识别");
                return "[ERROR] PaddleOCR预测器未初始化，请检查模型文件是否正确";
            }
            
            // 执行OCR识别
            int detLongSize = 960; // 检测模型的最大边长
            int run_det = 1; // 启用检测
            int run_cls = 1; // 启用方向分类
            int run_rec = 1; // 启用识别
            
            ArrayList<OcrResultModel> results = paddleOCRPredictor.runImage(bitmap, detLongSize, run_det, run_cls, run_rec);
            
            // 格式化识别结果
            return formatPaddleOCRResult(results);
        } catch (NullPointerException e) {
            Log.e(TAG, "PaddleOCR识别发生空指针异常: " + e.getMessage());
            Log.e(TAG, "异常堆栈: " + android.util.Log.getStackTraceString(e));
            return "[ERROR] PaddleOCR内部错误，请重试";
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
        String tempFilePath = null;
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
            tempFilePath = BitmapUtils.saveBitmapToTempFile(bitmap, context);
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
        // 在子线程中执行识别
        new Thread(() -> {
            try {
                Log.d(TAG, "开始PaddleOCR异步识别，Bitmap尺寸: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                
                // 检查预测器是否已经初始化
                if (paddleOCRPredictor == null) {
                    Log.e(TAG, "PaddleOCR预测器尚未初始化，无法执行识别");
                    callback.onOcrComplete("[ERROR] PaddleOCR预测器未初始化，请检查模型文件是否正确");
                    return;
                }
                
                // 执行OCR识别
                int detLongSize = 960; // 检测模型的最大边长
                int run_det = 1; // 启用检测
                int run_cls = 1; // 启用方向分类
                int run_rec = 1; // 启用识别
                
                ArrayList<OcrResultModel> results = paddleOCRPredictor.runImage(bitmap, detLongSize, run_det, run_cls, run_rec);
                
                // 格式化识别结果
                String formattedResult = formatPaddleOCRResult(results);
                callback.onOcrComplete(formattedResult);
            } catch (NullPointerException e) {
                Log.e(TAG, "PaddleOCR识别发生空指针异常: " + e.getMessage());
                Log.e(TAG, "异常堆栈: " + android.util.Log.getStackTraceString(e));
                callback.onOcrComplete("[ERROR] PaddleOCR内部错误，请重试");
            } catch (Exception e) {
                Log.e(TAG, "PaddleOCR识别失败: " + e.getMessage());
                Log.e(TAG, "异常堆栈: " + android.util.Log.getStackTraceString(e));
                callback.onOcrComplete("[ERROR] PaddleOCR识别失败: " + e.getMessage());
            }
        }).start();
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
     * 格式化PaddleOCR识别结果
     * @param results OCR识别结果列表
     * @return 格式化后的识别结果
     */
    private String formatPaddleOCRResult(ArrayList<OcrResultModel> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (OcrResultModel result : results) {
            if (result.getLabel() != null && !result.getLabel().isEmpty()) {
                sb.append(result.getLabel()).append("\n");
            }
        }
        
        String fullText = sb.toString().trim();
        
        // 应用题干字数限制
        return applyQuestionLengthLimit(fullText);
    }
    
    /**
     * 释放OCR资源
     */
    public void release() {
        try {
            // 释放百度OCR资源
            if (context != null && OCR.getInstance(context) != null) {
                OCR.getInstance(context).release();
                Log.d(TAG, "百度OCR资源释放成功");
            }
            
            // 释放PaddleOCR资源
            if (paddleOCRPredictor != null) {
                try {
                    paddleOCRPredictor.destroy();
                    Log.d(TAG, "PaddleOCR资源释放成功");
                } catch (Exception e) {
                    Log.e(TAG, "释放PaddleOCR资源失败: " + e.getMessage());
                } finally {
                    paddleOCRPredictor = null;
                }
            }
            
            isInitialized = false;
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