# 简单中文 TTS Demo

这是一个简单的 Android 中文语音合成（Text-to-Speech）示例项目，基于 sherpa-onnx 实现。

## 功能特性

- ✅ 支持中文朗读
- ✅ 简洁的用户界面
- ✅ 实时语音播放
- ✅ 支持停止朗读
- ✅ 使用 vits-melo-tts-zh_en 模型（支持中英文混合）

## 环境要求

- Android Studio Arctic Fox (2020.3.1) 或更高版本
- Android SDK API 21 (Android 5.0) 或更高版本
- JDK 8 或更高版本

## 模型下载与配置

### 🚀 快速配置脚本（推荐）

如果你已经安装了应用，可以使用以下一键配置脚本：

```bash
#!/bin/bash
# 快速配置脚本 - setup_model.sh

# 1. 下载模型（如果还没下载）
if [ ! -d "vits-melo-tts-zh_en" ]; then
    echo "正在下载模型..."
    wget https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-melo-tts-zh_en.tar.bz2
    tar -xjf vits-melo-tts-zh_en.tar.bz2
    rm vits-melo-tts-zh_en.tar.bz2
fi

# 2. 创建目录
echo "创建目录..."
adb shell mkdir -p /data/data/com.k2fsa.sherpa.onnx.simpletts/files/models/tts/vits-melo-tts-zh_en

# 3. 推送模型文件
echo "推送模型文件..."
adb push vits-melo-tts-zh_en/model.onnx /data/data/com.k2fsa.sherpa.onnx.simpletts/files/models/tts/vits-melo-tts-zh_en/
adb push vits-melo-tts-zh_en/lexicon.txt /data/data/com.k2fsa.sherpa.onnx.simpletts/files/models/tts/vits-melo-tts-zh_en/
adb push vits-melo-tts-zh_en/tokens.txt /data/data/com.k2fsa.sherpa.onnx.simpletts/files/models/tts/vits-melo-tts-zh_en/

# 4. 验证
echo "验证文件..."
adb shell ls -lh /data/data/com.k2fsa.sherpa.onnx.simpletts/files/models/tts/vits-melo-tts-zh_en/

echo "✅ 配置完成！现在可以启动应用了。"
```

保存为 `setup_model.sh` 并运行：
```bash
chmod +x setup_model.sh
./setup_model.sh
```

---

### 步骤 1: 下载 TTS 模型

下载 **vits-melo-tts-zh_en** 模型：

```bash
# 下载模型文件
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-melo-tts-zh_en.tar.bz2

# 解压
tar -xjf vits-melo-tts-zh_en.tar.bz2
```

或者直接访问：https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models

### 步骤 2: 配置模型文件

**重要**: 模型文件需要放在设备的 **应用私有目录** 中，而不是打包在 APK 里。

#### 方法 1: 使用 adb 推送（推荐开发时使用）

```bash
# 安装应用后，使用 adb 推送模型文件
adb shell mkdir -p /data/data/com.k2fsa.sherpa.onnx.simpletts/files/models/tts/vits-melo-tts-zh_en

adb push vits-melo-tts-zh_en/model.onnx /data/data/com.k2fsa.sherpa.onnx.simpletts/files/models/tts/vits-melo-tts-zh_en/
adb push vits-melo-tts-zh_en/lexicon.txt /data/data/com.k2fsa.sherpa.onnx.simpletts/files/models/tts/vits-melo-tts-zh_en/
adb push vits-melo-tts-zh_en/tokens.txt /data/data/com.k2fsa.sherpa.onnx.simpletts/files/models/tts/vits-melo-tts-zh_en/
```

#### 方法 2: 应用内下载（生产环境推荐）

在实际应用中，可以让应用首次运行时从网络下载模型到 `files/models/tts/` 目录。

#### 方法 3: 使用 SD 卡（需要权限）

也可以先将模型放到 SD 卡，然后在应用中复制到私有目录。

### 步骤 3: 验证文件路径

模型文件应该在以下位置：

```
/data/data/com.k2fsa.sherpa.onnx.simpletts/files/models/tts/vits-melo-tts-zh_en/
├── model.onnx          # 主模型文件（必需）
├── lexicon.txt         # 词典文件（必需）
└── tokens.txt          # 标记文件（必需）
```

