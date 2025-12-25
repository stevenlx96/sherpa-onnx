# TTS AAR 使用说明

---

## TTS AAR包含的所有类

| 类名 | 文件 | 说明 |
|------|------|------|
| `OfflineTts` | Tts.kt | 核心 TTS 引擎 |
| `TtsManager` | TtsManager.kt | 高级封装管理器 |
| `OfflineTtsConfig` | Tts.kt | TTS 配置数据类 |
| `OfflineTtsModelConfig` | Tts.kt | 模型配置数据类 |
| `OfflineTtsMatchaModelConfig` | Tts.kt | Matcha 模型配置 |
| `OfflineTtsVitsModelConfig` | Tts.kt | VITS 模型配置 |
| `OfflineTtsKokoroModelConfig` | Tts.kt | Kokoro 模型配置 |
| `OfflineTtsKittenModelConfig` | Tts.kt | Kitten 模型配置 |
| `GeneratedAudio` | Tts.kt | 生成的音频数据类 |
| Native库 | libsherpa-onnx-jni.so | JNI封装的sherpa-onnx |

---

## 1. OfflineTts - 核心 TTS 引擎

### 构造函数
```kotlin
val tts = OfflineTts(
    assetManager: AssetManager? = null,
    config: OfflineTtsConfig
)
```
- **assetManager**: 如果从 assets 加载模型则传入，从文件加载传 null
- **config**: TTS 配置对象

### 主要方法

#### 生成语音（一次性）
```kotlin
fun generate(
    text: String,
    sid: Int = 0,
    speed: Float = 1.0f
): GeneratedAudio
```
- **功能**: 一次性生成完整音频
- **参数**:
  - `text`: 要合成的文本
  - `sid`: 说话人 ID（多说话人模型使用）
  - `speed`: 语速（0.5-2.0）
- **返回**: `GeneratedAudio` 对象，包含音频数据和采样率
- **示例**:
```kotlin
val audio = tts.generate(
    text = "你好，欢迎使用语音合成",
    sid = 0,
    speed = 1.0f
)
// audio.samples 是 FloatArray
// audio.sampleRate 是采样率
```

#### 生成语音（流式回调）
```kotlin
fun generateWithCallback(
    text: String,
    sid: Int = 0,
    speed: Float = 1.0f,
    callback: (samples: FloatArray) -> Int
): GeneratedAudio
```
- **功能**: 流式生成音频，边生成边回调
- **参数**:
  - `text`: 要合成的文本
  - `sid`: 说话人 ID
  - `speed`: 语速（0.5-2.0）
  - `callback`: 音频回调函数，返回 1 继续生成，返回 0 停止
- **返回**: `GeneratedAudio` 对象
- **示例**:
```kotlin
Thread {
    tts.generateWithCallback(
        text = "你好",
        sid = 0,
        speed = 1.0f,
        callback = { samples ->
            audioTrack.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            1  // 继续
        }
    )
}.start()
```

### 查询方法

```kotlin
fun sampleRate(): Int          // 获取采样率（通常 22050）
fun numSpeakers(): Int         // 获取说话人数量
```

### 资源管理

```kotlin
fun allocate(assetManager: AssetManager? = null)  // 重新分配资源
fun free()                     // 释放资源
fun release()                  // 释放资源（同 free）
```
- **重要**: 在 `onDestroy()` 中调用 `release()`

---

## 2. TtsManager - 高级封装管理器

### 构造函数
```kotlin
val ttsManager = TtsManager(context: Context)
```

### 主要方法

#### 初始化 TTS
```kotlin
fun initialize(modelDir: String, modelType: String = "matcha")
```
- **功能**: 初始化 TTS 引擎和 AudioTrack
- **参数**:
  - `modelDir`: 模型目录名称（相对于 `/data/data/包名/files/models/tts/`）
  - `modelType`: 模型类型，`"matcha"` 或 `"vits"`
