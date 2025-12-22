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

## 构建

```bash
cd SherpaOnnxTts
./gradlew assembleDebug
```

## 使用

1. 安装 APK
2. 准备并推送模型文件
3. 打开应用，输入文字，点击"开始朗读"

## 模型文件

```bash
# 下载模型
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-melo-tts-zh_en.tar.bz2
tar xf vits-melo-tts-zh_en.tar.bz2

# 安装 APK
adb install app/build/outputs/apk/debug/app-debug.apk

# 推送模型文件
adb push vits-melo-tts-zh_en/model.onnx /data/data/com.k2fsa.sherpa.onnx.tts/files/models/tts/vits-melo-tts-zh_en/
adb push vits-melo-tts-zh_en/lexicon.txt /data/data/com.k2fsa.sherpa.onnx.tts/files/models/tts/vits-melo-tts-zh_en/
adb push vits-melo-tts-zh_en/tokens.txt /data/data/com.k2fsa.sherpa.onnx.tts/files/models/tts/vits-melo-tts-zh_en/
```
