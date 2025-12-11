# StreamingASR AAR 打包完整指南

## 📁 项目结构

```
StreamingASRApp/
├── app/                          # ✅ 原始应用（包含完整示例）
│   └── ...
├── library/                      # 🆕 用于打包 AAR 的 module
│   ├── src/
│   │   └── main/
│   │       ├── java/            # 源代码
│   │       │   └── com/example/streamingasr/
│   │       │       ├── SherpaOnnxASR.kt    # 🎯 公共 API 入口
│   │       │       ├── ModelManager.kt     # 模型管理
│   │       │       └── AudioRecorder.kt    # 音频录制
│   │       ├── jniLibs/         # ⚠️ 需要你手动放置 .so 文件
│   │       │   ├── arm64-v8a/
│   │       │   └── armeabi-v7a/
│   │       └── AndroidManifest.xml
│   ├── build.gradle.kts         # Library 配置
│   └── README.md                # 🔥 Library 详细 API 文档
├── build-library-aar.sh         # 🚀 一键构建脚本
├── download-libs.sh             # 📥 下载 native 库脚本
└── AAR打包说明.md               # 本文件
```

## ✨ 核心特性

此 AAR 提供完整的语音识别解决方案：

- 🎤 **实时流式语音识别 (ASR)** - 支持中英文双语
- 🔔 **唤醒词检测 (KWS)** - 小模型低功耗待机
- 🎯 **语音活动检测 (VAD)** - 智能断句
- 🔥 **热词支持 (Hotwords)** - 提高特定词识别准确度
- 🧹 **自动缓存管理** - 防止存储空间耗尽
- 📦 **音频录制和导出** - 完整的音频处理功能

## 🎯 关键特性：模型文件动态加载

**重要：** 此 AAR **不打包**模型文件，而是在运行时从应用数据目录加载模型。

### 模型加载路径（子目录结构）

模型文件需要按类型组织在子目录中：
```
/data/data/<应用包名>/files/models/
├── asr/        - ASR 语音识别模型
├── kws/        - KWS 唤醒词检测模型（可选）
└── vad/        - VAD 语音活动检测模型（可选）
```

例如，如果你的应用包名是 `com.yourcompany.yourapp`，模型路径为：
```
/data/data/com.yourcompany.yourapp/files/models/
├── asr/
│   ├── encoder-epoch-99-avg-1.onnx
│   ├── decoder-epoch-99-avg-1.onnx
│   ├── joiner-epoch-99-avg-1.onnx
│   ├── tokens.txt
│   └── hotwords.txt         # 可选：热词文件
├── kws/
│   ├── encoder-epoch-12-avg-2-chunk-16-left-64.onnx
│   ├── decoder-epoch-12-avg-2-chunk-16-left-64.onnx
│   ├── joiner-epoch-12-avg-2-chunk-16-left-64.onnx
│   ├── tokens.txt
│   └── keywords.txt         # 唤醒词列表
└── vad/
    └── silero_vad.onnx
```

### 优点

- ✅ **AAR 体积小**：只包含代码和 native 库（约 10-50 MB），不包含模型（通常几百 MB）
- ✅ **灵活更新**：模型可以独立更新，无需重新打包 AAR
- ✅ **多应用共享**：不同应用可以使用不同的模型
- ✅ **用户自定义**：用户可以自己选择和替换模型

## 🚀 快速开始

### 第一步：准备 Native 库文件

#### 方法 A：使用下载脚本（推荐）

```bash
cd StreamingASRApp
bash download-libs.sh
```

脚本会自动从 GitHub Releases 下载最新的预编译库并解压到 `app/src/main/jniLibs/`。

#### 方法 B：手动复制

如果已有 `.so` 文件，复制到以下目录：

```bash
# arm64-v8a 架构
cp /path/to/libsherpa-onnx-jni.so library/src/main/jniLibs/arm64-v8a/
cp /path/to/libonnxruntime.so library/src/main/jniLibs/arm64-v8a/

# armeabi-v7a 架构
cp /path/to/libsherpa-onnx-jni.so library/src/main/jniLibs/armeabi-v7a/
cp /path/to/libonnxruntime.so library/src/main/jniLibs/armeabi-v7a/
```

