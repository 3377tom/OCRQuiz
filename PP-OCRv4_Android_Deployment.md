# PP-OCRv4 模型 Android 部署指南

## 1. 模型转换准备

### 1.1 安装必要工具

安装 PaddlePaddle 和 PaddleLite 转换工具：
```bash
pip install paddlepaddle
pip install paddlelite
```

### 1.2 模型转换

将 PP-OCRv4 的 `.pdmodel` 和 `.pdparams` 转换为 PaddleLite 支持的 `.nb` 格式：

#### 文本检测模型 (PP-OCRv4-mobile-det)
```bash
paddle_lite_opt --model_dir=./PP-OCRv4_mobile_det_infer --param_file=./PP-OCRv4_mobile_det_infer/inference.pdparams --optimize_out_type=naive_buffer --optimize_out=./det_model --target_platform=arm
```

#### 文本识别模型 (PP-OCRv4-mobile-rec)
```bash
paddle_lite_opt --model_dir=./PP-OCRv4_mobile_rec_infer --param_file=./PP-OCRv4_mobile_rec_infer/inference.pdparams --optimize_out_type=naive_buffer --optimize_out=./rec_model --target_platform=arm
```

#### 文本方向分类模型 (可选)
```bash
paddle_lite_opt --model_dir=./PP-OCRv4_mobile_cls_infer --param_file=./PP-OCRv4_mobile_cls_infer/inference.pdparams --optimize_out_type=naive_buffer --optimize_out=./cls_model --target_platform=arm
```

## 2. Android 项目配置

### 2.1 将模型添加到项目

将转换后的 `.nb` 模型文件复制到 Android 项目的 `assets/paddleocr` 目录下：

```
app/src/main/assets/paddleocr/
├── det_model.nb
├── rec_model.nb
├── cls_model.nb
└── ppocr_keys_v1.txt
```

### 2.2 配置 PaddleLite 依赖

确保项目中已经包含 PaddleLite 的依赖配置：

在 `app/build.gradle` 中：
```groovy
// Paddle-Lite 本地依赖
implementation files('libs/paddlelite.jar')

// 配置 JNI 库目录
android {
    sourceSets {
        main {
            jniLibs.srcDirs = ['src/main/jniLibs']
        }
    }
}
```

确保 `src/main/jniLibs/arm64-v8a/` 目录下包含 `libpaddle_lite_jni.so` 文件。

## 3. 代码实现

### 3.1 初始化 PaddleOCR

在 `OCRHelper.java` 中初始化 PaddleOCR 预测器：

```java
private void initPaddleOCR() {
    // 关闭之前的预测器（如果存在）
    if (paddleOCRPredictor != null) {
        paddleOCRPredictor = null;
        Log.d(TAG, "已关闭之前的PaddleOCR预测器");
    }

    try {
        // 复制模型文件到应用私有目录
        copyPaddleOCRModelsFromAssets();

        // 获取模型路径
        String modelPath = context.getFilesDir().getAbsolutePath() + File.separator + PADDLE_OCR_MODEL_DIR;

        // 创建 PaddleOCR 配置
        PaddleOCRConfig config = new PaddleOCRConfig();
        config.setDetModelPath(modelPath + File.separator + "det_model.nb");
        config.setRecModelPath(modelPath + File.separator + "rec_model.nb");
        config.setClsModelPath(modelPath + File.separator + "cls_model.nb");
        config.setLabelPath(modelPath + File.separator + "ppocr_keys_v1.txt");
        config.setThreadNum(4);
        config.setUseOpencl(true);

        // 初始化 PaddleOCR
        paddleOCRPredictor = PaddleOCR.create(config);
        Log.d(TAG, "PaddleOCR初始化成功");
    } catch (Exception e) {
        Log.e(TAG, "PaddleOCR初始化失败: " + e.getMessage());
        e.printStackTrace();
    }
}
```

### 3.2 实现文本识别功能

