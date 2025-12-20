# PaddleOCR集成说明

本项目已集成PaddleOCR功能，用于实现本地OCR识别。要使用PaddleOCR功能，需要下载并配置相应的模型文件。

## 1. 下载PaddleOCR模型文件

请从PaddleOCR官方仓库下载以下模型文件：

### 必要的模型文件：
1. **检测模型**：det_chinese_db_infer
2. **识别模型**：rec_chinese_common_infer
3. **分类模型**：cls_chinese_infer
4. **标签文件**：ppocr_keys_v1.txt

### 下载方式：

#### 方式一：直接从GitHub下载
访问PaddleOCR官方GitHub仓库：https://github.com/PaddlePaddle/PaddleOCR

在仓库的`inference`目录下可以找到预训练模型。

#### 方式二：使用命令行下载
```bash
# 创建模型存储目录
mkdir -p paddleocr

# 下载检测模型
git clone https://github.com/PaddlePaddle/PaddleOCR/tree/release/2.6/inference/det_chinese_db_infer paddleocr/det_chinese_db_infer

# 下载识别模型
git clone https://github.com/PaddlePaddle/PaddleOCR/tree/release/2.6/inference/rec_chinese_common_infer paddleocr/rec_chinese_common_infer

# 下载分类模型
git clone https://github.com/PaddlePaddle/PaddleOCR/tree/release/2.6/inference/cls_chinese_infer paddleocr/cls_chinese_infer

# 下载标签文件
wget -P paddleocr https://github.com/PaddlePaddle/PaddleOCR/blob/release/2.6/ppocr/utils/ppocr_keys_v1.txt
```

## 2. 配置模型文件

下载完成后，需要将模型文件放置在项目的`assets/paddleocr`目录下。目录结构如下：

```
assets/
└── paddleocr/
    ├── det_chinese_db_infer/
    │   ├── model.nb
    │   └── params
    ├── rec_chinese_common_infer/
    │   ├── model.nb
    │   └── params
    ├── cls_chinese_infer/
    │   ├── model.nb
    │   └── params
    └── ppocr_keys_v1.txt
```

## 3. 注意事项

1. **模型格式**：确保下载的模型文件是`.nb`格式（Paddle Lite格式），而不是`.pdmodel`格式（PaddlePaddle格式）。如果下载的是`.pdmodel`格式，需要使用Paddle Lite的转换工具将其转换为`.nb`格式。

2. **模型版本**：推荐使用PaddleOCR v3.3版本的模型，该版本与Paddle Lite兼容良好。

3. **首次运行**：应用首次运行时，会自动将模型文件从assets目录复制到应用的私有目录中。

4. **内存占用**：PaddleOCR模型需要一定的内存空间，建议在配置较低的设备上关闭使用GPU加速。

## 4. 切换OCR接口

在应用的设置页面中，可以选择使用百度OCR或PaddleOCR接口：

1. 打开应用的"设置"页面
2. 在"OCR接口选择"部分选择所需的OCR接口
3. 保存设置后，应用将使用所选的OCR接口进行识别

## 5. 故障排除

### 问题：PaddleOCR初始化失败
**解决方法**：
- 检查模型文件是否正确放置在`assets/paddleocr`目录下
- 确保模型文件格式正确（使用`.nb`格式）
- 检查应用是否有读取assets目录的权限

### 问题：识别结果不准确
**解决方法**：
- 确保使用的是最新版本的模型文件
- 调整图片质量，确保文字清晰可见
- 在光线充足的环境下使用

### 问题：应用崩溃或卡顿
**解决方法**：
- 关闭GPU加速（在代码中设置`ocrConfig.setUseGPU(false)`）
- 调整识别参数，降低检测和识别的阈值
- 确保设备有足够的内存空间

## 6. 参考链接

- [PaddleOCR官方文档](https://github.com/PaddlePaddle/PaddleOCR/blob/release/2.6/doc/doc_ch/quickstart.md)
- [Paddle Lite官方文档](https://paddle-lite.readthedocs.io/zh/latest/)
- [PaddleOCR Android集成指南](https://github.com/PaddlePaddle/PaddleOCR/blob/release/2.6/doc/doc_ch/android.md)