**注意：** 如果 `download-libs.sh` 下载到了 `app/src/main/jniLibs/`，需要同步复制到 `library/src/main/jniLibs/`：

```bash
# 复制 native 库到 library module
cp -r app/src/main/jniLibs/* library/src/main/jniLibs/
```

### 第二步：构建 AAR

#### 方法一：使用一键脚本（推荐）

```bash
cd StreamingASRApp
./build-library-aar.sh
```

脚本会自动：
- ✅ 检查 .so 文件是否存在
- ✅ 清理旧的构建
- ✅ 构建 AAR
- ✅ 显示构建结果和文件大小

#### 方法二：手动构建

```bash
cd StreamingASRApp

# 清理旧构建
./gradlew :library:clean

# 构建 release AAR
./gradlew :library:assembleRelease

# 查看构建结果
ls -lh library/build/outputs/aar/library-release.aar
```

### 第三步：获取 AAR

构建成功后，AAR 文件位于：

```
library/build/outputs/aar/library-release.aar
```

## 📦 使用 AAR

### 在其他项目中集成

#### 1. 复制 AAR 文件

将 `library-release.aar` 复制到目标项目：

```
TargetProject/
└── app/
    └── libs/
        └── library-release.aar  # 放这里
```

#### 2. 配置依赖

在目标项目的 `app/build.gradle.kts` 中：

```kotlin
android {
    // ... 其他配置
}

dependencies {
    // 添加 AAR 依赖
    implementation(files("libs/library-release.aar"))

    // 必需的外部依赖
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

#### 3. 添加权限

在目标项目的 `AndroidManifest.xml` 中：

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
```

#### 4. 准备模型文件

**方法 A：使用 adb 推送模型（推荐）**

```bash
# 推送 ASR 模型到临时目录
adb push encoder-epoch-99-avg-1.onnx /data/local/tmp/
adb push decoder-epoch-99-avg-1.onnx /data/local/tmp/
adb push joiner-epoch-99-avg-1.onnx /data/local/tmp/
adb push tokens.txt /data/local/tmp/

# （可选）推送热词文件
adb push hotwords.txt /data/local/tmp/

# 使用 adb shell 移动到应用目录
adb shell
run-as com.yourcompany.yourapp
mkdir -p files/models/asr
cp /data/local/tmp/*.onnx files/models/asr/
cp /data/local/tmp/tokens.txt files/models/asr/
cp /data/local/tmp/hotwords.txt files/models/asr/  # 可选
exit

# 验证文件已推送
adb shell run-as com.yourcompany.yourapp ls -lh files/models/asr/
```

**推送 KWS 模型（可选）：**

```bash
# 推送 KWS 模型
adb push encoder-kws.onnx /data/local/tmp/
adb push decoder-kws.onnx /data/local/tmp/
adb push joiner-kws.onnx /data/local/tmp/
adb push tokens-kws.txt /data/local/tmp/
adb push keywords.txt /data/local/tmp/

adb shell
run-as com.yourcompany.yourapp
mkdir -p files/models/kws
cp /data/local/tmp/encoder-kws.onnx files/models/kws/encoder-epoch-12-avg-2-chunk-16-left-64.onnx
cp /data/local/tmp/decoder-kws.onnx files/models/kws/decoder-epoch-12-avg-2-chunk-16-left-64.onnx
cp /data/local/tmp/joiner-kws.onnx files/models/kws/joiner-epoch-12-avg-2-chunk-16-left-64.onnx
cp /data/local/tmp/tokens-kws.txt files/models/kws/tokens.txt
cp /data/local/tmp/keywords.txt files/models/kws/keywords.txt
exit
```

**推送 VAD 模型（可选）：**

```bash
adb push silero_vad.onnx /data/local/tmp/

adb shell
run-as com.yourcompany.yourapp
mkdir -p files/models/vad
cp /data/local/tmp/silero_vad.onnx files/models/vad/
exit
```

**方法 B：在应用内下载模型**

```kotlin
// 在你的应用中实现模型下载逻辑
class YourActivity : AppCompatActivity() {
    private suspend fun downloadModels() {
        val modelManager = ModelManager(this)
        val modelDir = modelManager.getModelDir()

        // 下载模型文件到 modelDir
        // 例如从你的服务器下载
        downloadFile("https://yourserver.com/encoder.onnx", File(modelDir, "encoder.onnx"))
        downloadFile("https://yourserver.com/decoder.onnx", File(modelDir, "decoder.onnx"))
        // ...
    }
}
```