```java
private String recognizeTextWithPaddleOCR(Bitmap bitmap) {
    try {
        Log.d(TAG, "开始PaddleOCR识别，Bitmap尺寸: " + bitmap.getWidth() + "x" + bitmap.getHeight());

        if (paddleOCRPredictor == null) {
            Log.e(TAG, "PaddleOCR预测器未初始化");
            initPaddleOCR();
            if (paddleOCRPredictor == null) {
                return "[ERROR] PaddleOCR未初始化成功，请检查模型文件";
            }
        }

        // 执行OCR识别
        List<OCRResult> results = paddleOCRPredictor.ocr(bitmap);

        // 处理识别结果
        StringBuilder sb = new StringBuilder();
        for (OCRResult result : results) {
            sb.append(result.text).append("\n");
        }

        String fullText = sb.toString().trim();
        if (fullText.isEmpty()) {
            Log.e(TAG, "PaddleOCR识别结果为空");
            return "[ERROR] PaddleOCR未识别到任何文字";
        }

        Log.d(TAG, "PaddleOCR识别成功: " + fullText);
        return applyQuestionLengthLimit(fullText);
    } catch (NullPointerException e) {
        Log.e(TAG, "PaddleOCR识别发生空指针异常: " + e.getMessage());
        return "[ERROR] PaddleOCR内部错误，请重试";
    } catch (Exception e) {
        Log.e(TAG, "PaddleOCR识别失败: " + e.getMessage());
        return "[ERROR] PaddleOCR识别失败: " + e.getMessage();
    }
}
```

### 3.3 模型文件复制工具

确保模型文件从 assets 复制到应用私有目录：

```java
private void copyPaddleOCRModelsFromAssets() {
    try {
        String[] modelFiles = {
            "paddleocr/det_model.nb",
            "paddleocr/rec_model.nb",
            "paddleocr/cls_model.nb",
            "paddleocr/ppocr_keys_v1.txt"
        };

        String modelPath = context.getFilesDir().getAbsolutePath() + File.separator + PADDLE_OCR_MODEL_DIR;
        File modelDir = new File(modelPath);
        if (!modelDir.exists()) {
            modelDir.mkdirs();
        }

        for (String modelFile : modelFiles) {
            String fileName = modelFile.substring(modelFile.lastIndexOf("/") + 1);
            File targetFile = new File(modelPath + File.separator + fileName);
            
            // 只有当文件不存在时才复制
            if (!targetFile.exists() || targetFile.length() == 0) {
                AssetManager assetManager = context.getAssets();
                InputStream inputStream = assetManager.open(modelFile);
                OutputStream outputStream = new FileOutputStream(targetFile);

                byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }

                outputStream.flush();
                outputStream.close();
                inputStream.close();
                
                Log.d(TAG, "模型文件复制成功: " + fileName);
            }
        }
    } catch (IOException e) {
        Log.e(TAG, "复制PaddleOCR模型文件失败: " + e.getMessage());
        e.printStackTrace();
    }
}
```

## 4. 性能优化建议

1. **使用 OpenCL 加速**：在配置中启用 `setUseOpencl(true)` 以利用 GPU 加速
2. **调整线程数**：根据设备性能设置合适的线程数（通常 2-4）
3. **模型量化**：使用 FP16 或 INT8 量化进一步减小模型大小和提升性能
4. **图像预处理优化**：在 CPU 上进行图像预处理，减少内存拷贝

## 5. 注意事项

1. **模型兼容性**：确保使用与 PaddleLite 版本兼容的模型
2. **权限配置**：确保应用具有文件读写权限
3. **资源释放**：在应用退出时正确释放 PaddleOCR 资源
4. **错误处理**：添加完善的错误处理和日志记录

## 6. 参考资料

- [PaddleLite 官方文档](https://paddle-lite.readthedocs.io/zh/latest/)
- [PP-OCR 官方文档](https://github.com/PaddlePaddle/PaddleOCR)
- [PaddleOCR Android Demo](https://github.com/PaddlePaddle/PaddleOCR/tree/release/2.6/deploy/android)