# Sherpa ONNX Simple TTS

一个简单的中文 TTS (文字转语音) Android 应用示例。

## 功能特点

- 支持中文文字转语音
- 简单的UI界面：输入文字，点击按钮朗读
- 使用 sherpa-onnx TTS 引擎
- 模型文件存储在 `/data/data/com.k2fsa.sherpa.onnx.tts/files/models/tts/`

## 构建环境

- AGP: 8.4.0
- Kotlin: 1.9.23
- Gradle: 8.6
- Java: 17
- compileSdk: 34
- minSdk: 21
- targetSdk: 34

## 设置步骤

### 1. 获取 JNI 库文件

有两种方式获取所需的 JNI 库文件：

#### 方式一：运行下载脚本（推荐）

```bash
cd android/SherpaOnnxSimpleTts
./download_jni_libs.sh
```

#### 方式二：从项目根目录构建

如果下载脚本失败，可以从项目根目录运行构建脚本：

```bash
# 返回项目根目录
cd ../..

# 构建各个架构的库
./build-android-arm64-v8a.sh
./build-android-armv7-eabi.sh
./build-android-x86-64.sh
./build-android-x86.sh
```

构建完成后，.so 文件会在以下目录：
- `build-android-arm64-v8a/install/lib/`
- `build-android-armv7-eabi/install/lib/`
- `build-android-x86-64/install/lib/`
- `build-android-x86/install/lib/`

然后复制到项目的 jniLibs 目录：

```bash
cd android/SherpaOnnxSimpleTts

# 复制 arm64-v8a
cp ../../build-android-arm64-v8a/install/lib/*.so app/src/main/jniLibs/arm64-v8a/

# 复制 armeabi-v7a
cp ../../build-android-armv7-eabi/install/lib/*.so app/src/main/jniLibs/armeabi-v7a/

# 复制 x86_64
cp ../../build-android-x86-64/install/lib/*.so app/src/main/jniLibs/x86_64/

# 复制 x86
cp ../../build-android-x86/install/lib/*.so app/src/main/jniLibs/x86/
```

#### 方式三：从 GitHub Releases 手动下载

1. 访问 [sherpa-onnx Releases](https://github.com/k2-fsa/sherpa-onnx/releases)
2. 下载 `sherpa-onnx-v{version}-android.tar.bz2`
3. 解压后，将 `jni/` 目录下的 .so 文件复制到 `app/src/main/jniLibs/` 对应的架构目录

### 2. 准备 TTS 模型文件

1. 下载中文 TTS 模型（推荐 vits-melo-tts-zh_en）：
   - model.onnx
   - lexicon.txt
   - tokens.txt

2. 在应用安装后，将模型文件放到设备的以下目录：
   ```
   /data/data/com.k2fsa.sherpa.onnx.tts/files/models/tts/vits-melo-tts-zh_en/
   ```

   使用 adb 命令：
   ```bash
   adb push model.onnx /data/data/com.k2fsa.sherpa.onnx.tts/files/models/tts/vits-melo-tts-zh_en/
   adb push lexicon.txt /data/data/com.k2fsa.sherpa.onnx.tts/files/models/tts/vits-melo-tts-zh_en/
   adb push tokens.txt /data/data/com.k2fsa.sherpa.onnx.tts/files/models/tts/vits-melo-tts-zh_en/
   ```

### 3. 构建 APK

```bash
./gradlew assembleDebug
```

或者在 Android Studio 中打开项目并运行。

## 使用方法

1. 启动应用
2. 在文本框中输入要朗读的中文文字
3. 点击"开始朗读"按钮
4. 点击"停止"按钮可以中断朗读

## 项目结构

```
SherpaOnnxSimpleTts/
├── app/
│   ├── src/main/
│   │   ├── java/com/k2fsa/sherpa/onnx/tts/
│   │   │   ├── MainActivity.kt    # 主活动
│   │   │   └── Tts.kt             # TTS API 封装
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   └── activity_main.xml  # UI 布局
│   │   │   └── values/
│   │   │       └── strings.xml        # 字符串资源
│   │   ├── jniLibs/                   # JNI 库文件
│   │   │   ├── arm64-v8a/
│   │   │   ├── armeabi-v7a/
│   │   │   ├── x86/
│   │   │   └── x86_64/
│   │   └── AndroidManifest.xml
│   ├── build.gradle
│   └── proguard-rules.pro
├── build.gradle
├── settings.gradle
└── download_jni_libs.sh           # JNI 库下载脚本
```

## 故障排除

### 应用崩溃：UnsatisfiedLinkError

错误信息：`library 'libsherpa-onnx-jni.so' not found`

解决方法：请确保已按照"获取 JNI 库文件"步骤操作，将 .so 文件放到正确的位置。

### 模型加载失败

错误信息：模型文件未找到

解决方法：
1. 确认模型文件已正确放置在 `/data/data/com.k2fsa.sherpa.onnx.tts/files/models/tts/vits-melo-tts-zh_en/` 目录
2. 使用 `adb shell ls /data/data/com.k2fsa.sherpa.onnx.tts/files/models/tts/vits-melo-tts-zh_en/` 验证文件是否存在

## 许可证

遵循 sherpa-onnx 项目的许可证。
