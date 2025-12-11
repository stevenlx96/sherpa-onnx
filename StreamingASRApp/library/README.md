# Sherpa-ONNX ASR Library

Android 语音识别库，基于 sherpa-onnx，支持实时流式识别、唤醒词检测和语音活动检测。

## 特性

✨ **核心功能**
- 🎤 实时流式语音识别 (ASR)
- 🔔 唤醒词检测 (KWS)
- 🎯 语音活动检测 (VAD)
- 🔥 热词支持（提高特定词识别准确度）
- 🧹 自动缓存管理（防止存储空间耗尽）
- 📦 音频录制和导出

✅ **开箱即用**
- 所有代码和 native 库打包在 AAR 中
- 模型从 `context.filesDir/models/` 加载
- 支持子目录结构组织模型（asr/kws/vad）

## 快速开始

### 1. 添加 AAR 依赖

将 `library-release.aar` 放到项目的 `libs/` 目录，然后在 `build.gradle.kts` 中添加：

```kotlin
dependencies {
    implementation(files("libs/library-release.aar"))

    // 必需的外部依赖
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
}
```

### 2. 部署模型文件

模型文件需要部署到：
```
/data/data/<你的包名>/files/models/
├── asr/        - ASR 语音识别模型
├── kws/        - KWS 唤醒词检测模型（可选）
└── vad/        - VAD 语音活动检测模型（可选）
```

使用 adb 推送示例：
```bash
# 推送 ASR 模型
adb push encoder-epoch-99-avg-1.onnx /data/local/tmp/
adb push decoder-epoch-99-avg-1.onnx /data/local/tmp/
adb push joiner-epoch-99-avg-1.onnx /data/local/tmp/
adb push tokens.txt /data/local/tmp/

# 移动到应用目录
adb shell
run-as <你的包名>
mkdir -p files/models/asr
cp /data/local/tmp/*.onnx files/models/asr/
cp /data/local/tmp/tokens.txt files/models/asr/
exit
```

### 3. 使用示例

```kotlin
import com.example.streamingasr.SherpaOnnxASR

class MainActivity : AppCompatActivity() {
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
            return
        }

        // 创建识别器
        recognizer = asr.createRecognizer()

        // 创建音频录制器
        recorder = asr.createAudioRecorder()
        recorder?.startRecording(savePcm = true, cacheInMemory = true)

        // 创建流并开始识别
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

    override fun onDestroy() {
        super.onDestroy()
        // 清理资源
        recorder?.stopRecording()
        recognizer?.release()
    }
}
```

## API 文档

### SherpaOnnxASR - 主要入口

```kotlin
class SherpaOnnxASR(context: Context)
```

#### 模型管理

```kotlin
// 检查模型是否存在
fun hasAsrModel(): Boolean
fun hasKwsModel(): Boolean
fun hasVadModel(): Boolean
fun hasModels(): Boolean  // 检查是否有任何模型

// 获取模型目录
fun getModelDir(): File

// 获取模型下载说明
fun getModelDownloadInstructions(): String
```

#### 创建识别器

```kotlin
// 创建 ASR 识别器
fun createRecognizer(
    modelType: ModelManager.ModelType = ModelManager.ModelType.ZIPFORMER_TRANSDUCER,
    numThreads: Int = Runtime.getRuntime().availableProcessors(),
    hotwordsFile: String = "",      // 热词文件（可选）
    hotwordsScore: Float = 1.5f      // 热词权重
): OnlineRecognizer?

// 自动检测并创建识别器
fun createRecognizerAuto(): OnlineRecognizer?

// 创建 VAD
fun createVad(
    threshold: Float = 0.5F,
    minSilenceDuration: Float = 0.3F,
    minSpeechDuration: Float = 0.25F,
    maxSpeechDuration: Float = 10.0F
): Vad?

// 创建唤醒词识别器
fun createKeywordSpotter(
    keywordsFile: String = "keywords.txt",
    threshold: Float = 0.5F,
    score: Float = 1.0F
): KeywordSpotter?
```

#### 音频录制

```kotlin
// 创建音频录制器
fun createAudioRecorder(
    sampleRate: Int = 16000,
    cacheDir: File = File(context.filesDir, "audio_cache")
): AudioRecorder

// 获取当前录制器
fun getAudioRecorder(): AudioRecorder?
```

#### 缓存管理

```kotlin
// 获取缓存大小
fun getAudioCacheSize(): Long       // 字节
fun getAudioCacheSizeMB(): Long     // MB

// 清理缓存
fun cleanAudioCache(
    maxCacheSizeBytes: Long = 500 * 1024 * 1024,  // 默认 500MB
    keepRecentCount: Int = 1                       // 保留最新 N 个
)

// 清除所有缓存
fun clearAudioCache()

// 清除内存缓存
fun clearMemoryCache()
```

### AudioRecorder - 音频录制器

```kotlin
// 开始录制
fun startRecording(
    savePcm: Boolean = true,        // 是否保存到磁盘
    cacheInMemory: Boolean = true   // 是否缓存到内存
): Boolean

// 读取音频数据
suspend fun readAudioData(): FloatArray?

// 停止录制
fun stopRecording()

// 获取当前 PCM 文件
fun getCurrentPcmFile(): File?

// 缓存管理
fun getCacheSize(): Long
fun cleanOldCacheFiles(maxCacheSizeBytes: Long, keepRecentCount: Int)
fun deleteAllCacheFiles()
fun clearPcmCache()
```

