# SherpaOnnxTts - 中文 TTS Android 应用

基于 sherpa-onnx 的 Android 文本转语音（TTS）应用，支持中文语音合成。

## 项目结构

```
SherpaOnnxTts/
├── app/                    # Android 应用模块（可运行的 Demo）
├── library/                # TTS 库模块（可打包成 AAR）
├── download-libs.sh        # 下载 JNI 库脚本
├── build-library-aar.sh    # 构建 AAR 包脚本
├── AAR_USAGE.md           # AAR 使用文档
└── README.md              # 本文件
```

## 快速开始

### 1. 下载依赖库

```bash
cd SherpaOnnxTts
./download-libs.sh
```

### 2. 准备模型文件

```bash
# 下载 Matcha 模型（推荐）
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/matcha-icefall-zh-baker.tar.bz2
tar -xjf matcha-icefall-zh-baker.tar.bz2
```

### 3. 运行 Demo 应用

```bash
# 构建
gradle :app:assembleDebug

# 安装
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 推送模型
adb shell mkdir -p /data/data/com.k2fsa.sherpa.onnx.tts/files/models/tts
adb push matcha-icefall-zh-baker /data/data/com.k2fsa.sherpa.onnx.tts/files/models/tts/
```

### 4. 打包 AAR 库

```bash
./build-library-aar.sh
```

详细使用方法请查看 [AAR_USAGE.md](AAR_USAGE.md)

## 环境要求

- Android SDK: 34
- Min SDK: 21
- Gradle: 8.13+
- Java: 17
- Kotlin: 1.9.23
- AGP: 8.4.0
