# SherpaOnnx TTS AAR 使用说明

## 📦 AAR 包简介

本 AAR 包（`tts.aar`）提供离线 TTS（文本转语音）功能，基于 sherpa-onnx 引擎，支持 Matcha-TTS 模型。

---

## 🔧 环境要求

### 开发环境

- **JDK**: 17 或更高版本
- **Gradle**: 8.13
- **Android Gradle Plugin (AGP)**: 8.4.0
- **Kotlin**: 1.9.23

### Android 要求

- **minSdk**: 21 (Android 5.0)
- **targetSdk**: 34 (Android 14)
- **compileSdk**: 34

---

## 📦 集成 AAR

### 1. 复制 AAR 文件

将 `tts.aar` 复制到项目：

```bash
cp tts.aar YourProject/app/libs/
```

### 2. 配置依赖

在 `app/build.gradle.kts` 中添加：

```kotlin
dependencies {
    // 引入 AAR 文件
    implementation(files("libs/tts.aar"))

    // 必需的外部依赖
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

---

## 📂 模型文件准备

### 需要的文件

使用 Matcha-TTS 模型需要以下文件（从 `matcha-icefall-zh-baker.tar.bz2` 解压）：

```
models/tts/
├── model.onnx                    # 必需: 声学模型
├── vocos-22khz-univ.onnx         # 必需: 声码器
├── lexicon.txt                   # 必需: 词典
├── tokens.txt                    # 必需: 音素列表
├── dict/                         # 必需: 字典目录（包含多个文件）
├── phone.fst                     # 可选: 电话号码规则
├── date.fst                      # 可选: 日期规则
└── number.fst                    # 可选: 数字规则
```

### 下载模型

```bash
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/matcha-icefall-zh-baker.tar.bz2
tar -xjf matcha-icefall-zh-baker.tar.bz2
```

### 推送到设备

```bash
# 推送到设备临时目录
adb push matcha-icefall-zh-baker /data/local/tmp/

