# Sherpa-ONNX 实时语音识别 AAR 使用说明

---

## AAR包含的所有类

| 类名 | 文件 | 说明 |
|------|------|------|
| `ModelManager` | ModelManager.kt | 模型管理器（加载ASR/KWS/VAD模型） |
| `AudioRecorder` | AudioRecorder.kt | 音频录制器（PCM录制和缓存） |
| `OnlineRecognizer` | OnlineRecognizer.kt | 在线识别器（sherpa-onnx核心） |
| `OnlineStream` | OnlineStream.kt | 识别流（处理音频流） |
| `Vad` | Vad.kt | VAD模型（智能断句） |
| `KeywordSpotter` | KeywordSpotter.kt | 唤醒词识别器（低功耗待机） |
| `ModelFiles` | ModelManager.kt | 自定义模型文件配置（数据类） |
| `ModelType` | ModelManager.kt | 模型类型枚举 |
| Native库 | libsherpa-onnx-jni.so | JNI封装的sherpa-onnx |

---

## 1️. ModelManager - 模型管理器

### 构造函数
```kotlin
val modelManager = ModelManager(context: Context)
```

### 模型目录结构

模型文件存储在应用内部存储：`/data/data/你的包名/files/models/`

```
models/
├── asr/        # ASR 语音识别模型
├── kws/        # KWS 唤醒词检测模型
└── vad/        # VAD 语音活动检测模型
```

### 主要方法

#### 模型目录
```kotlin
fun getModelDir(): File
```
- **功能**: 获取模型目录路径
- **返回**: `File` - 模型目录
- **示例**:
```kotlin
val modelDir = modelManager.getModelDir()
Log.i(TAG, "模型路径: ${modelDir.absolutePath}")
```

#### 检查模型
```kotlin
fun checkModelExists(modelType: ModelType): Boolean
fun checkKwsExists(keywordsFile: String = "keywords.txt"): Boolean
fun checkVadExists(): Boolean
```
- **功能**: 检查指定模型是否存在
- **参数**:
  - `modelType`: 模型类型（ZIPFORMER_TRANSDUCER, PARAFORMER, ZIPFORMER_CTC）
  - `keywordsFile`: 关键词文件名（默认 "keywords.txt"）
- **返回**: `Boolean` - 模型是否存在
- **示例**:
```kotlin
if (modelManager.checkModelExists(ModelManager.ModelType.ZIPFORMER_TRANSDUCER)) {
    Log.i(TAG, "ASR 模型存在")
}

if (modelManager.checkKwsExists()) {
    Log.i(TAG, "KWS 模型和 keywords.txt 存在")
}

if (modelManager.checkVadExists()) {
    Log.i(TAG, "VAD 模型存在")
}
```

#### 创建识别器（预定义模型类型）
```kotlin
fun createOnlineRecognizer(
    modelType: ModelType = ModelType.ZIPFORMER_TRANSDUCER,
    numThreads: Int = Runtime.getRuntime().availableProcessors()
): OnlineRecognizer?
```
- **功能**: 使用预定义的模型类型创建识别器
- **参数**:
  - `modelType`: 模型类型
  - `numThreads`: 线程数（默认为CPU核心数）
- **返回**: `OnlineRecognizer?` - 成功返回识别器，失败返回null
- **示例**:
```kotlin
val recognizer = modelManager.createOnlineRecognizer(
    modelType = ModelManager.ModelType.ZIPFORMER_TRANSDUCER,
    numThreads = 4
)
```

#### 创建识别器（自定义模型文件）
```kotlin
fun createOnlineRecognizer(
    modelFiles: ModelFiles,
    numThreads: Int = Runtime.getRuntime().availableProcessors()
): OnlineRecognizer?
```
- **功能**: 使用自定义的模型文件名创建识别器
- **参数**:
  - `modelFiles`: 自定义模型文件配置
  - `numThreads`: 线程数
- **返回**: `OnlineRecognizer?`
- **示例**:
```kotlin
val modelFiles = ModelFiles(
    encoder = "my-encoder.onnx",
    decoder = "my-decoder.onnx",
    joiner = "my-joiner.onnx",
    tokens = "tokens.txt"
)
val recognizer = modelManager.createOnlineRecognizer(modelFiles)
```

#### 自动创建识别器
```kotlin
fun createOnlineRecognizerAuto(
    numThreads: Int = Runtime.getRuntime().availableProcessors()
): OnlineRecognizer?
```
- **功能**: 自动扫描模型目录并匹配文件名模式
- **返回**: `OnlineRecognizer?`
- **示例**:
```kotlin
// 自动检测并加载模型
val recognizer = modelManager.createOnlineRecognizerAuto()
if (recognizer != null) {
    Log.i(TAG, "自动加载成功")
}
```

#### 创建 VAD
```kotlin
fun createVad(
    threshold: Float = 0.5F,
    minSilenceDuration: Float = 0.3F,
    minSpeechDuration: Float = 0.25F,
    maxSpeechDuration: Float = 10.0F
): Vad?
```
- **功能**: 创建 Silero VAD 模型
- **参数**:
  - `threshold`: 语音检测阈值（0-1，默认 0.5）
  - `minSilenceDuration`: 最短静音时长，用于断句（秒，默认 0.3）
  - `minSpeechDuration`: 最短语音时长，过滤杂音（秒，默认 0.25）
  - `maxSpeechDuration`: 最大语音时长，强制断句（秒，默认 10.0）