**方法 C：从 assets 复制到数据目录**

如果你想在应用首次运行时从 assets 复制模型：

```kotlin
class YourActivity : AppCompatActivity() {
    private fun copyModelsFromAssets() {
        val modelManager = ModelManager(this)
        val modelDir = modelManager.getModelDir()

        // 从你的应用的 assets 复制模型文件
        copyAssetFile("models/encoder.onnx", File(modelDir, "encoder.onnx"))
        copyAssetFile("models/decoder.onnx", File(modelDir, "decoder.onnx"))
        // ...
    }

    private fun copyAssetFile(assetPath: String, destFile: File) {
        assets.open(assetPath).use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
    }
}
```

#### 5. 使用代码

```kotlin
import com.example.streamingasr.SherpaOnnxASR

class YourActivity : AppCompatActivity() {
    private lateinit var asr: SherpaOnnxASR
    private var recognizer: OnlineRecognizer? = null
    private var recorder: AudioRecorder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化 ASR 库
        asr = SherpaOnnxASR(this)

        // 检查模型是否存在
        if (!asr.hasModels()) {
            Log.e(TAG, "模型文件不存在！")
            Log.i(TAG, asr.getModelDownloadInstructions())
            showModelInstructions()
            return
        }

        // 创建识别器（支持热词）
        recognizer = asr.createRecognizer(
            hotwordsFile = "hotwords.txt",  // 可选：热词文件
            hotwordsScore = 1.5f             // 可选：热词权重
        )

        // 创建音频录制器
        recorder = asr.createAudioRecorder()

        // 开始录制（自动清理缓存，默认保留500MB）
        recorder?.startRecording(savePcm = true, cacheInMemory = true)

        // 开始识别
        startRecognition()
    }

    private fun startRecognition() {
        val stream = recognizer?.createStream()

        lifecycleScope.launch(Dispatchers.IO) {
            while (isRecording) {
                val samples = recorder?.readAudioData()
                if (samples != null) {
                    stream?.acceptWaveform(samples, SherpaOnnxASR.SAMPLE_RATE)

                    while (recognizer?.isReady(stream) == true) {
                        recognizer?.decode(stream)
                    }

                    val result = recognizer?.getResult(stream)
                    withContext(Dispatchers.Main) {
                        tvResult.text = result?.text
                    }
                }
            }
        }
    }

    private fun showModelInstructions() {
        val instructions = asr.getModelDownloadInstructions()
        AlertDialog.Builder(this)
            .setTitle("需要模型文件")
            .setMessage(instructions)
            .setPositiveButton("确定", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 清理资源
        recorder?.stopRecording()
        recognizer?.release()

        // 可选：清理缓存
        asr.cleanAudioCache(
            maxCacheSizeBytes = 500 * 1024 * 1024,
            keepRecentCount = 1
        )
    }
}
```

**完整示例（包含 KWS + VAD）：**

```kotlin
class AdvancedActivity : AppCompatActivity() {
    private lateinit var asr: SherpaOnnxASR
    private var kws: KeywordSpotter? = null
    private var vad: Vad? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        asr = SherpaOnnxASR(this)

        // 创建 KWS 唤醒词识别器
        kws = asr.createKeywordSpotter(
            keywordsFile = "keywords.txt",
            threshold = 0.5f
        )

        // 创建 VAD 语音活动检测
        vad = asr.createVad(
            threshold = 0.5f,
            minSilenceDuration = 0.3f
        )

        // KWS 待机模式（不占用内存）
        val recorder = asr.createAudioRecorder()
        recorder.startRecording(savePcm = false, cacheInMemory = false)

        // 检查音频缓存大小
        val cacheSizeMB = asr.getAudioCacheSizeMB()
        Log.i(TAG, "音频缓存: ${cacheSizeMB} MB")
    }
}
```

#### 6. Sync 并运行

点击 Android Studio 的 **Sync Project with Gradle Files**，然后就可以运行了！

## ✅ AAR 包含内容

打包后的 AAR 包含：

### ✅ 源代码（编译后的 .class 文件）

