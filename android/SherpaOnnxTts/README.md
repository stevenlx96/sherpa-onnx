# Sherpa ONNX TTS

简单的中文 TTS (文字转语音) Android 应用示例。

## 功能

- 输入文字，点击按钮朗读
- 支持中文语音合成
- 模型文件存储在 `/data/data/com.k2fsa.sherpa.onnx.tts/files/models/tts/`

## 构建环境

- AGP: 8.4.0
- Kotlin: 1.9.23
- Gradle: 8.13
- Java: 17
- compileSdk: 34
- minSdk: 21
- targetSdk: 34

## 构建步骤

```bash
cd android/SherpaOnnxTts
./gradlew assembleDebug
```

## 准备模型文件

1. 下载中文 TTS 模型（推荐 vits-melo-tts-zh_en）：
   ```bash
   wget https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-melo-tts-zh_en.tar.bz2
   tar xf vits-melo-tts-zh_en.tar.bz2
   ```

2. 安装 APK 到设备：
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

3. 推送模型文件到设备：
   ```bash
   adb push vits-melo-tts-zh_en/model.onnx /data/data/com.k2fsa.sherpa.onnx.tts/files/models/tts/vits-melo-tts-zh_en/
   adb push vits-melo-tts-zh_en/lexicon.txt /data/data/com.k2fsa.sherpa.onnx.tts/files/models/tts/vits-melo-tts-zh_en/
   adb push vits-melo-tts-zh_en/tokens.txt /data/data/com.k2fsa.sherpa.onnx.tts/files/models/tts/vits-melo-tts-zh_en/
   ```

## 使用方法

1. 启动应用
2. 在文本框中输入要朗读的中文文字
3. 点击"开始朗读"按钮
4. 点击"停止"按钮可以中断朗读

## 依赖

项目使用 Maven 依赖自动获取 sherpa-onnx JNI 库：

```kotlin
implementation("com.k2fsa:sherpa-onnx:1.10.30")
```

Gradle 会自动从 Maven 仓库下载包含 JNI 库的 AAR 包。