- **返回**: `Vad?` - 成功返回VAD，失败返回null
- **示例**:
```kotlin
val vad = modelManager.createVad(
    threshold = 0.5F,
    minSilenceDuration = 1.0F,  // 静音1秒算句子结束
    minSpeechDuration = 0.25F,
    maxSpeechDuration = 10.0F
)
```

#### 创建唤醒词识别器
```kotlin
fun createKeywordSpotter(
    keywordsFile: String = "keywords.txt",
    threshold: Float = 0.5F,
    score: Float = 1.0F,
    maxActivePaths: Int = 4,
    numThreads: Int = 1
): KeywordSpotter?
```
- **功能**: 创建 KWS 唤醒词识别器
- **参数**:
  - `keywordsFile`: 关键词文件名（默认 "keywords.txt"）
  - `threshold`: 唤醒阈值（默认 0.5）
  - `score`: 关键词分数（默认 1.0）
  - `maxActivePaths`: 最大激活路径数（默认 4）
  - `numThreads`: 线程数（默认 1）
- **返回**: `KeywordSpotter?`
- **示例**:
```kotlin
val keywordSpotter = modelManager.createKeywordSpotter(
    keywordsFile = "keywords.txt",
    threshold = 0.5F,
    score = 1.0F
)
```

#### 工具方法
```kotlin
fun getModelDownloadInstructions(): String  // 获取模型下载说明
fun listModelFiles(): List<String>          // 列出模型目录中的所有文件
```

### ModelFiles 数据类
```kotlin
data class ModelFiles(
    // Transducer 模型文件
    val encoder: String? = null,
    val decoder: String? = null,
    val joiner: String? = null,

    // Paraformer/其他模型文件
    val model: String? = null,

    // 通用文件
    val tokens: String = "tokens.txt",

    // HomophoneReplacer 文件（可选）
    val lexicon: String? = null,
    val replaceFst: String? = null
)
```

### ModelType 枚举
```kotlin
enum class ModelType {
    ZIPFORMER_TRANSDUCER,  // Transducer模型 (推荐)
    PARAFORMER,             // Paraformer模型
    ZIPFORMER_CTC          // CTC模型
}
```

### 使用示例
```kotlin
val modelManager = ModelManager(context)

// 方式1: 使用预定义模型类型
val recognizer1 = modelManager.createOnlineRecognizer(
    modelType = ModelManager.ModelType.ZIPFORMER_TRANSDUCER
)

// 方式2: 使用自定义模型文件
val modelFiles = ModelFiles(
    encoder = "encoder-epoch-99-avg-1.onnx",
    decoder = "decoder-epoch-99-avg-1.onnx",
    joiner = "joiner-epoch-99-avg-1.onnx",
    tokens = "tokens.txt"
)
val recognizer2 = modelManager.createOnlineRecognizer(modelFiles)

// 方式3: 自动检测
val recognizer3 = modelManager.createOnlineRecognizerAuto()
```

---

## 2️. AudioRecorder - 音频录制器

### 构造函数
```kotlin
val audioRecorder = AudioRecorder(
    sampleRate: Int = 16000,
    cacheDir: File
)
```

### 参数说明

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `sampleRate` | Int | 16000 | 采样率（Hz） |
| `cacheDir` | File | - | 缓存目录（用于保存PCM文件） |

### 主要方法

#### 开始录制
```kotlin
fun startRecording(
    savePcm: Boolean = true,
    cacheInMemory: Boolean = true
): Boolean
```
- **功能**: 开始录制音频
- **参数**:
  - `savePcm`: 是否保存PCM文件到缓存目录
  - `cacheInMemory`: 是否在内存中缓存音频数据
- **返回**: `Boolean` - 成功返回true，失败返回false
- **示例**:
```kotlin
// 录制并保存PCM文件
audioRecorder.startRecording(savePcm = true, cacheInMemory = true)

// 仅录制，不保存文件（KWS 待机模式）
audioRecorder.startRecording(savePcm = false, cacheInMemory = false)
```

#### 读取音频数据
```kotlin
suspend fun readAudioData(): FloatArray?
```
- **功能**: 读取音频数据（阻塞调用，使用协程）
- **返回**: `FloatArray?` - 音频样本数据（范围: -1.0 到 1.0），如果无数据返回null
- **示例**:
```kotlin
lifecycleScope.launch(Dispatchers.IO) {
    while (isRecording) {
        val samples = audioRecorder.readAudioData()
        if (samples != null) {
            // 送入识别器
            stream?.acceptWaveform(samples, 16000)
        }
    }
}
```

#### 停止录制
```kotlin
fun stopRecording()
```
- **功能**: 停止录制
- **示例**:
```kotlin
audioRecorder.stopRecording()
```

#### 获取PCM文件
```kotlin
fun getCurrentPcmFile(): File?
```
- **功能**: 获取当前录制的PCM文件
- **返回**: `File?` - PCM文件，如果未保存返回null
- **示例**:
```kotlin
val pcmFile = audioRecorder.getCurrentPcmFile()
Log.i(TAG, "PCM文件: ${pcmFile?.absolutePath}")
```