- **示例**:
```kotlin
val ttsManager = TtsManager(context)
ttsManager.initialize("", "matcha")  // 空字符串表示直接在 tts 目录下
```

#### 朗读文本
```kotlin
fun speak(
    text: String,
    speed: Float = 0.8f,
    sid: Int = 0,
    callback: ((FloatArray) -> Int)? = null
)
```
- **功能**: 朗读文本（自动管理 AudioTrack）
- **参数**:
  - `text`: 要朗读的文本
  - `speed`: 语速（0.5-2.0，默认 0.8）
  - `sid`: 说话人 ID
  - `callback`: 可选的音频回调
- **示例**:
```kotlin
ttsManager.speak("你好，欢迎使用", speed = 1.0f)
```

#### 停止朗读
```kotlin
fun stop()
```

#### 释放资源
```kotlin
fun release()
```

### 查询方法

```kotlin
fun getNumSpeakers(): Int      // 获取说话人数量
fun getSampleRate(): Int       // 获取采样率
```

---

## 3. OfflineTtsConfig - TTS 配置数据类

### 定义
```kotlin
data class OfflineTtsConfig(
    var model: OfflineTtsModelConfig = OfflineTtsModelConfig(),
    var ruleFsts: String = "",
    var ruleFars: String = "",
    var maxNumSentences: Int = 1,
    var silenceScale: Float = 0.2f
)
```

### 参数说明

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `model` | OfflineTtsModelConfig | - | 模型配置 |
| `ruleFsts` | String | "" | 规则文件路径（逗号分隔） |
| `ruleFars` | String | "" | FAR 规则文件路径 |
| `maxNumSentences` | Int | 1 | 最大句子数 |
| `silenceScale` | Float | 0.2 | 停顿时长缩放（0.0-1.0） |

### 使用示例
```kotlin
val config = OfflineTtsConfig(
    model = OfflineTtsModelConfig(
        matcha = OfflineTtsMatchaModelConfig(
            acousticModel = "$modelPath/model.onnx",
            vocoder = "$modelPath/vocos-22khz-univ.onnx",
            lexicon = "$modelPath/lexicon.txt",
            tokens = "$modelPath/tokens.txt",
            dataDir = modelPath
        ),
        numThreads = 4,
        provider = "cpu"
    ),
    ruleFsts = "$modelPath/phone.fst,$modelPath/date.fst",
    silenceScale = 0.6f
)
```

---

## 4. OfflineTtsModelConfig - 模型配置数据类

### 定义
```kotlin
data class OfflineTtsModelConfig(
    var vits: OfflineTtsVitsModelConfig = OfflineTtsVitsModelConfig(),
    var matcha: OfflineTtsMatchaModelConfig = OfflineTtsMatchaModelConfig(),
    var kokoro: OfflineTtsKokoroModelConfig = OfflineTtsKokoroModelConfig(),
    var kitten: OfflineTtsKittenModelConfig = OfflineTtsKittenModelConfig(),
    var numThreads: Int = 1,
    var debug: Boolean = false,
    var provider: String = "cpu"
)
```

### 参数说明

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `vits` | OfflineTtsVitsModelConfig | - | VITS 模型配置 |
| `matcha` | OfflineTtsMatchaModelConfig | - | Matcha 模型配置 |
| `kokoro` | OfflineTtsKokoroModelConfig | - | Kokoro 模型配置 |
| `kitten` | OfflineTtsKittenModelConfig | - | Kitten 模型配置 |
| `numThreads` | Int | 1 | 推理线程数（推荐 4） |
| `debug` | Boolean | false | 调试模式 |
| `provider` | String | "cpu" | 计算提供者（cpu/gpu） |

---

## 5. OfflineTtsMatchaModelConfig - Matcha 模型配置

