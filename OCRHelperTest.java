import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.floatingocrquiz.OCRHelper;
import com.floatingocrquiz.OCRHelper.OCRInterfaceType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * OCRHelper测试类
 * 用于验证OCRHelper的基本功能
 */
public class OCRHelperTest {

    private static final String TAG = "OCRHelperTest";
    private Context context;

    public OCRHelperTest(Context context) {
        this.context = context;
    }

    /**
     * 测试OCRHelper的初始化
     */
    public void testInitialize() {
        Log.d(TAG, "开始测试OCRHelper初始化");
        
        try {
            // 获取OCRHelper实例
            OCRHelper ocrHelper = OCRHelper.getInstance(context);
            
            if (ocrHelper != null) {
                Log.d(TAG, "OCRHelper初始化成功");
            } else {
                Log.e(TAG, "OCRHelper初始化失败");
            }
        } catch (Exception e) {
            Log.e(TAG, "OCRHelper初始化异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 测试OCR接口切换功能
     */
    public void testInterfaceSwitch() {
        Log.d(TAG, "开始测试OCR接口切换功能");
        
        try {
            OCRHelper ocrHelper = OCRHelper.getInstance(context);
            
            // 测试切换到PaddleOCR
            ocrHelper.setCurrentOCRInterface(OCRInterfaceType.PADDLE_OCR);
            OCRInterfaceType currentInterface = ocrHelper.getCurrentOCRInterface();
            
            if (currentInterface == OCRInterfaceType.PADDLE_OCR) {
                Log.d(TAG, "成功切换到PaddleOCR接口");
            } else {
                Log.e(TAG, "切换到PaddleOCR接口失败");
            }
            
            // 测试切换回百度OCR
            ocrHelper.setCurrentOCRInterface(OCRInterfaceType.BAIDU_OCR);
            currentInterface = ocrHelper.getCurrentOCRInterface();
            
            if (currentInterface == OCRInterfaceType.BAIDU_OCR) {
                Log.d(TAG, "成功切换回百度OCR接口");
            } else {
                Log.e(TAG, "切换回百度OCR接口失败");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "OCR接口切换异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 测试PaddleOCR模型文件路径
     */
    public void testPaddleOCRModelPath() {
        Log.d(TAG, "开始测试PaddleOCR模型文件路径");
        
        try {
            // 检查应用目录下的模型文件路径
            String modelPath = context.getFilesDir().getAbsolutePath() + File.separator + "paddleocr";
            Log.d(TAG, "PaddleOCR模型文件路径: " + modelPath);
            
            // 检查assets目录下的模型文件
            String[] modelFiles = {
                "paddleocr/det_chinese_db_infer/model.nb",
                "paddleocr/det_chinese_db_infer/params",
                "paddleocr/rec_chinese_common_infer/model.nb",
                "paddleocr/rec_chinese_common_infer/params",
                "paddleocr/cls_chinese_infer/model.nb",
                "paddleocr/cls_chinese_infer/params",
                "paddleocr/ppocr_keys_v1.txt"
            };
            
            for (String fileName : modelFiles) {
                try {
                    InputStream is = context.getAssets().open(fileName);
                    if (is != null) {
                        Log.d(TAG, "找到模型文件: " + fileName);
                        is.close();
                    }
                } catch (IOException e) {
                    Log.w(TAG, "未找到模型文件: " + fileName);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "PaddleOCR模型文件路径测试异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 测试OCRHelper的释放功能
     */
    public void testRelease() {
        Log.d(TAG, "开始测试OCRHelper释放功能");
        
        try {
            OCRHelper ocrHelper = OCRHelper.getInstance(context);
            ocrHelper.release();
            Log.d(TAG, "OCRHelper释放成功");
        } catch (Exception e) {
            Log.e(TAG, "OCRHelper释放异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 运行所有测试
     */
    public void runAllTests() {
        Log.d(TAG, "==================== 开始运行所有OCRHelper测试 ====================");
        
        testInitialize();
        testInterfaceSwitch();
        testPaddleOCRModelPath();
        testRelease();
        
        Log.d(TAG, "==================== 所有OCRHelper测试运行完毕 ====================");
    }
}