#### 缓存管理
```kotlin
fun getAllPcmData(): List<ShortArray>         // 获取所有缓存的PCM数据
fun clearPcmCache()                           // 清除PCM缓存
fun getCacheSize(): Long                      // 获取缓存目录的总大小（字节）
fun deleteAllCacheFiles()                     // 删除所有缓存文件
fun cleanOldCacheFiles(                       // 清理旧的缓存文件
    maxCacheSizeBytes: Long = 500 * 1024 * 1024,
    keepRecentCount: Int = 1
)
```

### 使用示例
```kotlin
val cacheDir = File(context.filesDir, "audio_cache")
val audioRecorder = AudioRecorder(16000, cacheDir)

// 开始录制
audioRecorder.startRecording(savePcm = true)

// 读取音频数据
lifecycleScope.launch(Dispatchers.IO) {
    while (isRecording) {
        val samples = audioRecorder.readAudioData()
        if (samples != null) {
            // 处理音频
            vad?.acceptWaveform(samples)
            stream?.acceptWaveform(samples, 16000)
        }
    }
}

// 停止录制
audioRecorder.stopRecording()

// 获取录制的文件
val pcmFile = audioRecorder.getCurrentPcmFile()
Log.i(TAG, "已保存: ${pcmFile?.absolutePath}")
```

### 常量
- `SAMPLE_RATE`: 16000 (默认采样率)
- `CHANNEL_CONFIG`: CHANNEL_IN_MONO (单声道)
- `AUDIO_FORMAT`: ENCODING_PCM_16BIT (16位PCM)

---

## 3️. OnlineRecognizer - 在线识别器

**来自**: sherpa-onnx 库（com.k2fsa.sherpa.onnx）

### 构造函数
```kotlin
// 由 ModelManager 创建，不直接构造
val recognizer = modelManager.createOnlineRecognizer(...)
```

### 主要方法

#### 创建识别流
```kotlin
fun createStream(): OnlineStream
```
- **功能**: 创建一个识别流
- **返回**: `OnlineStream` - 识别流对象
- **示例**:
```kotlin
val stream = recognizer?.createStream()
```

#### 检查是否准备好解码
```kotlin
fun isReady(stream: OnlineStream): Boolean
```
- **功能**: 检查识别器是否准备好解码下一帧
- **返回**: `Boolean`
- **示例**:
```kotlin
while (recognizer?.isReady(stream) == true) {
    recognizer?.decode(stream)
}
```

#### 解码
```kotlin
fun decode(stream: OnlineStream)
```
- **功能**: 解码一帧音频
- **示例**:
```kotlin
while (recognizer?.isReady(stream) == true) {
    recognizer?.decode(stream)
}
```

#### 获取识别结果
```kotlin
fun getResult(stream: OnlineStream): OnlineRecognizerResult
```
- **功能**: 获取当前识别结果
- **返回**: `OnlineRecognizerResult` - 包含 `text` 字段
- **示例**:
```kotlin
val result = recognizer?.getResult(stream)
val currentText = result?.text ?: ""
Log.i(TAG, "识别结果: $currentText")
```

#### 检查端点（断句）
```kotlin
fun isEndpoint(stream: OnlineStream): Boolean
```
- **功能**: 检查是否到达端点（句子结束）
- **返回**: `Boolean`
- **示例**:
```kotlin
val isEndpoint = recognizer?.isEndpoint(stream) == true
if (isEndpoint && currentText.isNotEmpty()) {
    Log.i(TAG, "句子完成: $currentText")
    recognizer?.reset(stream)
}
```

#### 重置流
```kotlin
fun reset(stream: OnlineStream)
```
- **功能**: 重置流，用于识别下一句
- **示例**:
```kotlin
recognizer?.reset(stream)
```

#### 释放资源
```kotlin
fun release()
```
- **功能**: 释放识别器资源
- **示例**:
```kotlin
override fun onDestroy() {
    super.onDestroy()
    recognizer?.release()
}
```

### 使用示例
```kotlin
val recognizer = modelManager.createOnlineRecognizer()
val stream = recognizer?.createStream()

// 识别循环
lifecycleScope.launch(Dispatchers.IO) {
    while (isRecording) {
        val samples = audioRecorder?.readAudioData()

        if (samples != null) {
            // 送入音频流
            stream?.acceptWaveform(samples, 16000)

            // 解码
            while (recognizer?.isReady(stream!!) == true) {
                recognizer?.decode(stream!!)
            }

            // 获取结果
            val result = recognizer?.getResult(stream!!)
            val currentText = result?.text ?: ""

            // 检查是否断句
            if (recognizer?.isEndpoint(stream!!) == true && currentText.isNotEmpty()) {
                Log.i(TAG, "句子完成: $currentText")
                recognizer?.reset(stream!!)
            }
        }
    }
}
```

---

## 4️. OnlineStream - 识别流

**来自**: sherpa-onnx 库（com.k2fsa.sherpa.onnx）

### 创建
```kotlin
// 由 OnlineRecognizer 创建
val stream = recognizer.createStream()
```

### 主要方法

