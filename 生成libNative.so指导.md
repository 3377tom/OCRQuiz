# 生成libNative.so的详细指导

## 1. 概述

libNative.so是PaddleOCR的C++实现部分，包含了OCR检测、识别和分类模型的推理逻辑。它通过JNI（Java Native Interface）与Java代码交互，提供高性能的OCR推理能力。

## 2. 环境准备

### 2.1 安装必要的开发工具

- **Android Studio**：用于Android应用开发
- **Android NDK**：用于编译C++代码生成.so库
- **CMake**：跨平台构建系统，用于配置编译过程
- **Git**：用于克隆PaddleOCR源代码

### 2.2 配置环境变量

确保以下环境变量已正确配置：

```bash
# Windows环境示例
set ANDROID_NDK_HOME=D:\Android\Sdk\ndk\25.2.9519653
set CMAKE_HOME=D:\Android\Sdk\cmake\3.22.1\bin
set PATH=%PATH%;%ANDROID_NDK_HOME%;%CMAKE_HOME%
```

## 3. 获取PaddleOCR源代码

### 3.1 克隆PaddleOCR仓库

```bash
git clone https://github.com/PaddlePaddle/PaddleOCR.git
cd PaddleOCR
```

### 3.2 切换到稳定分支

```bash
git checkout release/2.7
```

## 4. 配置编译环境

### 4.1 创建Android构建目录

```bash
mkdir -p build/android
cd build/android
```

### 4.2 下载PaddleLite预编译库

