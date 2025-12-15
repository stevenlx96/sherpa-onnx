# 实时流式语音识别 Android 应用

基于 [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) 的独立Android项目，实现实时流式语音识别功能。

## ✨ 功能特性

- ✅ **实时流式语音识别** - 边说边识别，实时显示结果
- ✅ **语义断句** - 基于标点符号的智能断句，优于静音检测
- ✅ **同音字纠正** (可选) - 自动纠正识别错误，如 "在坐" → "在座"
- ✅ **自我修正检测** - 实时监测模型的文本修正行为
- ✅ **PCM音频缓存** - 自动保存录音的PCM格式数据
- ✅ **内部存储模型** - 模型存储在 `/data/data/com.example.streamingasr/files/models/`
- ✅ **ARM架构优化** - 专为 ARM64 和 ARMv7 优化

## 📋 技术规格

- **Target SDK**: 34 (Android 14)
- **Min SDK**: 24 (Android 7.0)
- **Java版本**: 17
- **音频格式**: PCM 16-bit, 单声道
- **采样率**: 16000 Hz
- **语言**: Kotlin

## 🚀 快速开始

### 1. 下载JNI库文件

运行提供的脚本自动下载预编译的sherpa-onnx库：

```bash
cd StreamingASRApp
./download-libs.sh
```