#### 接受音频波形
```kotlin
fun acceptWaveform(samples: FloatArray, sampleRate: Int)
```
- **功能**: 送入音频数据
- **参数**:
  - `samples`: 音频样本（范围: -1.0 到 1.0）
  - `sampleRate`: 采样率（通常为 16000）
- **示例**:
```kotlin
stream?.acceptWaveform(samples, 16000)
```

#### 释放资源
```kotlin
fun release()
```
- **功能**: 释放流资源
- **示例**:
```kotlin
stream?.release()
stream = null
```

---

## 5️. Vad - VAD 语音活动检测

**来自**: sherpa-onnx 库（com.k2fsa.sherpa.onnx）

### 构造函数
```kotlin
// 由 ModelManager 创建
val vad = modelManager.createVad(
    threshold = 0.5F,
    minSilenceDuration = 1.0F,
    minSpeechDuration = 0.25F,
    maxSpeechDuration = 10.0F
)
```

### 主要方法

#### 接受音频波形
```kotlin
fun acceptWaveform(samples: FloatArray)
```
- **功能**: 送入音频数据进行VAD检测
- **参数**: `samples` - 音频样本
- **示例**:
```kotlin
vad?.acceptWaveform(samples)
```

#### 检查队列是否为空
```kotlin
fun empty(): Boolean
```
- **功能**: 检查语音段队列是否为空
- **返回**: `Boolean` - true表示队列为空
- **示例**:
```kotlin
if (vad?.empty() == false) {
    // 有完整的语音段
}
```

#### 获取语音段
```kotlin
fun front(): SpeechSegment?
```
- **功能**: 获取队列头部的语音段（不移除）
- **返回**: `SpeechSegment?` - 包含 start 和 samples 字段
- **示例**:
```kotlin
val segment = vad?.front()
Log.d(TAG, "语音段开始时间: ${segment?.start}, 样本数: ${segment?.samples?.size}")
```

#### 移除语音段
```kotlin
fun pop()
```
- **功能**: 从队列中移除头部的语音段
- **示例**:
```kotlin
vad?.pop()
```

#### 重置 VAD
```kotlin
fun reset()
```
- **功能**: 重置 VAD 状态
- **示例**:
```kotlin
vad?.reset()
```

#### 释放资源
```kotlin
fun release()
```
- **功能**: 释放 VAD 资源
- **示例**:
```kotlin
vad?.release()
vad = null
```

### VAD 断句机制

VAD 内部维护一个语音段队列。当检测到完整的语音段时（静音时长达到 `minSilenceDuration`），会将语音段放入队列。

```kotlin
// VAD 断句逻辑
vad?.acceptWaveform(samples)

// 检查是否有完整的语音段
if (vad?.empty() == false) {
    val segment = vad?.front()  // 获取语音段
    vad?.pop()  // 从队列移除

    Log.i(TAG, "检测到完整语音段，触发断句")

    // 在这里处理句子完成
    // ...
}
```

### 使用示例
```kotlin
val vad = modelManager.createVad(
    threshold = 0.5F,
    minSilenceDuration = 1.0F,  // 静音1秒算句子结束
    minSpeechDuration = 0.25F,
    maxSpeechDuration = 10.0F
)

lifecycleScope.launch(Dispatchers.IO) {
    while (isRecording) {
        val samples = audioRecorder?.readAudioData()

        if (samples != null) {
            // 送入 VAD
            vad?.acceptWaveform(samples)

            // 检查是否有完整的语音段
            if (vad?.empty() == false) {
                val segment = vad?.front()
                vad?.pop()

                Log.i(TAG, "✓ 句子完成（VAD断句）")

                // 重置识别器，准备下一句
                recognizer?.reset(stream!!)
                vad?.reset()
            }
        }
    }
}
```

---

## 6️. KeywordSpotter - 唤醒词识别器

**来自**: sherpa-onnx 库（com.k2fsa.sherpa.onnx）

### 构造函数
```kotlin
// 由 ModelManager 创建
val keywordSpotter = modelManager.createKeywordSpotter(
    keywordsFile = "keywords.txt",
    threshold = 0.5F,
    score = 1.0F
)
```

### 主要方法

#### 创建 KWS 流
```kotlin
fun createStream(): OnlineStream
```
- **功能**: 创建 KWS 识别流
- **返回**: `OnlineStream`
- **示例**:
```kotlin
val kwsStream = keywordSpotter?.createStream()
```

#### 检查是否准备好解码
```kotlin
fun isReady(stream: OnlineStream): Boolean
```

#### 解码
```kotlin
fun decode(stream: OnlineStream)
```

#### 获取检测结果
```kotlin
fun getResult(stream: OnlineStream): KeywordResult
```
- **功能**: 获取唤醒词检测结果
- **返回**: `KeywordResult` - 包含 `keyword` 字段
- **示例**:
```kotlin
val result = keywordSpotter?.getResult(kwsStream!!)
if (result != null && result.keyword.isNotEmpty()) {
    Log.i(TAG, "检测到唤醒词: ${result.keyword}")
}
```

#### 重置流
```kotlin
fun reset(stream: OnlineStream)
```

#### 释放资源
```kotlin
fun release()
```

### keywords.txt 文件格式

```
你好小智
小爱同学
嗨小度
```