- **`SherpaOnnxASR`** - 🎯 公共 API 入口类
  - 模型管理（检查、加载、创建识别器）
  - 音频录制器创建
  - 缓存管理（获取大小、清理、清除）
  - VAD/KWS 创建
  - 热词支持
- **`ModelManager`** - 内部模型管理类（从子目录加载模型）
- **`AudioRecorder`** - 音频录制类（支持内存/磁盘缓存）
- **`com.k2fsa.sherpa.onnx.*`** - sherpa-onnx 封装类

### ✅ Native 库文件

- `libsherpa-onnx-jni.so` - sherpa-onnx JNI 接口
- `libonnxruntime.so` - ONNX Runtime

### ✅ 配置文件

- `AndroidManifest.xml` - Library manifest

### ❌ 不包含

- **模型文件** - 需要运行时从 `/data/data/包名/files/models/` 加载
- **UI 界面** - 无 Activity，仅提供 API

---

## 📚 使用者需要做什么

### ✅ 必需步骤：

1. 将模型文件部署到子目录：
   ```
   /data/data/包名/files/models/
   ├── asr/      - ASR 模型（必需）
   ├── kws/      - KWS 模型（可选）
   └── vad/      - VAD 模型（可选）
   ```

2. 使用 `SherpaOnnxASR` 类作为入口：
   ```kotlin
   val asr = SherpaOnnxASR(context)
   val recognizer = asr.createRecognizer()
   val recorder = asr.createAudioRecorder()
   ```

### ❌ 不需要做：

- ❌ 配置 .so 文件路径（已打包在 AAR）
- ❌ 手动创建 OnlineRecognizer 配置（`SherpaOnnxASR` 自动处理）
- ❌ 手动管理音频缓存（自动清理，默认保留 500MB）

## 🎉 优点

1. **AAR 体积小**：不包含模型，只有 10-50 MB
2. **灵活部署**：模型可以通过网络下载、adb 推送、或从 assets 复制
3. **模型可更新**：无需重新打包 AAR 即可更换模型
4. **自动化加载**：`ModelManager` 自动检测和加载模型
5. **支持多种模型**：Transducer、Paraformer、CTC 等
6. **保持原项目完整**：原 app 目录未被修改，可以继续开发和测试

## 📋 SherpaOnnxASR API 文档

`SherpaOnnxASR` 是 library 的公共 API 入口，提供完整的语音识别功能。

### 1. 模型管理

```kotlin
val asr = SherpaOnnxASR(context)

// 检查模型是否存在
asr.hasAsrModel()  // 检查 ASR 模型
asr.hasKwsModel()  // 检查 KWS 模型
asr.hasVadModel()  // 检查 VAD 模型
asr.hasModels()    // 检查是否有任何模型

// 获取模型目录
val modelDir = asr.getModelDir()
// 返回: /data/data/包名/files/models/

// 列出模型文件
val files = asr.listModelFiles()

// 获取下载说明
val instructions = asr.getModelDownloadInstructions()
```

### 2. 创建识别器

```kotlin
// 方式一：默认配置（推荐）
val recognizer = asr.createRecognizer()

// 方式二：带热词支持
val recognizer = asr.createRecognizer(
    hotwordsFile = "hotwords.txt",  // 热词文件名（在 asr/ 目录）
    hotwordsScore = 1.5f             // 热词权重（1.0-3.0）
)

// 方式三：自动检测模型类型
val recognizer = asr.createRecognizerAuto()

// 方式四：自定义模型文件
val modelFiles = ModelFiles(
    encoder = "my-encoder.onnx",
    decoder = "my-decoder.onnx",
    joiner = "my-joiner.onnx",
    tokens = "my-tokens.txt"
)
val recognizer = asr.createRecognizerCustom(modelFiles)
```

支持的模型类型：
- **ZIPFORMER_TRANSDUCER** - 流式 Transducer 模型（推荐）
- **PARAFORMER** - Paraformer 模型
- **ZIPFORMER_CTC** - CTC 模型

### 3. 创建 VAD（语音活动检测）