### 定义
```kotlin
data class OfflineTtsMatchaModelConfig(
    var acousticModel: String = "",
    var vocoder: String = "",
    var lexicon: String = "",
    var tokens: String = "",
    var dataDir: String = "",
    var dictDir: String = "",
    var noiseScale: Float = 1.0f,
    var lengthScale: Float = 1.0f
)
```

### 参数说明

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `acousticModel` | String | "" | 声学模型路径（model.onnx） |
| `vocoder` | String | "" | 声码器路径（vocos-22khz-univ.onnx，必需） |
| `lexicon` | String | "" | 词典路径（lexicon.txt） |
| `tokens` | String | "" | 音素列表路径（tokens.txt） |
| `dataDir` | String | "" | 数据目录路径 |
| `dictDir` | String | "" | 字典目录（未使用） |
| `noiseScale` | Float | 1.0 | 情感起伏（0.0-2.0，推荐 0.6-1.0） |
| `lengthScale` | Float | 1.0 | 音长缩放（0.5-2.0，推荐 0.9-1.2） |

### 使用示例
```kotlin
val matchaConfig = OfflineTtsMatchaModelConfig(
    acousticModel = "/path/to/model.onnx",
    vocoder = "/path/to/vocos-22khz-univ.onnx",
    lexicon = "/path/to/lexicon.txt",
    tokens = "/path/to/tokens.txt",
    dataDir = "/path/to/models/tts",
    noiseScale = 0.8f,    // 自然情感
    lengthScale = 1.05f   // 稍微拉长音素
)
```

---

## 6. OfflineTtsVitsModelConfig - VITS 模型配置

### 定义
```kotlin
data class OfflineTtsVitsModelConfig(
    var model: String = "",
    var lexicon: String = "",
    var tokens: String = "",
    var dataDir: String = "",
    var dictDir: String = "",
    var noiseScale: Float = 0.667f,
    var noiseScaleW: Float = 0.8f,
    var lengthScale: Float = 1.0f
)
```

### 参数说明

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `model` | String | "" | VITS 模型路径 |
| `lexicon` | String | "" | 词典路径 |
| `tokens` | String | "" | 音素列表路径 |
| `dataDir` | String | "" | 数据目录 |
| `dictDir` | String | "" | 字典目录（未使用） |
| `noiseScale` | Float | 0.667 | 噪声缩放 |
| `noiseScaleW` | Float | 0.8 | 噪声宽度缩放 |
| `lengthScale` | Float | 1.0 | 音长缩放 |

---

## 7. GeneratedAudio - 生成的音频数据类

### 定义
```kotlin
class GeneratedAudio(
    val samples: FloatArray,
    val sampleRate: Int
)
```

### 属性

| 属性 | 类型 | 说明 |
|------|------|------|
| `samples` | FloatArray | 音频样本数组（归一化到 -1 到 1） |
| `sampleRate` | Int | 采样率（Hz） |

### 方法

```kotlin
fun save(filename: String): Boolean
```
- **功能**: 保存音频到 WAV 文件
- **参数**: `filename` - 文件路径
- **返回**: 成功返回 true

### 使用示例
```kotlin
val audio = tts.generate("你好", sid = 0, speed = 1.0f)

// 获取音频数据
val samples = audio.samples      // FloatArray
val sampleRate = audio.sampleRate  // 22050

// 保存到文件
audio.save("/sdcard/output.wav")

// 使用 AudioTrack 播放
audioTrack.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
```

---

## 模型文件准备

### Matcha-TTS 所需文件

将模型文件放在 `/data/data/YOUR_PACKAGE/files/models/tts/` 目录：

```
models/tts/
├── model.onnx                    # 必需: 声学模型
├── vocos-22khz-univ.onnx         # 必需: 声码器
├── lexicon.txt                   # 必需: 词典
├── tokens.txt                    # 必需: 音素列表
├── dict/                         # 必需: 字典目录
├── phone.fst                     # 可选: 电话号码规则
├── date.fst                      # 可选: 日期规则
└── number.fst                    # 可选: 数字规则
```

### 下载和推送