每行一个唤醒词，支持中文。

### 使用示例
```kotlin
val keywordSpotter = modelManager.createKeywordSpotter(
    keywordsFile = "keywords.txt",
    threshold = 0.5F
)

val kwsStream = keywordSpotter?.createStream()

lifecycleScope.launch(Dispatchers.IO) {
    // 开始录音（不保存，不缓存）
    audioRecorder?.startRecording(savePcm = false, cacheInMemory = false)

    while (isInStandbyMode) {
        val samples = audioRecorder?.readAudioData()

        if (samples != null) {
            kwsStream?.acceptWaveform(samples, 16000)

            while (keywordSpotter?.isReady(kwsStream!!) == true) {
                keywordSpotter?.decode(kwsStream!!)
            }

            val result = keywordSpotter?.getResult(kwsStream!!)
            if (result != null && result.keyword.isNotEmpty()) {
                Log.i(TAG, "🔊 检测到唤醒词: ${result.keyword}")

                // 切换到 ASR 识别模式
                switchToAsrMode()

                // 重置 KWS 流
                keywordSpotter?.reset(kwsStream!!)
            }
        }
    }
}
```

---

## 完整使用示例

### 基本流程（直接识别模式）

```kotlin
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ASRDemo"
        private const val SAMPLE_RATE = 16000
    }

    // 核心组件
    private lateinit var modelManager: ModelManager
    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    private var vad: Vad? = null
    private var audioRecorder: AudioRecorder? = null

    // 状态
    private var isRecording = false
    private var recognitionJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化模型管理器
        modelManager = ModelManager(this)

        // 检查并请求权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                100
            )
        } else {
            initializeModels()
        }
    }

    private fun initializeModels() {
        lifecycleScope.launch(Dispatchers.IO) {
            // 加载 ASR 模型
            recognizer = modelManager.createOnlineRecognizer(
                modelType = ModelManager.ModelType.ZIPFORMER_TRANSDUCER
            )

            // 加载 VAD 模型
            vad = modelManager.createVad(
                threshold = 0.5F,
                minSilenceDuration = 1.0F,
                minSpeechDuration = 0.25F,
                maxSpeechDuration = 10.0F
            )

            withContext(Dispatchers.Main) {
                if (recognizer != null) {
                    Log.i(TAG, "✓ 模型加载成功")
                    startRecognition()
                } else {
                    Log.e(TAG, "❌ 模型加载失败")
                    Log.i(TAG, modelManager.getModelDownloadInstructions())
                }
            }
        }
    }

    private fun startRecognition() {
        try {
            // 创建缓存目录
            val cacheDir = File(filesDir, "audio_cache")
            audioRecorder = AudioRecorder(SAMPLE_RATE, cacheDir)

            // 创建识别流
            stream = recognizer?.createStream()

            // 开始录音
            if (audioRecorder?.startRecording(savePcm = true) == true) {
                isRecording = true
                startRecognitionTask()
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动失败", e)
        }
    }

    private fun startRecognitionTask() {
        recognitionJob = lifecycleScope.launch(Dispatchers.IO) {
            var lastText = ""
            val completedSentences = mutableListOf<String>()

            try {
                while (isActive && isRecording) {
                    // 读取音频数据
                    val samples = audioRecorder?.readAudioData()

                    if (samples != null && samples.isNotEmpty()) {
                        // 送入 VAD
                        vad?.acceptWaveform(samples)

                        // 送入识别流
                        stream?.acceptWaveform(samples, SAMPLE_RATE)

                        // 解码
                        while (recognizer?.isReady(stream!!) == true) {
                            recognizer?.decode(stream!!)
                        }

                        // 获取识别结果
                        val result = recognizer?.getResult(stream!!)
                        val currentText = result?.text ?: ""

                        // 检查 VAD 断句
                        var vadSentenceComplete = false
                        if (vad?.empty() == false) {
                            val segment = vad?.front()
                            vad?.pop()

                            Log.d(TAG, "VAD 检测到完整语音段 (start=${segment?.start})")
                            vadSentenceComplete = true
                        }

                        // 备用断句：ASR 内置 endpoint
                        val isEndpoint = recognizer?.isEndpoint(stream!!) == true

                        // 组合断句策略
                        val shouldBreak = when {
                            vadSentenceComplete && currentText.isNotEmpty() -> {
                                Log.d(TAG, "断句触发: VAD 语音段完成")
                                true
                            }
                            isEndpoint && currentText.isNotEmpty() -> {
                                Log.d(TAG, "断句触发: ASR 内置 endpoint")
                                true
                            }
                            else -> false
                        }

                        // 如果检测到句子结束
                        if (shouldBreak && currentText.isNotEmpty()) {
                            // 句子完成
                            completedSentences.add(currentText)
                            Log.i(TAG, "✓ 句子完成: $currentText")

                            withContext(Dispatchers.Main) {
                                // 更新UI
                                updateUI(completedSentences)
                            }

                            // 🎯 这里可以发送给 LLM
                            // sendToLLM(currentText)

                            // 重置流，准备下一句
                            recognizer?.reset(stream!!)
                            vad?.reset()
                            lastText = ""
                        } else if (currentText != lastText && currentText.length >= 3) {
                            // 实时更新部分结果
                            withContext(Dispatchers.Main) {
                                updatePartialResult(completedSentences, currentText)
                            }
                            lastText = currentText
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "识别错误", e)
            }
        }
    }

    private fun stopRecognition() {
        isRecording = false
        recognitionJob?.cancel()
        audioRecorder?.stopRecording()
        stream?.release()
        stream = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecognition()
        recognizer?.release()
        vad?.release()
        audioRecorder = null
    }
}
```