或者手动下载：
1. 访问 [sherpa-onnx Releases](https://github.com/k2-fsa/sherpa-onnx/releases)
2. 下载 `sherpa-onnx-{version}.aar`
3. 解压AAR文件（它是一个ZIP文件）
4. 将 `jni/` 目录下的所有 `.so` 文件复制到 `app/src/main/jniLibs/` 对应的架构目录

### 2. 下载语音识别模型

推荐使用中英文双语模型：

```bash
# 下载模型
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2

# 解压
tar xvf sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2

# 获取需要的文件
cd sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20/
ls -lh
# 需要的文件:
# - encoder-epoch-99-avg-1.onnx
# - decoder-epoch-99-avg-1.onnx
# - joiner-epoch-99-avg-1.onnx
# - tokens.txt
```

### 3. 安装应用并推送模型

```bash
# 编译并安装应用
cd StreamingASRApp
./gradlew installDebug

# 推送模型文件到设备
adb push encoder-epoch-99-avg-1.onnx /data/local/tmp/
adb push decoder-epoch-99-avg-1.onnx /data/local/tmp/
adb push joiner-epoch-99-avg-1.onnx /data/local/tmp/
adb push tokens.txt /data/local/tmp/

# 移动到应用内部存储
adb shell
su  # 如果设备已root
# 或者使用 run-as 命令 (无需root)
run-as com.example.streamingasr
mkdir -p /data/data/com.example.streamingasr/files/models/
cp /data/local/tmp/encoder-epoch-99-avg-1.onnx /data/data/com.example.streamingasr/files/models/
cp /data/local/tmp/decoder-epoch-99-avg-1.onnx /data/data/com.example.streamingasr/files/models/
cp /data/local/tmp/joiner-epoch-99-avg-1.onnx /data/data/com.example.streamingasr/files/models/
cp /data/local/tmp/tokens.txt /data/data/com.example.streamingasr/files/models/
exit
```

**更简单的方法（推荐）**：

如果你的设备支持，可以在应用中添加文件选择功能，让用户通过UI复制模型文件。或者将模型文件放在assets目录中（但这会增大APK体积）。

### 4. (可选) 部署同音字纠正功能

HomophoneReplacer可以自动纠正常见的同音字错误，提升识别准确度。

**一键部署**（推荐）：

```bash
cd StreamingASRApp
./download-homophone-files.sh
```

这个脚本会自动：
1. 下载 `lexicon.txt` 和 `replace.fst`
2. 推送到设备
3. 复制到应用目录

**手动部署**：

```bash
# 下载文件
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/hr-files/lexicon.txt
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/hr-files/replace.fst

# 推送到设备
adb push lexicon.txt /data/local/tmp/
adb push replace.fst /data/local/tmp/

# 复制到应用目录
adb shell "run-as com.example.streamingasr cp /data/local/tmp/lexicon.txt /data/data/com.example.streamingasr/files/models/"
adb shell "run-as com.example.streamingasr cp /data/local/tmp/replace.fst /data/data/com.example.streamingasr/files/models/"
```

**功能示例**：
- ❌ "在坐的各位" → ✅ "在座的各位"
- ❌ "因该这样做" → ✅ "应该这样做"
- ❌ "在线在坐" → ✅ "在线在座"

**注意**：
- 如果不部署这些文件，应用仍然可以正常工作，只是没有同音字纠正功能
- HomophoneReplacer基于FST规则，速度很快，不会影响流式识别性能

### 5. 运行应用

1. 打开应用
2. 授予录音权限
3. 等待模型加载完成（查看日志确认是否启用HomophoneReplacer）
4. 点击"开始识别"按钮开始录音和识别
5. 说话，实时查看识别结果
   - 识别结果会根据标点符号自动断句
   - 如果启用了HomophoneReplacer，同音字错误会被自动纠正
   - 模型的自我修正行为会在日志中显示（🔄标记）
6. 点击"停止识别"结束
7. 可以使用"清除"按钮清空识别结果

## 📁 项目结构

```
StreamingASRApp/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/
│   │   │   │   ├── example/streamingasr/
│   │   │   │   │   ├── MainActivity.kt          # 主Activity
│   │   │   │   │   ├── AudioRecorder.kt         # 音频录制（支持PCM缓存）
│   │   │   │   │   └── ModelManager.kt          # 模型管理
│   │   │   │   └── k2fsa/sherpa/onnx/
│   │   │   │       ├── OnlineRecognizer.kt      # 在线识别器
│   │   │   │       ├── OnlineStream.kt          # 音频流
│   │   │   │       ├── FeatureConfig.kt         # 特征配置
│   │   │   │       └── ...
│   │   │   ├── res/
│   │   │   │   ├── layout/
│   │   │   │   │   └── activity_main.xml        # 主界面布局
│   │   │   │   └── values/
│   │   │   │       ├── strings.xml
│   │   │   │       ├── colors.xml
│   │   │   │       └── themes.xml
│   │   │   ├── jniLibs/                         # JNI库文件（需下载）
│   │   │   │   ├── arm64-v8a/
│   │   │   │   │   └── libsherpa-onnx-jni.so
│   │   │   │   ├── armeabi-v7a/
│   │   │   │   ├── x86/
│   │   │   │   └── x86_64/
│   │   │   └── AndroidManifest.xml
│   │   └── build.gradle.kts
│   └── proguard-rules.pro
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── download-libs.sh                              # 下载JNI库文件脚本
├── download-homophone-files.sh                   # 下载同音字纠正文件脚本
└── README.md
```

## 🎯 核心功能说明

### 1. 音频录制与缓存 (AudioRecorder.kt)

```kotlin
val audioRecorder = AudioRecorder(
    sampleRate = 16000,
    cacheDir = File(filesDir, "audio_cache")
)

// 开始录制（自动保存PCM）
audioRecorder.startRecording(savePcm = true)

// 读取音频数据
val samples = audioRecorder.readAudioData()  // FloatArray

// 停止录制
audioRecorder.stopRecording()
```

**PCM缓存位置**:
- PCM文件: `/data/data/com.example.streamingasr/files/audio_cache/audio_{timestamp}.pcm`

### 2. 模型管理 (ModelManager.kt)

```kotlin
val modelManager = ModelManager(context)

// 检查模型是否存在
if (modelManager.checkModelExists(ModelType.ZIPFORMER_TRANSDUCER)) {
    // 创建识别器
    val recognizer = modelManager.createOnlineRecognizer()
}

// 获取模型目录
val modelDir = modelManager.getModelDir()
// 返回: /data/data/com.example.streamingasr/files/models/
```

### 3. 实时识别流程

```kotlin
// 1. 创建识别流
val stream = recognizer.createStream()

// 2. 送入音频数据 (FloatArray)
stream.acceptWaveform(samples, sampleRate = 16000)

// 3. 解码
while (recognizer.isReady(stream)) {
    recognizer.decode(stream)
}

// 4. 获取结果
val result = recognizer.getResult(stream)
val text = result.text  // 识别文本

// 5. 检查句子边界
if (recognizer.isEndpoint(stream)) {
    println("句子结束: $text")
    recognizer.reset(stream)  // 重置，继续下一句
}

// 6. 释放资源
stream.release()
```

## 📦 支持的模型类型

### 1. Zipformer Transducer (推荐 - 中英文双语)
```
模型文件:
- encoder-epoch-99-avg-1.onnx
- decoder-epoch-99-avg-1.onnx
- joiner-epoch-99-avg-1.onnx
- tokens.txt

下载地址:
https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2
```

### 2. Paraformer (中文+英文)
```
模型文件:
- encoder.int8.onnx
- decoder.int8.onnx
- tokens.txt

下载地址:
https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-paraformer-bilingual-zh-en.tar.bz2
```

### 3. Zipformer CTC
```
模型文件:
- model.int8.onnx
- tokens.txt
```

更多模型请访问: https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models

## 🎨 高级功能

### 1. 同音字纠正 (HomophoneReplacer)

**工作原理**：
- 使用FST (Finite State Transducer) 基于规则的替换
- 在每次`getResult()`返回结果前自动应用
- 不影响流式识别性能（FST查找非常快，< 1ms）

**配置文件**：
- `lexicon.txt` - 词典文件，定义发音到文字的映射
- `replace.fst` - 替换规则文件，定义同音字转换规则

**应用场景**：
```
输入：我认为在坐的各位应该因该知道这个问题
输出：我认为在座的各位应该应该知道这个问题
```

**启用/禁用**：
- 文件存在 → 自动启用（日志显示：`HomophoneReplacer enabled`）
- 文件不存在 → 自动禁用（日志显示：`HomophoneReplacer disabled`）

### 2. 语义断句

**实现原理**：
- 优先检测标点符号（。！？.!?）作为句子边界
- 静音检测（endpoint）作为备选方案
- 两者结合，更准确地判断句子结束

**代码位置**：MainActivity.kt:249-256

**优势**：
- 不会在句子中间因为短暂停顿而断句
- 识别结果更符合语义完整性

### 3. 自我修正检测

**功能说明**：
- 监测模型在流式识别过程中的文本修正行为
- 使用公共前缀算法检测变化部分
- 在日志中记录修正事件

**日志示例**：
```
I/MainActivity: 🔄 自我修正检测: "你好" → "你是"
```

**代码位置**：MainActivity.kt:241-247, 298-326

## 🔧 构建配置

### Gradle配置

```kotlin
android {
    compileSdk = 34

    defaultConfig {
        targetSdk = 34
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
```

### 权限配置

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
```

## 🐛 常见问题

### 1. 模型加载失败

**错误**: "Model files not found"

**解决**:
- 确认模型文件已正确复制到 `/data/data/com.example.streamingasr/files/models/`
- 检查文件名是否正确
- 使用 `adb shell run-as com.example.streamingasr ls files/models/` 查看文件

### 2. JNI库加载失败

**错误**: "UnsatisfiedLinkError: libsherpa-onnx-jni.so"

**解决**:
- 运行 `./download-libs.sh` 下载库文件
- 确认 `app/src/main/jniLibs/` 目录下有对应架构的 `.so` 文件

### 3. 录音权限被拒绝

**解决**: 在应用设置中手动授予录音权限

### 4. 识别结果为空

**可能原因**:
- 麦克风音量太小
- 模型与语言不匹配（使用中文模型识别英文）
- 环境噪音过大

## 📱 设备要求

- Android 7.0 (API 24) 或更高版本
- 至少 2GB RAM
- 麦克风权限
- 存储空间（模型大小约 50-200 MB）

## 🔗 相关链接

- [sherpa-onnx 官方仓库](https://github.com/k2-fsa/sherpa-onnx)
- [模型下载](https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models)
- [在线文档](https://k2-fsa.github.io/sherpa/onnx/)

## 📄 许可证

本项目基于 Apache 2.0 许可证。

sherpa-onnx 使用 Apache 2.0 许可证。

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📞 支持

如有问题，请访问:
- [sherpa-onnx Issues](https://github.com/k2-fsa/sherpa-onnx/issues)
- [sherpa-onnx Discussions](https://github.com/k2-fsa/sherpa-onnx/discussions)