```bash
# 1. 下载模型
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/matcha-icefall-zh-baker.tar.bz2
tar -xjf matcha-icefall-zh-baker.tar.bz2

# 2. 推送到设备
adb push matcha-icefall-zh-baker /data/local/tmp/

# 3. 复制到应用目录
adb shell
su
mkdir -p /data/data/YOUR_PACKAGE/files/models/tts
cp -r /data/local/tmp/matcha-icefall-zh-baker/* /data/data/YOUR_PACKAGE/files/models/tts/
chmod -R 755 /data/data/YOUR_PACKAGE/files/models/tts/
```

**MODELSCOPE 下载 URL**: `https://www.modelscope.cn/models/k2-fsa/sherpa-onnx-tts-models/files`

---

## 完整使用示例

### 方式 1: 使用 TtsManager（推荐）

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var ttsManager: TtsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化 TtsManager
        ttsManager = TtsManager(this)

        // 初始化模型（模型文件直接在 tts 目录下）
        ttsManager.initialize("", "matcha")
    }

    fun speakText(text: String) {
        // 朗读文本
        ttsManager.speak(
            text = text,
            speed = 1.0f,
            sid = 0
        )
    }

    fun stopSpeaking() {
        ttsManager.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsManager.release()
    }
}
```

### 方式 2: 使用 OfflineTts（完全控制）

```kotlin
import com.k2fsa.sherpa.onnx.*
import android.media.AudioTrack
import android.media.AudioFormat
import android.media.AudioAttributes
import android.media.AudioManager
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var tts: OfflineTts
    private lateinit var audioTrack: AudioTrack
    private var isSpeaking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initTts()
        initAudioTrack()
    }

    private fun initTts() {
        val modelPath = File(filesDir, "models/tts").absolutePath

        val ruleFsts = listOf(
            "$modelPath/phone.fst",
            "$modelPath/date.fst",
            "$modelPath/number.fst"
        ).filter { File(it).exists() }.joinToString(",")

        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                matcha = OfflineTtsMatchaModelConfig(
                    acousticModel = "$modelPath/model.onnx",
                    vocoder = "$modelPath/vocos-22khz-univ.onnx",
                    lexicon = "$modelPath/lexicon.txt",
                    tokens = "$modelPath/tokens.txt",
                    dataDir = modelPath,
                    noiseScale = 0.8f,
                    lengthScale = 1.05f
                ),
                numThreads = 4,
                debug = false,
                provider = "cpu"
            ),
            ruleFsts = ruleFsts,
            silenceScale = 0.6f
        )

        tts = OfflineTts(assetManager = null, config = config)
    }

    private fun initAudioTrack() {
        val sampleRate = tts.sampleRate()
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setSampleRate(sampleRate)
            .build()

        audioTrack = AudioTrack(
            audioAttributes,
            audioFormat,
            bufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        audioTrack.setVolume(AudioTrack.getMaxVolume())
    }

    fun speak(text: String, speed: Float = 1.0f) {
        if (isSpeaking) return

        isSpeaking = true
        audioTrack.play()

        Thread {
            tts.generateWithCallback(
                text = text,
                sid = 0,
                speed = speed,
                callback = { samples ->
                    if (isSpeaking) {
                        audioTrack.write(
                            samples,
                            0,
                            samples.size,
                            AudioTrack.WRITE_BLOCKING
                        )
                        1
                    } else {
                        0
                    }
                }
            )

            runOnUiThread {
                stopSpeaking()
            }
        }.start()
    }

    fun stopSpeaking() {
        isSpeaking = false
        audioTrack.pause()
        audioTrack.flush()
    }

    override fun onDestroy() {
        super.onDestroy()

        if (::audioTrack.isInitialized) {
            audioTrack.stop()
            audioTrack.release()
        }

        if (::tts.isInitialized) {
            tts.release()
        }
    }
}
```

### 参数调整示例

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var tts: OfflineTts
    private val modelPath = File(filesDir, "models/tts").absolutePath

    // 重新初始化 TTS（修改情感和音长参数）
    fun reinitTts(noiseScale: Float, lengthScale: Float) {
        if (::tts.isInitialized) {
            tts.release()
        }

        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                matcha = OfflineTtsMatchaModelConfig(
                    acousticModel = "$modelPath/model.onnx",
                    vocoder = "$modelPath/vocos-22khz-univ.onnx",
                    lexicon = "$modelPath/lexicon.txt",
                    tokens = "$modelPath/tokens.txt",
                    dataDir = modelPath,
                    noiseScale = noiseScale,
                    lengthScale = lengthScale
                ),
                numThreads = 4,
                provider = "cpu"
            ),
            silenceScale = 0.6f
        )

        tts = OfflineTts(assetManager = null, config = config)
    }

    // 应用场景：新闻播报
    fun setupNewsMode() {
        reinitTts(noiseScale = 0.6f, lengthScale = 0.9f)
    }

    // 应用场景：儿童故事
    fun setupStoryMode() {
        reinitTts(noiseScale = 1.2f, lengthScale = 1.1f)
    }

    // 应用场景：语言学习
    fun setupLearningMode() {
        reinitTts(noiseScale = 0.8f, lengthScale = 1.2f)
    }
}
```