### KWS 唤醒模式示例

```kotlin
// 状态枚举
enum class WakeState {
    STANDBY,   // 待机：仅监听唤醒词
    ACTIVE     // 激活：进行语音识别
}

private var wakeState = WakeState.STANDBY
private var keywordSpotter: KeywordSpotter? = null
private var kwsStream: OnlineStream? = null
private var kwsJob: Job? = null

// 初始化 KWS
private fun initializeKws() {
    lifecycleScope.launch(Dispatchers.IO) {
        if (modelManager.checkKwsExists()) {
            keywordSpotter = modelManager.createKeywordSpotter(
                keywordsFile = "keywords.txt",
                threshold = 0.5F,
                score = 1.0F
            )

            withContext(Dispatchers.Main) {
                if (keywordSpotter != null) {
                    Log.i(TAG, "✓ KWS 模式已启用")
                    startKwsMonitoring()
                }
            }
        }
    }
}

// 开始 KWS 监听
private fun startKwsMonitoring() {
    kwsStream = keywordSpotter?.createStream()

    // 开始录音（不保存，不缓存）
    audioRecorder?.startRecording(savePcm = false, cacheInMemory = false)

    kwsJob = lifecycleScope.launch(Dispatchers.IO) {
        wakeState = WakeState.STANDBY

        while (isActive && wakeState == WakeState.STANDBY) {
            val samples = audioRecorder?.readAudioData()

            if (samples != null) {
                kwsStream?.acceptWaveform(samples, SAMPLE_RATE)

                while (keywordSpotter?.isReady(kwsStream!!) == true) {
                    keywordSpotter?.decode(kwsStream!!)
                }

                val result = keywordSpotter?.getResult(kwsStream!!)
                if (result != null && result.keyword.isNotEmpty()) {
                    Log.i(TAG, "🔊 检测到唤醒词: ${result.keyword}")

                    withContext(Dispatchers.Main) {
                        onWakeWordDetected(result.keyword)
                    }

                    keywordSpotter?.reset(kwsStream!!)
                }
            }
        }
    }
}

// 唤醒词检测后的处理
private fun onWakeWordDetected(keyword: String) {
    Log.i(TAG, "唤醒词触发: $keyword")

    // 停止 KWS
    kwsJob?.cancel()
    kwsStream?.release()
    kwsStream = null

    // 切换到 ACTIVE 模式
    wakeState = WakeState.ACTIVE

    // 启动 ASR 识别
    startAsrRecognition()
}

// 启动 ASR 识别
private fun startAsrRecognition() {
    lifecycleScope.launch(Dispatchers.IO) {
        // 动态创建识别器（避免与 KWS 资源冲突）
        if (recognizer == null) {
            recognizer = modelManager.createOnlineRecognizer()
        }

        if (vad == null) {
            vad = modelManager.createVad(
                threshold = 0.5F,
                minSilenceDuration = 1.0F,
                minSpeechDuration = 0.25F,
                maxSpeechDuration = 10.0F
            )
        }

        stream = recognizer?.createStream()

        withContext(Dispatchers.Main) {
            Log.i(TAG, "🎙️ 正在识别...")
            startRecognitionTask()
        }
    }
}
```

---

## 技术细节

### 模型路径

所有模型文件存储在应用内部存储：
```
/data/data/你的包名/files/models/
├── asr/
│   ├── encoder-epoch-99-avg-1.onnx
│   ├── decoder-epoch-99-avg-1.onnx
│   ├── joiner-epoch-99-avg-1.onnx
│   ├── tokens.txt
│   ├── lexicon.txt (可选)
│   └── replace.fst (可选)
├── kws/
│   ├── encoder-epoch-12-avg-2-chunk-16-left-64.onnx
│   ├── decoder-epoch-12-avg-2-chunk-16-left-64.onnx
│   ├── joiner-epoch-12-avg-2-chunk-16-left-64.onnx
│   ├── tokens.txt
│   └── keywords.txt
└── vad/
    └── silero_vad.onnx
```

### 音频格式

- **采样率**: 16000 Hz
- **声道**: 单声道 (MONO)
- **位深度**: 16-bit PCM
- **数据范围**: -1.0 到 1.0 (FloatArray)

### 线程模型

- `ModelManager.createOnlineRecognizer()`: IO线程（阻塞操作）
- `AudioRecorder.readAudioData()`: IO线程（suspend函数，阻塞读取）
- 识别循环: 协程 (Dispatchers.IO)
- UI更新: 主线程 (withContext(Dispatchers.Main))

### VAD 断句机制

VAD 使用 Silero VAD 模型，内部维护语音段队列：

