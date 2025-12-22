# Sherpa ONNX TTS

简单的中文 TTS (文字转语音) Android 应用。

## 功能

- 输入文字，点击按钮朗读
- 支持中文语音合成
- 模型文件存储在 `/data/data/com.k2fsa.sherpa.onnx.tts/files/models/tts/`

## 环境

- AGP: 8.4.0
- Kotlin: 1.9.23
- Gradle: 8.13
- Java: 17
- compileSdk: 34

## 快速开始

### 1. 下载 JNI 库

```bash
cd SherpaOnnxTts
./download-libs.sh
```

### 2. 构建 APK

```bash
./gradlew assembleDebug
```

### 3. 准备模型文件

```bash
# 下载模型
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-melo-tts-zh_en.tar.bz2
tar xf vits-melo-tts-zh_en.tar.bz2

# 安装 APK
adb install app/build/outputs/apk/debug/app-debug.apk

# 推送模型文件到设备
adb push vits-melo-tts-zh_en/model.onnx /data/data/com.k2fsa.sherpa.onnx.tts/files/models/tts/vits-melo-tts-zh_en/
adb push vits-melo-tts-zh_en/lexicon.txt /data/data/com.k2fsa.sherpa.onnx.tts/files/models/tts/vits-melo-tts-zh_en/
adb push vits-melo-tts-zh_en/tokens.txt /data/data/com.k2fsa.sherpa.onnx.tts/files/models/tts/vits-melo-tts-zh_en/
```

### 4. 使用

1. 打开应用
2. 在文本框中输入要朗读的文字
3. 点击"开始朗读"按钮
