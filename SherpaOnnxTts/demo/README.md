# SherpaOnnx TTS Demo - AAR 使用示例

这是一个使用 SherpaOnnx TTS AAR 库的独立示例项目。

## 项目特点

- ✅ 完全独立的项目（不依赖主项目源码）
- ✅ 使用编译好的 AAR 文件
- ✅ 简洁的 API 调用（TtsManager）
- ✅ 完整的错误处理和提示
- ✅ 支持 Matcha 和 VITS 模型

## 快速开始

### 1. 准备 AAR 文件

首先需要从主项目构建并复制 AAR：

```bash
# 回到主项目目录
cd ..

# 构建 AAR（如果还没有）
./build-library-aar.sh

# 复制 AAR 到 demo/libs/
./copy-aar-to-demo.sh
```

### 2. 准备模型文件

下载并解压 TTS 模型：

```bash
# 下载 Matcha 模型（推荐）
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/matcha-icefall-zh-baker.tar.bz2
tar -xjf matcha-icefall-zh-baker.tar.bz2
```

### 3. 构建 Demo 应用

```bash
# 构建 APK
gradle :app:assembleDebug
```

### 4. 安装到设备

```bash
# 安装应用
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 推送模型文件到设备
adb shell mkdir -p /data/data/com.example.demo.tts/files/models/tts
adb push matcha-icefall-zh-baker /data/data/com.example.demo.tts/files/models/tts/
```

### 5. 运行应用

在设备上打开 "Sherpa TTS Demo" 应用，输入文字后点击"开始朗读"即可。

## 项目结构

```
demo/
├── app/                          # 应用模块
│   ├── src/main/
│   │   ├── java/com/example/demo/tts/
│   │   │   └── MainActivity.kt   # 主活动（使用 TtsManager）
│   │   ├── res/                  # 资源文件
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── libs/                         # AAR 文件目录
│   └── library-release.aar       # TTS AAR（需要复制）
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## 核心代码

### 初始化 TTS

```kotlin
val ttsManager = TtsManager(context)
ttsManager.initialize("matcha-icefall-zh-baker", "matcha")
```

### 朗读文本

```kotlin
ttsManager.speak(
    text = "你好，世界",
    speed = 0.8f,  // 语速
    sid = 0        // 说话人 ID
)
```

### 停止朗读

```kotlin
ttsManager.stop()
```

### 释放资源

```kotlin
ttsManager.release()
```

## 切换模型

### 使用 VITS 模型

1. 下载 VITS 模型：
```bash
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-melo-tts-zh_en.tar.bz2
tar -xjf vits-melo-tts-zh_en.tar.bz2
adb push vits-melo-tts-zh_en /data/data/com.example.demo.tts/files/models/tts/
```

2. 修改 MainActivity.kt：
```kotlin
ttsManager.initialize("vits-melo-tts-zh_en", "vits")
```

## 环境要求

- **Android SDK**: 34
- **Min SDK**: 21
- **Gradle**: 8.13+
- **Java**: 17
- **Kotlin**: 1.9.23

## 常见问题

### Q: AAR 文件在哪里？

A: 需要先从主项目构建 AAR，然后使用 `copy-aar-to-demo.sh` 脚本复制到 `demo/libs/` 目录。

### Q: 应用崩溃或初始化失败？

A: 请确保：
1. 模型文件已正确推送到设备
2. 路径正确：`/data/data/com.example.demo.tts/files/models/tts/matcha-icefall-zh-baker/`
3. 模型文件完整（model-steps-3.onnx, lexicon.txt, tokens.txt, dict/）

### Q: 音量太小？

A: TtsManager 已将音量设置为最大，如果仍然太小请检查设备音量设置。

### Q: 如何调整语速？

A: 修改 `speak()` 方法的 `speed` 参数，范围 0.5-2.0。

## 参考资料

- [主项目 README](../README.md)
- [AAR 使用文档](../AAR_USAGE.md)
- [sherpa-onnx 官方文档](https://k2-fsa.github.io/sherpa/onnx/tts/index.html)

## 许可证

本项目基于 sherpa-onnx，遵循其开源许可证。