---

## 技术细节

### 线程模型
- **TTS 生成**: 必须在子线程执行（`generate` 和 `generateWithCallback` 都是阻塞调用）
- **AudioTrack 写入**: 可在子线程执行（使用 `WRITE_BLOCKING` 模式）
- **UI 更新**: 使用 `runOnUiThread` 切换到主线程

### 参数约束

| 参数 | 范围 | 推荐值 | 运行时调整 |
|------|------|--------|-----------|
| `speed` | 0.5 - 2.0 | 1.0 | ✅ 是 |
| `noiseScale` | 0.0 - 2.0 | 0.6 - 1.0 | ❌ 否（需重新初始化） |
| `lengthScale` | 0.5 - 2.0 | 0.9 - 1.2 | ❌ 否（需重新初始化） |
| `silenceScale` | 0.0 - 1.0 | 0.6 | ❌ 否（需重新初始化） |
| `numThreads` | 1 - 8 | 4 | ❌ 否（需重新初始化） |

### 音频格式
- **采样率**: 22050 Hz
- **声道**: 单声道（Mono）
- **编码**: PCM Float（32-bit）
- **样本范围**: -1.0 到 1.0

### 常量
- `SAMPLE_RATE`: 22050 (TtsManager 使用)

---

## 集成步骤

### 1. 添加依赖

在 `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation(files("libs/tts.aar"))
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
}
```

### 2. 环境要求

- **minSdk**: 21 (Android 5.0)
- **targetSdk**: 34
- **compileSdk**: 34
- **JDK**: 17+
- **Kotlin**: 1.9.23+

### 3. 权限（如需保存文件）

在 `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

---

## 常见问题

### 模型加载失败

**错误**: `--matcha-acoustic-model: '.../model.onnx' does not exist`

**解决**:
```bash
adb shell "ls -la /data/data/YOUR_PACKAGE/files/models/tts/"
# 确认文件存在
```

### 缺少 vocoder

**错误**: `Vocoder is not specified. Return an empty wave`

**解决**: Matcha 模型必须指定 `vocoder` 参数
```kotlin
vocoder = "$modelPath/vocos-22khz-univ.onnx"
```

### 播放无声音

**检查**:
```kotlin
// 检查采样率
Log.d("TTS", "Sample rate: ${tts.sampleRate()}")

// 检查 AudioTrack 状态
Log.d("TTS", "AudioTrack state: ${audioTrack.state}")

// 确保音量最大
audioTrack.setVolume(AudioTrack.getMaxVolume())

// 确保调用了 play
audioTrack.play()
```

---