1. `acceptWaveform(samples)` - 送入音频数据
2. VAD 检测语音活动
3. 当静音时长达到 `minSilenceDuration` 时，认为语音段结束
4. 完整的语音段放入队列
5. 通过 `empty()`, `front()`, `pop()` 访问队列

**参数调优**:
- `threshold`: 0.3-0.6（值越小越敏感）
- `minSilenceDuration`: 0.5-2.0s（值越小断句越快）
- `minSpeechDuration`: 0.2-0.5s（过滤短噪音）
- `maxSpeechDuration`: 5-15s（强制断句）

### HomophoneReplacer（热词/同音字纠正）

如果 ASR 模型目录存在 `lexicon.txt` 和 `replace.fst` 文件，会自动启用同音字纠正功能。

**详细使用说明请参见下方 "HomophoneReplacer 使用说明" 章节。**

### 资源管理

**重要**: 必须在 Activity/Fragment 的 `onDestroy()` 中释放资源：

```kotlin
override fun onDestroy() {
    super.onDestroy()
    recognizer?.release()
    vad?.release()
    keywordSpotter?.release()
    stream?.release()
    kwsStream?.release()
    audioRecorder = null
}
```

### 性能优化

1. **线程数**: 默认使用 CPU 核心数，可以限制为 2-4
2. **模型选择**: 使用 14M 模型（快速）vs 140M 模型（准确）
3. **内存优化**: KWS 模式下动态创建/释放 ASR 资源
4. **缓存管理**: 定期清理音频缓存文件

---

## 模型下载

### 必需: ASR 模型

**推荐: Zipformer Transducer (中英文双语)**

下载地址:
```
https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2
```

需要的文件:
- `encoder-epoch-99-avg-1.onnx`
- `decoder-epoch-99-avg-1.onnx`
- `joiner-epoch-99-avg-1.onnx`
- `tokens.txt`

部署到: `/data/data/你的包名/files/models/asr/`

### 可选: KWS 模型

下载地址:
```
https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01.tar.bz2
```

需要的文件:
- `encoder-epoch-12-avg-2-chunk-16-left-64.onnx`
- `decoder-epoch-12-avg-2-chunk-16-left-64.onnx`
- `joiner-epoch-12-avg-2-chunk-16-left-64.onnx`
- `tokens.txt`
- `keywords.txt` (自己创建)

部署到: `/data/data/你的包名/files/models/kws/`

### 推荐: VAD 模型

下载地址:
```
https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx
```

文件名: `silero_vad.onnx`

部署到: `/data/data/你的包名/files/models/vad/`

### 部署方法

使用 adb:
```bash
# 推送到 SD 卡
adb push models/ /sdcard/

# 进入设备
adb shell
su

# 复制到应用内部存储
cp -r /sdcard/models /data/data/你的包名/files/

# 修改权限
chmod -R 755 /data/data/你的包名/files/models/
```

---

## 7️⃣ HomophoneReplacer - 热词/同音字纠正

**来自**: sherpa-onnx 库（com.k2fsa.sherpa.onnx）

### 功能说明

HomophoneReplacer 是 Sherpa-ONNX 的同音字自动纠正功能。当 ASR 模型目录存在 `lexicon.txt` 和 `replace.fst` 文件时，会自动启用该功能，无需修改代码。

**纠正示例**:
- "在坐" → "在座"
- "因该" → "应该"
- "做作业" → "做作业"（保持不变）

### 文件要求

| 文件 | 说明 | 路径 |
|------|------|------|
| `lexicon.txt` | 词典文件（拼音映射） | `/data/data/你的包名/files/models/asr/` |
| `replace.fst` | 替换规则（OpenFST 格式） | `/data/data/你的包名/files/models/asr/` |

**注意**: 两个文件必须同时存在才会启用功能。

### 使用方法

#### 下载文件

从 sherpa-onnx 官方获取：
```
https://github.com/k2-fsa/sherpa-onnx/releases/tag/hr-files
```

下载文件：
- `lexicon.txt`
- `replace.fst`

#### 部署文件

使用 adb 推送到设备：
```bash
adb push lexicon.txt /data/data/你的包名/files/models/asr/
adb push replace.fst /data/data/你的包名/files/models/asr/
```

#### 启用功能

无需修改代码，正常创建识别器即可：
```kotlin
val recognizer = modelManager.createOnlineRecognizer(
    modelType = ModelManager.ModelType.ZIPFORMER_TRANSDUCER
)
```

ModelManager 会自动检测文件并启用 HomophoneReplacer。

#### 验证启用

查看 Logcat 日志：
```
I/ModelManager: HomophoneReplacer enabled: lexicon=/data/data/.../lexicon.txt, fst=/data/data/.../replace.fst
```

如果未启用，日志会显示：
```
D/ModelManager: HomophoneReplacer disabled: lexicon.txt not found
D/ModelManager: HomophoneReplacer disabled: replace.fst not found
```

### 工作原理

ModelManager 在创建识别器时会自动检测这两个文件：

```kotlin
private fun createHomophoneReplacerConfig(): HomophoneReplacerConfig {
    val lexiconFile = File(asrDir, "lexicon.txt")
    val replaceFstFile = File(asrDir, "replace.fst")

    return if (lexiconFile.exists() && replaceFstFile.exists()) {
        HomophoneReplacerConfig(
            lexicon = lexiconFile.absolutePath,
            ruleFsts = replaceFstFile.absolutePath
        )
    } else {
        HomophoneReplacerConfig()
    }
}
```

