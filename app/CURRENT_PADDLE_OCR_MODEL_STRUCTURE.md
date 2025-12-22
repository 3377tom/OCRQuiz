# PaddleOCR模型结构说明

## 当前模型结构

当前应用使用的PaddleOCR模型结构如下：

```
assets/paddleocr/
├── cls_chinese_infer/
│   ├── model.nb
│   └── params
├── det_chinese_db_infer/
│   ├── model.nb
│   └── params
├── rec_chinese_common_infer/
│   ├── model.nb
│   └── params
├── rec_english_common_infer/
│   └── model.nb
└── ppocr_keys_v1.txt
```

### 模型类型说明
- **det_chinese_db_infer**: 中文检测模型
- **rec_chinese_common_infer**: 中文识别模型
- **cls_chinese_infer**: 中文方向分类模型
- **rec_english_common_infer**: 英文识别模型
- **ppocr_keys_v1.txt**: 识别字典文件

## 如何切换到Slim模型

根据CSDN博客文章（https://blog.csdn.net/gitblog_00951/article/details/150998716），可以使用Slim模型来优化性能。Slim模型是经过压缩的轻量级模型，具有更小的体积和更快的推理速度。

### 1. 下载Slim模型文件

首先需要下载以下Slim模型文件：
- `ch_PP-OCRv3_det_slim_opt.nb`
- `ch_PP-OCRv3_rec_slim_opt.nb`
- `ch_ppocr_mobile_v2.0_cls_slim_opt.nb`

### 2. 更新assets目录结构

将下载的Slim模型文件直接放入`assets/paddleocr/`目录下，无需创建子目录：

```
assets/paddleocr/
├── ch_PP-OCRv3_det_slim_opt.nb
├── ch_PP-OCRv3_rec_slim_opt.nb
├── ch_ppocr_mobile_v2.0_cls_slim_opt.nb
├── rec_english_common_infer/
│   └── model.nb
└── ppocr_keys_v1.txt
```

### 3. 修改OCRHelper.java中的模型名称常量

更新`OCRHelper.java`文件中的模型名称常量，使用Slim模型的文件名：

```java
// 使用Slim模型名称
private static final String DET_MODEL_NAME = "ch_PP-OCRv3_det_slim_opt.nb";
private static final String REC_MODEL_NAME = "ch_PP-OCRv3_rec_slim_opt.nb";
private static final String CLS_MODEL_NAME = "ch_ppocr_mobile_v2.0_cls_slim_opt.nb";
```

### 4. 调整模型加载路径逻辑

更新模型文件检查和加载路径，直接使用.nb文件而不是目录结构：

```java
// 检查模型文件是否存在，不存在则复制
String modelPath = context.getFilesDir().getAbsolutePath() + File.separator + PADDLE_OCR_MODEL_DIR;
File detModelFile = new File(modelPath + File.separator + DET_MODEL_NAME);
File recModelFile = new File(modelPath + File.separator + 
    (currentOCRLanguage == OCRLanguageType.CHINESE ? REC_MODEL_NAME : REC_ENGLISH_MODEL_NAME + File.separator + "model.nb"));
File clsModelFile = new File(modelPath + File.separator + CLS_MODEL_NAME);
File labelFile = new File(modelPath + File.separator + LABEL_FILE_NAME);
```

### 5. 更新模型复制逻辑

修改`copyPaddleOCRModelsFromAssets()`方法，直接复制Slim模型文件：

```java
// 需要复制的模型文件列表 - Slim模型直接使用.nb文件
String[] modelFiles = {
    DET_MODEL_NAME,
    REC_MODEL_NAME,
    CLS_MODEL_NAME,
    REC_ENGLISH_MODEL_NAME + File.separator + "model.nb",
    LABEL_FILE_NAME
};
```

## 注意事项

1. **当前限制**：当前使用的PaddleLite SDK不包含OCRConfig和OCRPredictor类，因此PaddleOCR功能目前不可用。需要使用完整的PaddleOCR SDK才能实现完整的OCR功能。

2. **性能优化**：Slim模型相比标准模型具有更小的体积和更快的推理速度，适合在移动设备上使用。

3. **模型选择**：根据应用需求选择合适的模型，中文识别使用中文模型，英文识别使用英文模型。

4. **模型更新**：定期检查PaddleOCR官方仓库获取最新模型，以获得更好的识别效果和性能。