## 高级功能

### 1. 热词支持

提高特定词语的识别准确度：

```kotlin
// 方式1：全局热词文件
val recognizer = asr.createRecognizer(
    hotwordsFile = "hotwords.txt",  // 放在 models/asr/ 目录
    hotwordsScore = 1.5f
)

// 方式2：动态热词
val hotwords = "小达 小智助手 人工智能"
val stream = recognizer.createStream(hotwords = hotwords)
```

详见 [HOTWORDS_USAGE.md](../HOTWORDS_USAGE.md)

### 2. 唤醒词检测 (KWS)

```kotlin
val kws = asr.createKeywordSpotter(
    keywordsFile = "keywords.txt",
    threshold = 0.5f
)

val kwsStream = kws.createStream()

// 在循环中检测唤醒词
kwsStream.acceptWaveform(samples, 16000)
while (kws.isReady(kwsStream)) {
    kws.decode(kwsStream)
}

val result = kws.getResult(kwsStream)
if (result.keyword.isNotEmpty()) {
    Log.i(TAG, "检测到唤醒词: ${result.keyword}")
}
```

详见 [KWS_USAGE.md](../KWS_USAGE.md)

### 3. VAD 智能断句

```kotlin
val vad = asr.createVad(
    threshold = 0.5f,
    minSilenceDuration = 0.3f  // 0.3秒静音即断句
)

// 检测语音活动
vad.acceptWaveform(samples)
if (vad.isSpeechDetected()) {
    // 有语音
} else {
    // 静音
}
```

### 4. 自动缓存清理

每次开始新录制时，自动清理旧缓存：

```kotlin
// 录制器会自动管理缓存
recorder.startRecording(savePcm = true)

// 或手动清理
asr.cleanAudioCache(
    maxCacheSizeBytes = 500 * 1024 * 1024,  // 500MB
    keepRecentCount = 1                      // 保留最新1个
)
```

## 模型下载

### ASR 模型（必需）

中英文双语模型：
```bash
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2
tar xvf sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2
```

需要的文件：
- encoder-epoch-99-avg-1.onnx
- decoder-epoch-99-avg-1.onnx
- joiner-epoch-99-avg-1.onnx
- tokens.txt

### KWS 模型（可选）

中文唤醒词模型：
```bash
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01.tar.bz2
tar xvf sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01.tar.bz2
```

### VAD 模型（推荐）

```bash
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx
```

## 构建 AAR

### 方法1：使用 Gradle

```bash
cd StreamingASRApp
./gradlew :library:assembleRelease
```

生成的 AAR 文件：
```
library/build/outputs/aar/library-release.aar
```

### 方法2：使用 Android Studio

1. 打开项目
2. Build → Make Module 'StreamingASRApp.library'
3. 在 `library/build/outputs/aar/` 找到生成的 AAR

## 目录结构

```
library/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       ├── example/streamingasr/
│       │       │   ├── SherpaOnnxASR.kt      # 公共 API 入口
│       │       │   ├── ModelManager.kt       # 模型管理
│       │       │   └── AudioRecorder.kt      # 音频录制
│       │       └── k2fsa/sherpa/onnx/
│       │           ├── OnlineRecognizer.kt   # sherpa-onnx 封装
│       │           ├── Vad.kt
│       │           ├── KeywordSpotter.kt
│       │           └── ...
│       ├── jniLibs/
│       │   ├── arm64-v8a/
│       │   │   ├── libsherpa-onnx-jni.so
│       │   │   └── libonnxruntime.so
│       │   └── armeabi-v7a/
│       │       └── ...
│       └── AndroidManifest.xml
├── build.gradle.kts
└── README.md
```

## 注意事项

### 1. 模型文件大小

AAR 本身不包含模型文件，模型需要单独部署到：
```
/data/data/<包名>/files/models/
```

这样设计的原因：
- AAR 体积更小
- 模型可以按需下载和更新
- 支持不同应用使用不同模型

### 2. 权限

需要在应用的 `AndroidManifest.xml` 中添加：

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

并在运行时请求麦克风权限。

### 3. 架构支持

当前支持：
- arm64-v8a
- armeabi-v7a

如需其他架构，请修改 `build.gradle.kts` 中的 `ndk.abiFilters`。

### 4. 缓存管理

- KWS 待机模式：不占用内存缓存
- ASR 识别模式：自动清理旧缓存（默认保留最新500MB）
- 手动清理：调用 `asr.clearAudioCache()`

## 示例项目

完整示例参考 `app` module 中的 `MainActivity.kt`。

## 文档

- [热词使用指南](../HOTWORDS_USAGE.md)
- [唤醒词使用指南](../KWS_USAGE.md)
- [VAD 使用指南](../VAD_USAGE.md)

## 版本信息

```kotlin
Log.i(TAG, asr.getVersionInfo())
```

输出：
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

## 许可证

基于 sherpa-onnx 开源项目。

## 联系方式

- GitHub: [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)
- 文档: [sherpa-onnx 文档](https://k2-fsa.github.io/sherpa/onnx/)