从[PaddleLite官方发布页](https://github.com/PaddlePaddle/Paddle-Lite/releases)下载适合的预编译库，选择Android平台和对应的架构（arm64-v8a、armeabi-v7a等）。

### 4.3 解压PaddleLite库

将下载的PaddleLite库解压到`third_party`目录：

```bash
mkdir -p third_party
unzip paddlelite-android-arm64-v8a-*.zip -d third_party/paddlelite
```

## 5. 创建CMakeLists.txt

在`build/android`目录下创建`CMakeLists.txt`文件，内容如下：

```cmake
cmake_minimum_required(VERSION 3.10)
project(PaddleOCRNative)

# 设置C++标准
set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 设置Android构建参数
set(ANDROID_ABI "arm64-v8a")
set(ANDROID_PLATFORM "android-21")

# 引入PaddleLite
set(PADDLELITE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party/paddlelite)
include_directories(${PADDLELITE_DIR}/include)
link_directories(${PADDLELITE_DIR}/lib)

# 引入OpenCV（可选，如果需要图像处理）
# set(OpenCV_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party/opencv/sdk/native/jni)
# find_package(OpenCV REQUIRED)

# 添加PaddleOCR源代码
aux_source_directory(${CMAKE_CURRENT_SOURCE_DIR}/../../ppocr/src OCR_SOURCES)

# 添加JNI源代码
aux_source_directory(${CMAKE_CURRENT_SOURCE_DIR}/jni JNI_SOURCES)

# 创建共享库
add_library(native SHARED
    ${OCR_SOURCES}
    ${JNI_SOURCES}
)

# 链接依赖库
target_link_libraries(native
    paddle_lite_api_shared
    # ${OpenCV_LIBS}
    log
    android
)
```

## 6. 创建JNI接口文件

在`build/android/jni`目录下创建`ocr_jni.cpp`文件，实现JNI接口：

```cpp
#include <jni.h>
#include <string>
#include <vector>
#include <memory>
#include "paddle_api.h"
#include "ppocr/ocr_det.h"
#include "ppocr/ocr_rec.h"
#include "ppocr/ocr_cls.h"

using namespace paddle::lite_api;

// 全局OCR实例
std::unique_ptr<OCRDetector> g_detector = nullptr;
std::unique_ptr<OCRRecognizer> g_recognizer = nullptr;
std::unique_ptr<OCRClassifier> g_classifier = nullptr;

// JNI方法：初始化OCR
extern "C" JNIEXPORT jlong JNICALL
Java_com_floatingocrquiz_OCRPredictorNative_init(
    JNIEnv *env,
    jobject thiz,
    jstring det_model_path,
    jstring rec_model_path,
    jstring cls_model_path,
    jstring label_path,
    jboolean use_gpu)
{
    // 获取模型路径
    const char* det_path = env->GetStringUTFChars(det_model_path, nullptr);
    const char* rec_path = env->GetStringUTFChars(rec_model_path, nullptr);
    const char* cls_path = env->GetStringUTFChars(cls_model_path, nullptr);
    const char* label_path = env->GetStringUTFChars(label_path, nullptr);
    
    // 初始化PaddleLite
    MobileConfig config;
    config.set_threads(4);
    if (use_gpu) {
        config.set_power_mode(LITE_POWER_HIGH);
        config.set_precision_mode(LITE_PRECISION_HIGH);
    } else {
        config.set_power_mode(LITE_POWER_NORMAL);
        config.set_precision_mode(LITE_PRECISION_FP32);
    }
    
    // 初始化检测器
    g_detector.reset(new OCRDetector());
    g_detector->Init(det_path, config);
    
    // 初始化识别器
    g_recognizer.reset(new OCRRecognizer());
    g_recognizer->Init(rec_path, label_path, config);
    
    // 初始化分类器
    g_classifier.reset(new OCRClassifier());
    g_classifier->Init(cls_path, config);
    
    // 释放资源
    env->ReleaseStringUTFChars(det_model_path, det_path);
    env->ReleaseStringUTFChars(rec_model_path, rec_path);
    env->ReleaseStringUTFChars(cls_model_path, cls_path);
    env->ReleaseStringUTFChars(label_path, label_path);
    
    return reinterpret_cast<jlong>(g_detector.get());
}

// JNI方法：执行OCR推理
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_floatingocrquiz_OCRPredictorNative_forward(
    JNIEnv *env,
    jobject thiz,
    jlong native_pointer,
    jobject bitmap)
{
    if (native_pointer == 0 || g_detector == nullptr || g_recognizer == nullptr || g_classifier == nullptr) {
        return nullptr;
    }
    
    // 处理Bitmap图像
    AndroidBitmapInfo info;
    void* pixels = nullptr;
    AndroidBitmap_getInfo(env, bitmap, &info);
    AndroidBitmap_lockPixels(env, bitmap, &pixels);
    
    // 转换为OpenCV Mat（如果使用OpenCV）
    // cv::Mat img(info.height, info.width, CV_8UC4, pixels);
    
    // 执行OCR推理
    std::vector<OCRResult> results;
    g_detector->Run(pixels, info.width, info.height, info.stride, results);
    
    // 释放Bitmap锁
    AndroidBitmap_unlockPixels(env, bitmap);
    
    // 将结果转换为Java对象数组
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray resultArray = env->NewObjectArray(results.size(), stringClass, nullptr);
    
    for (int i = 0; i < results.size(); i++) {
        std::string text = results[i].text;
        jstring jText = env->NewStringUTF(text.c_str());
        env->SetObjectArrayElement(resultArray, i, jText);
        env->DeleteLocalRef(jText);
    }
    
    return resultArray;
}

// JNI方法：释放资源
extern "C" JNIEXPORT void JNICALL
Java_com_floatingocrquiz_OCRPredictorNative_release(
    JNIEnv *env,
    jobject thiz,
    jlong native_pointer)
{
    g_detector.reset();
    g_recognizer.reset();
    g_classifier.reset();
}
```

## 7. 编译生成libNative.so

### 7.1 配置CMake

```bash
cd build/android
cmake .. \
    -DCMAKE_SYSTEM_NAME=Android \
    -DCMAKE_SYSTEM_VERSION=21 \
    -DCMAKE_ANDROID_ARCH_ABI=arm64-v8a \
    -DCMAKE_ANDROID_NDK=${ANDROID_NDK_HOME} \
    -DCMAKE_ANDROID_STL_TYPE=c++_static \
    -DCMAKE_BUILD_TYPE=Release
```

### 7.2 执行编译

```bash
cmake --build . --config Release
```

编译完成后，libNative.so将生成在`build/android/libarm64-v8a`目录下。

## 8. 支持多架构

为了支持不同的Android设备架构，可以为每种架构单独编译：

```bash
# armeabi-v7a
cmake .. \
    -DCMAKE_SYSTEM_NAME=Android \
    -DCMAKE_SYSTEM_VERSION=21 \
    -DCMAKE_ANDROID_ARCH_ABI=armeabi-v7a \
    -DCMAKE_ANDROID_NDK=${ANDROID_NDK_HOME} \
    -DCMAKE_ANDROID_STL_TYPE=c++_static \
    -DCMAKE_BUILD_TYPE=Release
cmake --build . --config Release

# x86
cmake .. \
    -DCMAKE_SYSTEM_NAME=Android \
    -DCMAKE_SYSTEM_VERSION=21 \
    -DCMAKE_ANDROID_ARCH_ABI=x86 \
    -DCMAKE_ANDROID_NDK=${ANDROID_NDK_HOME} \
    -DCMAKE_ANDROID_STL_TYPE=c++_static \
    -DCMAKE_BUILD_TYPE=Release
cmake --build . --config Release

# x86_64
cmake .. \
    -DCMAKE_SYSTEM_NAME=Android \
    -DCMAKE_SYSTEM_VERSION=21 \
    -DCMAKE_ANDROID_ARCH_ABI=x86_64 \
    -DCMAKE_ANDROID_NDK=${ANDROID_NDK_HOME} \
    -DCMAKE_ANDROID_STL_TYPE=c++_static \
    -DCMAKE_BUILD_TYPE=Release
cmake --build . --config Release
```

## 9. 集成到Android项目

将生成的libNative.so复制到Android项目的`src/main/jniLibs`目录下：

```bash
mkdir -p /path/to/your/project/app/src/main/jniLibs/arm64-v8a
cp build/android/libarm64-v8a/libnative.so /path/to/your/project/app/src/main/jniLibs/arm64-v8a/

# 复制其他架构的.so库
mkdir -p /path/to/your/project/app/src/main/jniLibs/armeabi-v7a
cp build/android/libarmeabi-v7a/libnative.so /path/to/your/project/app/src/main/jniLibs/armeabi-v7a/
```

## 10. 验证集成结果

重新编译并运行Android应用，检查PaddleOCR功能是否正常工作。

## 11. 注意事项

1. **版本兼容性**：确保PaddleOCR、PaddleLite和NDK版本相互兼容
2. **模型文件**：确保使用的.nb模型文件与编译的libNative.so兼容
3. **权限配置**：确保应用具有读取模型文件的权限
4. **性能优化**：可以通过调整线程数、精度模式等参数优化推理性能
5. **内存管理**：确保及时释放JNI资源，避免内存泄漏

## 12. 替代方案

如果编译过程复杂，也可以考虑使用PaddleOCR提供的[预编译库](https://github.com/PaddlePaddle/PaddleOCR/tree/release/2.7/deploy/android)，或者使用PaddleOCR的[Android SDK](https://github.com/PaddlePaddle/PaddleOCR/tree/release/2.7/deploy/android/sdk)。

---

以上是生成libNative.so的详细步骤，希望对您有所帮助。如果在编译过程中遇到问题，可以参考PaddleOCR官方文档或在GitHub上提交issue寻求帮助。