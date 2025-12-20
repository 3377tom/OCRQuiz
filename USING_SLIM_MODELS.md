# PaddleOCR Slim模型使用说明

本文档详细介绍了FloatingOCRQuiz应用中使用的PaddleOCR Slim模型，包括模型名称、大小、下载链接和使用方法等信息。

## 1. 模型概述

FloatingOCRQuiz应用使用了PaddleOCR Slim模型，这是PaddleOCR团队针对移动设备优化的轻量级模型，具有体积小、推理速度快、准确率高等特点。

## 2. 使用的模型列表

### 2.1 文本检测模型

| 模型名称 | 模型类型 | 模型大小 | 下载链接 |
|---------|---------|---------|---------|
| det_chinese_db_infer | 文本检测模型 | 1.44 MB | [点击下载](https://paddleocr.bj.bcebos.com/20-09-22/mobile/lite/ch_ppocr_mobile_v1.1_det_prune_opt.nb) |

### 2.2 文本识别模型

| 模型名称 | 模型类型 | 模型大小 | 语言支持 | 下载链接 |
|---------|---------|---------|---------|---------|
| rec_chinese_common_infer | 文本识别模型 | 1.53 MB | 中文 | [点击下载](https://paddleocr.bj.bcebos.com/20-09-22/mobile/lite/ch_ppocr_mobile_v1.1_rec_quant_opt.nb) |
| rec_english_common_infer | 文本识别模型 | 0.87 MB | 英文 | [点击下载](https://paddleocr.bj.bcebos.com/20-09-22/mobile-slim/en/en_ppocr_mobile_v1.1_rec_quant_opt.nb) |

### 2.3 文本方向分类模型

| 模型名称 | 模型类型 | 模型大小 | 下载链接 |
|---------|---------|---------|---------|
| cls_chinese_infer | 文本方向分类模型 | 0.17 MB | [点击下载](https://paddleocr.bj.bcebos.com/20-09-22/mobile/lite/ch_ppocr_mobile_v1.1_cls_opt.nb) |

### 2.4 字典文件

| 文件名称 | 文件类型 | 文件大小 | 下载链接 |
|---------|---------|---------|---------|
| ppocr_keys_v1.txt | 字符字典文件 | 25.6 KB | [点击下载](https://paddleocr.bj.bcebos.com/dygraph_v2.0/ch/ch_ppocr_mobile_v2.0_cls_infer.tar) |

## 3. 模型文件结构

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

## 4. 模型使用方法

### 4.1 初始化PaddleOCR

在应用启动时，OCRHelper类会自动初始化PaddleOCR，并从assets目录复制模型文件到应用的私有目录。

### 4.2 切换OCR接口

在设置界面中，您可以选择使用百度OCR或PaddleOCR接口：

- 百度OCR：需要网络连接，支持中英文识别
- PaddleOCR：离线使用，支持中英文识别

### 4.3 切换识别语言

在设置界面中，您可以选择识别语言：

- 中文识别：使用中文识别模型
- 英文识别：使用英文识别模型

### 4.4 模型切换逻辑

当您切换识别语言时，应用会自动重新初始化PaddleOCR，并加载相应的识别模型：

```java
// 根据当前语言选择识别模型
if (currentOCRLanguage == OCRLanguageType.CHINESE) {
    ocrConfig.setRecModelDir(modelPath + File.separator + REC_MODEL_NAME);
} else {
    ocrConfig.setRecModelDir(modelPath + File.separator + REC_ENGLISH_MODEL_NAME);
}
```

## 5. 性能优化建议

1. **模型选择**：根据实际需求选择合适的模型，英文识别可以选择更小的英文专用模型
2. **参数调整**：可以通过调整OCRConfig中的参数来平衡准确率和速度
3. **图片预处理**：在进行OCR识别前，可以对图片进行适当的预处理，如调整亮度、对比度等

## 6. 注意事项

1. 首次使用PaddleOCR时，应用会自动复制模型文件到应用私有目录，可能需要一些时间
2. 模型文件较大，建议在应用发布时使用APK分包或动态下载的方式减少APK大小
3. 不同设备的性能不同，识别速度可能会有所差异

## 7. 版本更新记录

- v1.0.0：首次集成PaddleOCR Slim模型，支持中英文识别和模型切换功能

---

如有任何问题或建议，请随时联系我们的开发团队。