# 复制到应用私有目录（直接放在 tts 目录下）
adb shell
su
mkdir -p /data/data/YOUR_PACKAGE/files/models/tts
cp -r /data/local/tmp/matcha-icefall-zh-baker/* /data/data/YOUR_PACKAGE/files/models/tts/
chmod -R 755 /data/data/YOUR_PACKAGE/files/models/tts/
exit
```

**验证**：

```bash
adb shell "ls -la /data/data/YOUR_PACKAGE/files/models/tts/"
```

应该看到：`model.onnx`、`vocos-22khz-univ.onnx`、`lexicon.txt`、`tokens.txt`、`dict/` 等文件。

---

## 🎯 核心功能

### 1. 基础初始化

在 Activity 或服务中初始化 TTS：

```kotlin
import com.k2fsa.sherpa.onnx.*
import java.io.File

class YourActivity : AppCompatActivity() {

    private lateinit var tts: OfflineTts

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化 TTS
        initTts()
    }

    private fun initTts() {
        // 模型路径（直接指向 tts 目录）
        val modelPath = File(filesDir, "models/tts").absolutePath

        // 检查并配置规则文件（可选）
        val ruleFsts = listOf(
            "$modelPath/phone.fst",
            "$modelPath/date.fst",
            "$modelPath/number.fst"
        ).filter { File(it).exists() }.joinToString(",")

        // 创建配置
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                matcha = OfflineTtsMatchaModelConfig(
                    acousticModel = "$modelPath/model.onnx",
                    vocoder = "$modelPath/vocos-22khz-univ.onnx",
                    lexicon = "$modelPath/lexicon.txt",
                    tokens = "$modelPath/tokens.txt",
                    dataDir = modelPath,
                    noiseScale = 0.8f,      // 情感起伏 (0.0-2.0)
                    lengthScale = 1.05f     // 音长缩放 (0.5-2.0)
                ),
                numThreads = 4,             // 线程数，推荐 4
                debug = false,              // 调试模式
                provider = "cpu"            // 计算提供者
            ),
            ruleFsts = ruleFsts,            // 规则文件路径
            silenceScale = 0.6f             // 停顿时长 (0.0-1.0)
        )

        // 初始化 TTS 对象
        tts = OfflineTts(assetManager = null, config = config)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 释放资源
        if (::tts.isInitialized) {
            tts.release()
        }
    }
}
```

### 2. 语音合成

#### 方式 1：流式回调（推荐）

适合实时播放，边生成边播放，延迟低：

```kotlin
import android.media.AudioTrack
import android.media.AudioFormat
import android.media.AudioAttributes
import android.media.AudioManager

class YourActivity : AppCompatActivity() {

    private lateinit var tts: OfflineTts
    private lateinit var audioTrack: AudioTrack
    private var isSpeaking = false

    // 生成并播放语音
    fun speak(text: String, speed: Float = 1.0f) {
        isSpeaking = true
        audioTrack.play()

        // 在子线程中生成音频
        Thread {
            tts.generateWithCallback(
                text = text,
                sid = 0,                    // 说话人 ID（通常为 0）
                speed = speed,              // 语速 (0.5-2.0)
                callback = { samples ->
                    // 每次生成一小段音频就调用此回调
                    if (isSpeaking) {
                        // 将音频数据写入 AudioTrack 播放
                        audioTrack.write(
                            samples,
                            0,
                            samples.size,
                            AudioTrack.WRITE_BLOCKING
                        )
                        1  // 返回 1 继续生成
                    } else {
                        0  // 返回 0 停止生成
                    }
                }
            )

            // 播放完成后的处理
            runOnUiThread {
                stopSpeaking()
            }
        }.start()
    }

    // 停止播放
    fun stopSpeaking() {
        isSpeaking = false
        audioTrack.pause()
        audioTrack.flush()
    }
}
```

#### 方式 2：一次性生成

适合需要保存音频或后续处理：

```kotlin
fun generateAudio(text: String, speed: Float = 1.0f): FloatArray {
    // 一次性生成完整音频
    val audio = tts.generate(
        text = text,
        sid = 0,
        speed = speed
    )

    // audio 是完整的音频数据 (FloatArray)
    // 可以保存到文件或直接播放
    return audio
}

// 使用示例
fun saveToFile(text: String) {
    val audioData = generateAudio(text, speed = 1.0f)

    // 保存到 WAV 文件
    val file = File(getExternalFilesDir(null), "tts_output.wav")
    // ... 写入 WAV 文件的代码
}
```

### 3. 参数调节

#### 动态调整语速

语速可以在运行时动态调整，无需重新初始化：

```kotlin
// 正常速度
speak("这是正常速度的朗读", speed = 1.0f)

// 慢速朗读（适合学习）
speak("这是慢速朗读，更清晰", speed = 0.8f)

// 快速朗读（适合浏览）
speak("这是快速朗读，节省时间", speed = 1.5f)
```

**参数说明**：
- **范围**：0.5 - 2.0
- **默认值**：1.0
- **效果**：值越大语速越快
- **是否需要重新初始化**：❌ 否

#### 调整情感起伏和音长

这两个参数需要在初始化时设置，修改后需要重新创建 `OfflineTts` 对象：

```kotlin
class YourActivity : AppCompatActivity() {

    private lateinit var tts: OfflineTts
    private val modelPath = File(filesDir, "models/tts").absolutePath

    // 根据参数重新初始化 TTS
    fun reinitTts(noiseScale: Float, lengthScale: Float) {
        // 释放旧对象
        if (::tts.isInitialized) {
            tts.release()
        }

        // 创建新配置
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                matcha = OfflineTtsMatchaModelConfig(
                    acousticModel = "$modelPath/model.onnx",
                    vocoder = "$modelPath/vocos-22khz-univ.onnx",
                    lexicon = "$modelPath/lexicon.txt",
                    tokens = "$modelPath/tokens.txt",
                    dataDir = modelPath,
                    noiseScale = noiseScale,    // 情感起伏
                    lengthScale = lengthScale   // 音长缩放
                ),
                numThreads = 4,
                provider = "cpu"
            ),
            ruleFsts = "",
            silenceScale = 0.6f
        )

        // 创建新对象
        tts = OfflineTts(assetManager = null, config = config)
    }

    // 使用示例
    fun applySettings() {
        // 设置情感丰富、音素稍长的效果
        reinitTts(noiseScale = 1.2f, lengthScale = 1.1f)

        // 然后进行朗读
        speak("现在使用新的参数朗读", speed = 1.0f)
    }
}
```

**情感起伏（Noise Scale）**：
- **范围**：0.0 - 2.0（推荐 0.6 - 1.0）
- **默认值**：0.8
- **效果**：
  - `0.0` - 完全平淡，机器感强
  - `0.6-0.8` - 自然情感（推荐）
  - `1.0-2.0` - 情感丰富到夸张
- **是否需要重新初始化**：✅ 是

**音长缩放（Length Scale）**：
- **范围**：0.5 - 2.0（推荐 0.9 - 1.2）
- **默认值**：1.05
- **效果**：
  - `< 1.0` - 音素缩短
  - `= 1.0` - 标准长度
  - `> 1.0` - 音素拉长
- **是否需要重新初始化**：✅ 是

### 4. 规则处理

自动识别和处理特殊文本格式，使朗读更自然：

```kotlin
private fun initTtsWithRules() {
    val modelPath = File(filesDir, "models/tts").absolutePath

    // 检查规则文件是否存在
    val ruleFsts = listOf(
        "$modelPath/phone.fst",    // 电话号码规则
        "$modelPath/date.fst",     // 日期规则
        "$modelPath/number.fst"    // 数字规则
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
            provider = "cpu"
        ),
        ruleFsts = ruleFsts,  // 设置规则文件
        silenceScale = 0.6f
    )

    tts = OfflineTts(assetManager = null, config = config)
}
```

**效果示例**：
```kotlin
// 原始文本 -> 实际朗读
speak("我的电话是138-0013-8000")
// 朗读为："我的电话是一三八零零一三八零零零"

speak("今天是2025年1月15日")
// 朗读为："今天是二零二五年一月十五日"

speak("金额是12345元")
// 朗读为："金额是一万两千三百四十五元"
```

### 5. 音频播放

#### 初始化 AudioTrack

在 Activity 的 `onCreate` 或 TTS 初始化后创建 AudioTrack：

```kotlin
import android.media.AudioTrack
import android.media.AudioFormat
import android.media.AudioAttributes
import android.media.AudioManager

class YourActivity : AppCompatActivity() {

    private lateinit var tts: OfflineTts
    private lateinit var audioTrack: AudioTrack

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 先初始化 TTS
        initTts()

        // 再初始化 AudioTrack
        initAudioTrack()
    }

    private fun initAudioTrack() {
        // 获取 TTS 的采样率（通常是 22050 Hz）
        val sampleRate = tts.sampleRate()

        // 计算缓冲区大小
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )

        // 创建音频属性
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        // 创建音频格式
        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setSampleRate(sampleRate)
            .build()

        // 创建 AudioTrack
        audioTrack = AudioTrack(
            audioAttributes,
            audioFormat,
            bufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        // 设置音量
        audioTrack.setVolume(AudioTrack.getMaxVolume())
    }

    override fun onDestroy() {
        super.onDestroy()

        // 释放资源
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

#### 完整的播放控制

包含播放、停止、状态管理：

```kotlin
class YourActivity : AppCompatActivity() {

    private lateinit var tts: OfflineTts
    private lateinit var audioTrack: AudioTrack
    private var isSpeaking = false

    // 开始朗读
    fun startSpeaking(text: String, speed: Float = 1.0f) {
        // 如果正在朗读，先停止
        if (isSpeaking) {
            stopSpeaking()
        }

        // 标记为正在朗读
        isSpeaking = true

        // 清空缓冲区
        audioTrack.pause()
        audioTrack.flush()

        // 开始播放
        audioTrack.play()

        // 在子线程中生成音频
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
                        1  // 继续
                    } else {
                        0  // 停止
                    }
                }
            )

            // 播放完成
            runOnUiThread {
                onSpeakingComplete()
            }
        }.start()
    }

    // 停止朗读
    fun stopSpeaking() {
        isSpeaking = false
        audioTrack.pause()
        audioTrack.flush()
    }

    // 播放完成回调
    private fun onSpeakingComplete() {
        isSpeaking = false
        // 可以在这里更新 UI 或执行其他操作
    }
}
```

---

## 📊 参数快速参考

| 参数 | 位置 | 范围 | 默认值 | 是否需要重新初始化 |
|------|------|------|--------|-------------------|
| **speed** | `generateWithCallback(speed = ?)` | 0.5 - 2.0 | 1.0 | ❌ 否 |
| **noiseScale** | `OfflineTtsMatchaModelConfig(noiseScale = ?)` | 0.0 - 2.0 | 0.8 | ✅ 是 |
| **lengthScale** | `OfflineTtsMatchaModelConfig(lengthScale = ?)` | 0.5 - 2.0 | 1.05 | ✅ 是 |
| **silenceScale** | `OfflineTtsConfig(silenceScale = ?)` | 0.0 - 1.0 | 0.6 | ✅ 是 |

---

## 💡 应用场景示例

### 场景 1: 新闻播报 App

**需求**：快速播报新闻，语音平稳专业

```kotlin
class NewsActivity : AppCompatActivity() {

    private lateinit var tts: OfflineTts
    private lateinit var audioTrack: AudioTrack

    fun readNews(newsContent: String) {
        // 先设置参数（只需要设置一次）
        setupNewsMode()

        // 使用快速语速播报
        startSpeaking(newsContent, speed = 1.5f)
    }

    private fun setupNewsMode() {
        // 新闻播报模式：低情感、短音素
        reinitTts(
            noiseScale = 0.6f,    // 降低情感，更平稳
            lengthScale = 0.9f    // 缩短音素，更紧凑
        )
    }
}
```

### 场景 2: 儿童故事 App

**需求**：生动有趣，节奏适中

```kotlin
class StoryActivity : AppCompatActivity() {

    fun readStory(storyText: String) {
        // 设置故事模式
        setupStoryMode()

        // 使用稍慢语速，更清晰
        startSpeaking(storyText, speed = 0.9f)
    }

    private fun setupStoryMode() {
        // 故事模式：高情感、稍长音素
        reinitTts(
            noiseScale = 1.2f,    // 增强情感，更生动
            lengthScale = 1.1f    // 拉长音素，更有节奏感
        )
    }
}
```

### 场景 3: 视障辅助 App

**需求**：清晰易懂，速度适中

```kotlin
class AccessibilityActivity : AppCompatActivity() {

    fun readScreenContent(content: String) {
        // 设置无障碍模式
        setupAccessibilityMode()

        // 使用慢速朗读
        startSpeaking(content, speed = 0.8f)
    }

    private fun setupAccessibilityMode() {
        // 无障碍模式：适中情感、标准音素
        reinitTts(
            noiseScale = 0.7f,    // 适中情感
            lengthScale = 1.05f   // 标准音长
        )
    }
}
```

### 场景 4: 语言学习 App

**需求**：极慢速度，清晰发音

```kotlin
class LearningActivity : AppCompatActivity() {

    fun readWord(word: String) {
        // 设置学习模式
        setupLearningMode()

        // 使用极慢语速
        startSpeaking(word, speed = 0.7f)
    }

    private fun setupLearningMode() {
        // 学习模式：自然情感、拉长音素
        reinitTts(
            noiseScale = 0.8f,    // 自然情感
            lengthScale = 1.2f    // 拉长音素，听清每个音
        )
    }

    // 重复播放功能
    fun repeatWord(word: String, times: Int = 3) {
        var count = 0

        fun playNext() {
            if (count < times) {
                count++
                startSpeaking(word, speed = 0.7f)

                // 等待播放完成后再播放下一次
                Handler(Looper.getMainLooper()).postDelayed({
                    playNext()
                }, 2000)  // 间隔 2 秒
            }
        }

        playNext()
    }
}
```

---

## 📝 注意事项

1. **AAR 文件名**：建议重命名为 `tts.aar`
2. **模型文件路径**：`/data/data/YOUR_PACKAGE/files/models/tts/`（直接在 tts 目录下）
3. **必需文件**：`model.onnx`、`vocos-22khz-univ.onnx`、`lexicon.txt`、`tokens.txt`、`dict/`
4. **可选文件**：`phone.fst`、`date.fst`、`number.fst`
5. **线程要求**：TTS 生成必须在子线程执行
6. **参数更新**：修改 `noiseScale` 或 `lengthScale` 需要重新创建 `OfflineTts` 对象
7. **资源释放**：使用完毕后必须调用 `tts.release()` 和 `audioTrack.release()`

---

## 🔍 故障排查

### 模型文件不存在

**错误**：`--matcha-acoustic-model: '.../model.onnx' does not exist`

**解决**：
```bash
# 检查文件是否存在
adb shell "ls -la /data/data/YOUR_PACKAGE/files/models/tts/"

# 确认应该看到这些文件
# model.onnx
# vocos-22khz-univ.onnx
# lexicon.txt
# tokens.txt
# dict/

# 如果缺失，重新推送
adb push matcha-icefall-zh-baker /data/local/tmp/
adb shell "su -c 'cp -r /data/local/tmp/matcha-icefall-zh-baker/* /data/data/YOUR_PACKAGE/files/models/tts/'"
```

### 缺少 vocoder

**错误**：`Vocoder is not specified. Return an empty wave`

**原因**：Matcha 模型必须指定 vocoder

**解决**：
```kotlin
// 确保配置中包含 vocoder
matcha = OfflineTtsMatchaModelConfig(
    acousticModel = "$modelPath/model.onnx",
    vocoder = "$modelPath/vocos-22khz-univ.onnx",  // 必需！
    // ...
)
```

### 播放无声音

**可能原因**：
1. 设备音量太低
2. AudioTrack 未正确初始化
3. 采样率不匹配

**解决**：
```kotlin
// 检查采样率
val sampleRate = tts.sampleRate()
Log.d("TTS", "Sample rate: $sampleRate")  // 应该是 22050

// 检查 AudioTrack 状态
Log.d("TTS", "AudioTrack state: ${audioTrack.state}")  // 应该是 STATE_INITIALIZED
Log.d("TTS", "AudioTrack playState: ${audioTrack.playState}")  // 播放时应该是 PLAYSTATE_PLAYING

// 设置音量到最大
audioTrack.setVolume(AudioTrack.getMaxVolume())

// 确保调用了 play()
audioTrack.play()
```