```kotlin
val vad = asr.createVad(
    threshold = 0.5f,           // 语音检测阈值 (0-1)
    minSilenceDuration = 0.3f,  // 最短静音时长（秒）
    minSpeechDuration = 0.25f,  // 最短语音时长（秒）
    maxSpeechDuration = 10.0f   // 最大语音时长（秒）
)
```

### 4. 创建 KWS（唤醒词识别器）

```kotlin
val kws = asr.createKeywordSpotter(
    keywordsFile = "keywords.txt",  // 关键词文件（在 kws/ 目录）
    threshold = 0.5f,               // 唤醒阈值
    score = 1.0f,                   // 关键词分数
    maxActivePaths = 4,             // 最大激活路径数
    numThreads = 1                  // 线程数
)
```

### 5. 音频录制

```kotlin
// 创建录制器
val recorder = asr.createAudioRecorder(
    sampleRate = 16000,
    cacheDir = File(context.filesDir, "audio_cache")
)

// 开始录制
recorder.startRecording(
    savePcm = true,         // 保存到磁盘
    cacheInMemory = true    // 缓存到内存
)

// 读取音频数据（suspend 函数）
lifecycleScope.launch(Dispatchers.IO) {
    val samples = recorder.readAudioData()
}

// 停止录制
recorder.stopRecording()

// 获取当前 PCM 文件
val pcmFile = recorder.getCurrentPcmFile()
```

### 6. 缓存管理（暴露的接口）

```kotlin
// 获取缓存大小
val cacheSizeBytes = asr.getAudioCacheSize()  // 字节
val cacheSizeMB = asr.getAudioCacheSizeMB()    // MB

// 清理旧缓存（保留最新文件）
asr.cleanAudioCache(
    maxCacheSizeBytes = 500 * 1024 * 1024,  // 最大 500MB
    keepRecentCount = 1                      // 保留最新 1 个文件
)

// 清除所有磁盘缓存
asr.clearAudioCache()

// 清除内存缓存
asr.clearMemoryCache()
```

### 7. 版本信息

```kotlin
val versionInfo = asr.getVersionInfo()
// 返回库版本、功能列表、模型状态、缓存大小等信息
Log.i(TAG, versionInfo)
```

输出示例：
```
Sherpa-ONNX ASR Library
Version: 1.0.0

Features:
- Real-time Streaming ASR
- Keyword Spotting (KWS)
- Voice Activity Detection (VAD)
- Automatic Cache Management
- Audio Recording & Export

Model Directory: /data/data/.../files/models
ASR Model: ✓
KWS Model: ✓
VAD Model: ✓
Audio Cache: 125 MB
```

## ⚠️ 注意事项

### 1. 模型文件路径（子目录结构）

模型文件**必须**按类型组织在子目录中：
```
/data/data/<你的应用包名>/files/models/
├── asr/        - ASR 语音识别模型
├── kws/        - KWS 唤醒词检测模型（可选）
└── vad/        - VAD 语音活动检测模型（可选）
```

不能放在其他位置（如 SD 卡、外部存储等），因为使用 `context.filesDir`。

### 2. 文件名要求

#### ASR 模型（asr/ 目录）

**Transducer 模型**需要：
- encoder-epoch-99-avg-1.onnx（或包含 "encoder" 的 .onnx 文件）
- decoder-epoch-99-avg-1.onnx（或包含 "decoder" 的 .onnx 文件）
- joiner-epoch-99-avg-1.onnx（或包含 "joiner" 的 .onnx 文件）
- tokens.txt
- hotwords.txt（可选）

**Paraformer 模型**需要：
- encoder.int8.onnx
- decoder.int8.onnx
- tokens.txt

**CTC 模型**需要：
- model.int8.onnx（或其他单个 .onnx 文件）
- tokens.txt

#### KWS 模型（kws/ 目录，可选）

- encoder-epoch-12-avg-2-chunk-16-left-64.onnx
- decoder-epoch-12-avg-2-chunk-16-left-64.onnx
- joiner-epoch-12-avg-2-chunk-16-left-64.onnx
- tokens.txt
- keywords.txt（唤醒词列表，每行一个，UTF-8 无 BOM）

#### VAD 模型（vad/ 目录，可选）

- silero_vad.onnx

**注意：** 使用自动检测模式时，会根据文件名模式识别模型类型。

### 3. 权限问题

应用需要有读写 `/data/data/包名/files/` 的权限，这是默认拥有的（应用内部存储）。