使用 adb 验证：
```bash
adb shell ls -lh /data/data/com.k2fsa.sherpa.onnx.simpletts/files/models/tts/vits-melo-tts-zh_en/
```

## 构建与运行

### 方法 1: 使用 Android Studio

1. 用 Android Studio 打开项目
2. 等待 Gradle 同步完成
3. 连接 Android 设备或启动模拟器
4. 点击 "Run" 按钮 (▶️)

### 方法 2: 使用命令行

```bash
# 进入项目目录
cd android/SherpaOnnxSimpleTts

# 构建 Debug APK
./gradlew assembleDebug

# 或者直接安装到设备
./gradlew installDebug
```

构建完成后，APK 文件位于：
```
app/build/outputs/apk/debug/app-debug.apk
```

## 使用说明

1. 启动应用
2. 在文本框中输入要朗读的中文文字
3. 点击 "🔊 朗读" 按钮开始朗读
4. 点击 "⏹ 停止" 按钮可以随时停止朗读

## 更换其他中文 TTS 模型

如果你想使用其他中文 TTS 模型，可以修改 `MainActivity.kt` 中的 `initTts()` 函数：

### 选项 1: vits-icefall-zh-aishell3（多说话人）

下载地址：https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-icefall-zh-aishell3.tar.bz2

```kotlin
val modelDir = "vits-icefall-zh-aishell3"
val modelName = "model.onnx"
val lexicon = "lexicon.txt"
val ruleFars = "vits-icefall-zh-aishell3/rule.far"
```

### 选项 2: matcha-icefall-zh-baker（女声）

下载地址：https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/matcha.html

```kotlin
val modelDir = "matcha-icefall-zh-baker"
val acousticModelName = "model-steps-3.onnx"
val vocoder = "vocos-22khz-univ.onnx"  // 需要单独下载 vocoder
val lexicon = "lexicon.txt"
```

## 项目结构

```
SherpaOnnxSimpleTts/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── assets/                 # 模型文件目录
│   │       ├── java/
│   │       │   └── com/k2fsa/sherpa/onnx/simpletts/
│   │       │       ├── MainActivity.kt # 主活动
│   │       │       └── Tts.kt          # TTS API 封装
│   │       ├── jniLibs/                # Native 库
│   │       ├── res/
│   │       │   ├── layout/
│   │       │   │   └── activity_main.xml
│   │       │   └── values/
│   │       │       └── strings.xml
│   │       └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
├── settings.gradle
└── README.md
```

## 常见问题

### Q1: 应用启动时崩溃

**原因**：可能是模型文件未正确放置。

**解决方法**：
1. 检查 `app/src/main/assets/vits-melo-tts-zh_en/` 目录是否存在
2. 确认 `model.onnx`, `lexicon.txt`, `tokens.txt` 三个文件都存在
3. 查看 Logcat 日志获取详细错误信息

### Q2: 朗读时没有声音

**原因**：可能是设备音量设置问题。

**解决方法**：
1. 检查设备媒体音量
2. 尝试使用耳机测试
3. 查看 Logcat 日志中是否有错误信息

### Q3: 支持哪些设备架构？

当前项目包含以下架构的 JNI 库：
- ✅ arm64-v8a (64位 ARM，大多数现代手机)
- ✅ armeabi-v7a (32位 ARM)
- ✅ x86 (32位 x86 模拟器)
- ✅ x86_64 (64位 x86 模拟器)

### Q4: 如何集成到我的 LLM 项目？

你可以将 TTS 功能作为模块集成：

1. 复制 `Tts.kt` 和 `jniLibs` 到你的项目
2. 在你的 Activity 中初始化 TTS：
   ```kotlin
   val tts = OfflineTts(assetManager = assets, config = config)
   ```
3. 朗读 LLM 生成的文本：
   ```kotlin
   val llmOutput = "这是 LLM 生成的文本"
   tts.generateWithCallback(text = llmOutput, sid = 0, speed = 1.0f, callback = ::audioCallback)
   ```

## 相关链接

- [sherpa-onnx 官方文档](https://k2-fsa.github.io/sherpa/onnx/)
- [TTS 模型下载](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models)
- [更多中文模型](https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/index.html)

## 许可证

本项目基于 sherpa-onnx，遵循 Apache 2.0 许可证。

## 作者

基于 sherpa-onnx TTS 示例项目简化而来。