### 完整目录结构

```
/data/data/你的包名/files/models/
├── asr/
│   ├── encoder-epoch-99-avg-1.onnx
│   ├── decoder-epoch-99-avg-1.onnx
│   ├── joiner-epoch-99-avg-1.onnx
│   ├── tokens.txt
│   ├── lexicon.txt       ← 同音字词典
│   └── replace.fst       ← 替换规则
├── kws/
│   └── ...
└── vad/
    └── silero_vad.onnx
```

### 文件格式

#### lexicon.txt 格式

词典文件，记录词语和拼音映射：

```
在坐 z ai4 z uo4
在座 z ai4 z uo4
因该 y in1 g ai1
应该 y ing1 g ai1
```

**格式**: `词语 拼音1 拼音2 拼音3 ...`

#### replace.fst 格式

替换规则文件（OpenFST 格式）：

```
0 1 在坐 在座
0 1 因该 应该
1
```

**格式**: `起始状态 目标状态 输入词 输出词`

**注意**: 这是二进制格式，需要用 OpenFST 工具编译生成，建议直接使用官方提供的文件。

### 自定义替换规则

#### 方法1: 修改现有文件

1. 编辑 `lexicon.txt`，添加新词:
   ```
   做站 z uo4 z han4
   做站 z uo4 z han4
   ```

2. 使用 OpenFST 工具重新编译 `replace.fst`

3. 替换设备上的文件，重新创建识别器

#### 方法2: 使用官方工具

sherpa-onnx 提供了工具来生成自定义规则：

```bash
# 安装 OpenFST
sudo apt-get install libfst-dev

# 使用 sherpa-onnx 工具生成 replace.fst
# 详见: https://k2-fsa.github.io/sherpa/onnx/hotwords/
```

### 测试验证

```kotlin
val recognizer = modelManager.createOnlineRecognizer()
val stream = recognizer?.createStream()

// 测试: 说 "我在坐在这里"
// 期望输出: "我在座在这里"（"在坐" 被纠正为 "在座"）
```

### 常见问题

#### Q1: 功能不生效

**排查步骤**:
1. 检查文件路径是否正确
   ```bash
   adb shell ls /data/data/你的包名/files/models/asr/lexicon.txt
   adb shell ls /data/data/你的包名/files/models/asr/replace.fst
   ```

2. 确认两个文件都存在（缺一不可）

3. 查看日志是否有 "HomophoneReplacer enabled"

#### Q2: 更新文件后需要重启应用吗？

需要重新加载识别器：

```kotlin
// 释放旧识别器
recognizer?.release()

// 重新创建（会重新检测文件）
recognizer = modelManager.createOnlineRecognizer()
```

#### Q3: 可以不使用该功能吗？

可以。如果不放置这两个文件，功能不会启用，不影响正常使用。

#### Q4: 文件放错位置会怎样？

ModelManager 找不到文件时，功能不会启用，但不会报错。日志会显示：
```
D/ModelManager: HomophoneReplacer disabled: lexicon.txt not found
```

#### Q5: 可以动态切换替换规则吗？

可以，步骤如下：
1. 替换设备上的文件（lexicon.txt 和 replace.fst）
2. 释放旧识别器
3. 重新创建识别器

```kotlin
// 替换文件
val newLexicon = File(modelManager.getModelDir(), "asr/lexicon.txt")
newLexicon.writeText("新的词典内容")

// 重新加载
recognizer?.release()
recognizer = modelManager.createOnlineRecognizer()
```

### 性能影响

| 指标 | 影响 |
|------|------|
| CPU 占用 | 几乎无影响（< 1%） |
| 内存占用 | 约 1-5 MB（取决于规则数量） |
| 识别延迟 | 几乎无影响（后处理，不影响实时性） |

### 最佳实践

1. **使用官方文件**: 直接使用 sherpa-onnx 官方提供的文件，已包含常见同音字错误
2. **定期更新**: 根据实际识别错误，定期更新替换规则
3. **测试验证**: 更新文件后，测试常见句子确保规则生效
4. **备份原文件**: 修改前备份原文件，避免出错

### 使用示例

```kotlin
// 初始化
val modelManager = ModelManager(context)

// 正常创建识别器（如果文件存在，会自动启用 HomophoneReplacer）
val recognizer = modelManager.createOnlineRecognizer(
    modelType = ModelManager.ModelType.ZIPFORMER_TRANSDUCER
)

val stream = recognizer?.createStream()

// 使用识别器
lifecycleScope.launch(Dispatchers.IO) {
    while (isRecording) {
        val samples = audioRecorder?.readAudioData()

        if (samples != null) {
            stream?.acceptWaveform(samples, 16000)

            while (recognizer?.isReady(stream!!) == true) {
                recognizer?.decode(stream!!)
            }

            val result = recognizer?.getResult(stream!!)
            val text = result?.text ?: ""

            // 识别结果已自动应用同音字纠正
            Log.i(TAG, "识别结果: $text")
        }
    }
}
```

---

**版本**: 1.0.0
**最后更新**: 2024-12-25