如果从外部存储复制模型，需要申请存储权限。

### 4. 首次运行

建议在应用首次运行时检查模型是否存在，如果不存在则提示用户下载或从 assets 复制。

```kotlin
val asr = SherpaOnnxASR(context)

if (!asr.hasModels()) {
    // 显示提示或自动下载
    Log.i(TAG, asr.getModelDownloadInstructions())
    showModelMissingDialog()
}
```

### 5. 缓存管理

音频缓存会自动管理：
- KWS 待机模式：`startRecording(savePcm=false, cacheInMemory=false)` - 不占用内存
- ASR 识别模式：`startRecording(savePcm=true, cacheInMemory=true)` - 自动清理旧缓存
- 默认保留最新 500MB，可通过 `cleanAudioCache()` 自定义

### 6. 热词功能

热词可以提高特定词识别准确度：
- 全局热词文件：放在 `models/asr/hotwords.txt`
- 使用方法：`createRecognizer(hotwordsFile="hotwords.txt", hotwordsScore=1.5f)`
- 详细说明：参见 [HOTWORDS_USAGE.md](../HOTWORDS_USAGE.md)

## 🔧 自定义配置

如需修改 AAR 配置，编辑 `library/build.gradle.kts`：

```kotlin
android {
    namespace = "com.example.streamingasr"
    compileSdk = 34

    defaultConfig {
        minSdk = 24  // 修改最低支持的 Android 版本

        ndk {
            // 修改支持的架构
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
        }
    }
}
```

## 🆘 常见问题

**Q: 为什么不打包模型到 AAR？**
A: 模型文件通常很大（几百 MB），打包到 AAR 会导致：
   - AAR 文件太大，不便传输
   - 模型更新需要重新打包 AAR
   - 无法灵活选择不同的模型

**Q: 如何让用户下载模型？**
A: 可以在应用中实现模型下载功能：
   ```kotlin
   suspend fun downloadModel() {
       val modelDir = modelManager.getModelDir()
       // 从你的服务器下载到 modelDir
   }
   ```

**Q: 能否支持从 SD 卡加载模型？**
A: 当前固定使用 `context.filesDir`。如需支持外部存储，需要修改源码。

**Q: AAR 大小大概多少？**
A: 只包含 .so 文件的 AAR 约 10-50 MB（取决于架构数量）。

**Q: 如何验证模型已正确加载？**
A: 使用 `createRecognizer()` 返回值检查：
   ```kotlin
   val asr = SherpaOnnxASR(context)
   val recognizer = asr.createRecognizer()
   if (recognizer != null) {
       Log.i(TAG, "模型加载成功")
   } else {
       Log.e(TAG, "模型加载失败")
       Log.i(TAG, asr.getModelDownloadInstructions())
   }
   ```

**Q: 能否同时维护 App 和 Library？**
A: 可以！`app/` 目录保持不变，继续开发；需要更新 AAR 时，将改动同步到 `library/` 并重新构建。

**Q: 如何在有网络限制的环境中构建 AAR？**
A:
1. 在能访问网络的机器上运行 `bash download-libs.sh` 下载 native 库
2. 将 `app/src/main/jniLibs/` 复制到 `library/src/main/jniLibs/`
3. 在 Android Studio 中打开项目
4. 选择 Build → Make Module 'StreamingASRApp.library'
5. AAR 文件在 `library/build/outputs/aar/library-release.aar`

**Q: 如何使用热词功能？**
A: 参见 [HOTWORDS_USAGE.md](HOTWORDS_USAGE.md) 完整文档，简单使用：
   ```kotlin
   val recognizer = asr.createRecognizer(
       hotwordsFile = "hotwords.txt",
       hotwordsScore = 1.5f
   )
   ```

---

**祝你打包顺利！** 🎉

## 📚 相关文档

- [library/README.md](library/README.md) - Library 完整 API 文档
- [HOTWORDS_USAGE.md](HOTWORDS_USAGE.md) - 热词使用指南
- [KWS_USAGE.md](KWS_USAGE.md) - 唤醒词使用指南
- [VAD_USAGE.md](VAD_USAGE.md) - VAD 使用指南
- [快速开始.md](快速开始.md) - 快速入门指南
