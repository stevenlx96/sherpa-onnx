# SherpaOnnx TTS AAR 功能详解

## 📦 AAR 包概述

本 AAR 包 (`library-release.aar`) 提供了基于 **sherpa-onnx** 引擎的离线 TTS（文本转语音）功能，专为 Android 应用设计。通过集成这个 AAR，你的应用可以实现**高质量的中文语音合成**，无需网络连接，完全在设备本地运行。

### 核心特性

- ✅ **离线运行**：无需联网，保护用户隐私
- ✅ **高质量合成**：基于 Matcha-TTS 模型，自然流畅的中文语音
- ✅ **参数可调**：支持动态调整语速、情感起伏、音长等参数
- ✅ **流式播放**：实时生成并播放音频，无需等待全部合成完成
- ✅ **轻量集成**：单个 AAR 文件，约 20MB，包含所需的 JNI 库
- ✅ **灵活控制**：支持开始、停止、暂停等播放控制

---

## 🎯 AAR 包能做什么？

### 1. 文本转语音合成

**核心功能**：将任意中文文本转换为语音并播放。

**实际应用场景**：
- 📚 **阅读辅助**：为视障用户朗读屏幕内容
- 📱 **通知播报**：语音播报新闻、消息、提醒
- 🎓 **教育应用**：课文朗读、单词发音
- 🚗 **导航播报**：语音导航、路况播报
- 🤖 **智能助手**：虚拟客服、语音交互

**完整代码示例**：

```kotlin
// 基本的文本转语音
class TtsActivity : AppCompatActivity() {
    private lateinit var tts: OfflineTts

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化 TTS
        val modelPath = File(filesDir, "models/tts/matcha-icefall-zh-baker").absolutePath
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
                debug = true,
                provider = "cpu"
            ),
            ruleFsts = "",
            silenceScale = 0.6f
        )

        tts = OfflineTts(assetManager = null, config = config)

        // 使用示例
        speakText("你好，这是一个语音合成示例。")
    }

    private fun speakText(text: String) {
        Thread {
            tts.generateWithCallback(
                text = text,
                sid = 0,
                speed = 1.0f,
                callback = { samples ->
                    // 播放音频（详见"流式音频播放"章节）
                    1
                }
            )
        }.start()
    }
}
```

---

### 2. 实时调节语速

**功能说明**：动态调整朗读速度，范围 0.5x - 2.0x。

**应用场景**：
- 👴 **老年用户**：降低语速，听得更清楚
- 📖 **快速浏览**：提高语速，节省时间
- 🎧 **有声书**：根据用户习惯调整舒适语速

**参数范围**：

| 语速值 | 效果描述 |
|-------|---------|
| 0.5x  | 慢速（适合学习） |
| 0.8x  | 较慢 |
| 1.0x  | 正常速度（默认） |
| 1.2x  | 较快 |
| 2.0x  | 快速（适合浏览） |

**完整代码示例**：

```kotlin
// 布局文件 activity_main.xml
<LinearLayout>
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="语速 (Speed):" />

    <SeekBar
        android:id="@+id/seek_speed"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:max="200"
        android:progress="100" />
</LinearLayout>

// Activity 代码
class TtsActivity : AppCompatActivity() {
    private lateinit var seekSpeed: SeekBar

    private fun speakWithSpeed(text: String) {
        // 将 SeekBar 进度转换为实际语速
        val speed = seekSpeed.progress / 100f  // 100 -> 1.0f

        Thread {
            tts.generateWithCallback(
                text = text,
                sid = 0,
                speed = speed,  // 直接传入语速参数
                callback = this::callback
            )
        }.start()
    }
}
```

**使用示例**：

```kotlin
// 慢速朗读（0.8x）
seekSpeed.progress = 80
speakWithSpeed("这是慢速朗读的文本")

// 快速朗读（1.5x）
seekSpeed.progress = 150
speakWithSpeed("这是快速朗读的文本")
```

---

### 3. 调节情感起伏（Noise Scale）

**功能说明**：控制语音的情感表现力，调整音调变化的随机性。

**应用场景**：
- 📻 **播客节目**：增加情感，更生动
- 📄 **文档朗读**：降低情感，更专业
- 🎭 **有声剧**：根据角色调整情感表现

**参数范围**：

| Noise Scale | 效果描述 |
|-------------|---------|
| 0.0         | 完全平淡，机器感强 |
| 0.6         | 较为平稳 |
| 0.8         | 自然情感（推荐） |
| 1.0         | 情感丰富 |
| 2.0         | 情感夸张 |

**重要提示**：此参数需要在创建 `OfflineTts` 对象时设置，运行时修改需要重新初始化。

**完整代码示例**：

```kotlin
class TtsActivity : AppCompatActivity() {
    private lateinit var tts: OfflineTts
    private lateinit var seekNoise: SeekBar
    private var modelPath: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        modelPath = File(filesDir, "models/tts/matcha-icefall-zh-baker").absolutePath

        // 初始化 TTS
        initTtsWithParameters()
    }

    // 根据当前滑块值重新初始化 TTS
    private fun initTtsWithParameters() {
        // 获取当前情感起伏值
        val noise = seekNoise.progress / 100f  // 80 -> 0.8f

        // 如果已经初始化过，先释放
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
                    noiseScale = noise,  // 设置情感起伏
                    lengthScale = 1.05f
                ),
                numThreads = 4,
                debug = true,
                provider = "cpu"
            ),
            ruleFsts = "",
            silenceScale = 0.6f
        )

        tts = OfflineTts(assetManager = null, config = config)
    }

    // 使用时需要先更新配置
    private fun speakWithEmotion(text: String) {
        // 重新初始化以应用新的情感参数
        initTtsWithParameters()

        // 然后朗读
        Thread {
            tts.generateWithCallback(
                text = text,
                sid = 0,
                speed = 1.0f,
                callback = this::callback
            )
        }.start()
    }
}
```

---

### 4. 调节音长缩放（Length Scale）

**功能说明**：控制音素的持续时间，影响语音的节奏感。

**应用场景**：
- 🎤 **演讲练习**：拉长音素，更清晰
- ⏱️ **时间控制**：缩短音素，快速播报
- 🎵 **节奏调整**：配合背景音乐调整节奏

**参数范围**：

| Length Scale | 效果描述 |
|--------------|---------|
| 0.5          | 音素极短，语速极快 |
| 0.9          | 音素稍短 |
| 1.05         | 标准长度（推荐） |
| 1.2          | 音素拉长 |
| 2.0          | 音素极长，语速极慢 |

**Length Scale vs Speed 的区别**：

- **Speed**：改变整体播放速度，运行时调整，无需重新初始化
- **Length Scale**：改变音素本身的长度，需要重新初始化 TTS 对象

**完整代码示例**：

```kotlin
class TtsActivity : AppCompatActivity() {
    private lateinit var seekLength: SeekBar

    // 同时支持 Noise 和 Length 参数调节
    private fun initTtsWithAllParameters() {
        val noise = seekNoise.progress / 100f   // 默认 80 -> 0.8f
        val length = seekLength.progress / 100f // 默认 105 -> 1.05f

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
                    noiseScale = noise,
                    lengthScale = length  // 设置音长缩放
                ),
                numThreads = 4,
                debug = true,
                provider = "cpu"
            ),
            ruleFsts = "",
            silenceScale = 0.6f
        )

        tts = OfflineTts(assetManager = null, config = config)
    }
}
```

---

### 5. 流式音频播放

**功能说明**：边生成边播放，无需等待全部合成完成，实现低延迟播放。

**技术优势**：
- ⚡ **低延迟**：点击播放后立即开始输出
- 💾 **内存友好**：不需要缓存完整音频数据
- 🔄 **实时控制**：可以随时停止

**工作原理**：

```
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│  输入文本     │ -> │  TTS 生成     │ -> │  AudioTrack  │
│  "你好世界"   │    │  音频片段     │    │  实时播放     │
└──────────────┘    └──────────────┘    └──────────────┘
                          ↑                     ↓
                          └─── callback ────────┘
                          (每次生成一小段音频就回调一次)
```

**完整代码示例**：

```kotlin
class TtsActivity : AppCompatActivity() {
    private lateinit var tts: OfflineTts
    private lateinit var track: AudioTrack
    private var isSpeaking: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 初始化 TTS（省略，参考前面章节）

        // 2. 初始化 AudioTrack
        initAudioTrack()
    }

    private fun initAudioTrack() {
        val sampleRate = tts.sampleRate()  // 获取采样率（通常是 22050 Hz）

        val bufLength = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )

        val attr = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setSampleRate(sampleRate)
            .build()

        track = AudioTrack(
            attr,
            format,
            bufLength,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        track.setVolume(AudioTrack.getMaxVolume())
    }

    // 回调函数：每生成一小段音频就调用一次
    private fun callback(samples: FloatArray): Int {
        if (isSpeaking) {
            // 将音频数据写入 AudioTrack 进行播放
            track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            return 1  // 返回 1 表示继续生成
        }
        return 0  // 返回 0 表示停止生成
    }

    // 开始播放
    private fun speak(text: String, speed: Float = 1.0f) {
        isSpeaking = true
        track.play()

        Thread {
            tts.generateWithCallback(
                text = text,
                sid = 0,
                speed = speed,
                callback = this::callback
            )

            runOnUiThread {
                // 播放完成后的处理
                stopSpeaking()
            }
        }.start()
    }

    // 停止播放
    private fun stopSpeaking() {
        isSpeaking = false
        track.pause()
        track.flush()
    }
}
```

---

### 6. 播放控制

**功能说明**：完整的播放控制功能，包括开始、停止、状态管理。

**完整代码示例**：

```kotlin
class TtsActivity : AppCompatActivity() {
    private lateinit var btnSpeak: Button
    private lateinit var btnStop: Button
    private lateinit var textInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnSpeak = findViewById(R.id.btn_speak)
        btnStop = findViewById(R.id.btn_stop)
        textInput = findViewById(R.id.text_input)

        // 初始状态
        btnSpeak.isEnabled = true
        btnStop.isEnabled = false

        btnSpeak.setOnClickListener { onClickSpeak() }
        btnStop.setOnClickListener { onClickStop() }
    }

    private fun onClickSpeak() {
        val text = textInput.text.toString().trim()

        // 1. 检查输入
        if (text.isBlank()) {
            Toast.makeText(this, "请输入要朗读的文字", Toast.LENGTH_SHORT).show()
            return
        }

        // 2. 更新按钮状态
        isSpeaking = true
        btnSpeak.isEnabled = false
        btnStop.isEnabled = true

        // 3. 清空缓冲并开始播放
        track.pause()
        track.flush()
        track.play()

        // 4. 异步生成音频
        Thread {
            try {
                tts.generateWithCallback(
                    text = text,
                    sid = 0,
                    speed = seekSpeed.progress / 100f,
                    callback = this::callback
                )

                runOnUiThread {
                    onClickStop()
                    Toast.makeText(this, "朗读完成", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("TTS", "朗读出错", e)
                runOnUiThread {
                    onClickStop()
                    Toast.makeText(this, "朗读失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun onClickStop() {
        isSpeaking = false
        btnSpeak.isEnabled = true
        btnStop.isEnabled = false
        track.pause()
        track.flush()
    }
}
```

**按钮状态管理**：

| 状态     | 开始按钮 | 停止按钮 |
|---------|---------|---------|
| 未播放   | ✅ 启用  | ❌ 禁用  |
| 播放中   | ❌ 禁用  | ✅ 启用  |

---

### 7. 自动规则处理

**功能说明**：自动识别和处理电话号码、日期、数字等特殊文本格式。

**支持的规则**（需要模型文件包含对应的 `.fst` 文件）：

- 📞 **电话号码**：`phone.fst` - "138-0013-8000" → "一三八零零一三八零零零"
- 📅 **日期格式**：`date.fst` - "2025-01-15" → "二零二五年一月十五日"
- 🔢 **数字朗读**：`number.fst` - "12345" → "一万两千三百四十五"

**完整代码示例**：

```kotlin
private fun initTtsWithRules() {
    val modelPath = File(filesDir, "models/tts/matcha-icefall-zh-baker").absolutePath

    // 检查并添加规则文件
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
            debug = true,
            provider = "cpu"
        ),
        ruleFsts = ruleFsts,  // 添加规则文件
        silenceScale = 0.6f
    )

    tts = OfflineTts(assetManager = null, config = config)
}
```

**效果示例**：

| 输入文本 | 实际朗读 |
|---------|---------|
| "我的电话是138-0013-8000" | "我的电话是一三八零零一三八零零零" |
| "今天是2025年1月15日" | "今天是二零二五年一月十五日" |
| "金额是12345元" | "金额是一万两千三百四十五元" |

---

### 8. 错误处理

**功能说明**：完善的错误处理机制，帮助用户快速定位问题。

**完整代码示例**：

```kotlin
class TtsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // 初始化 TTS
            initTts()
            initAudioTrack()

            Toast.makeText(this, "TTS 初始化成功", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e("TTS", "初始化失败", e)

            val modelPath = File(filesDir, "models/tts/matcha-icefall-zh-baker").absolutePath

            val errorMsg = """
                TTS 初始化失败！

                错误信息: ${e.message}

                请确保模型文件已放置在:
                $modelPath/

                需要的文件:
                - model.onnx
                - vocos-22khz-univ.onnx
                - lexicon.txt
                - tokens.txt
                - dict/ (目录)
                - phone.fst, date.fst, number.fst (可选)
            """.trimIndent()

            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
        }
    }

    // 朗读时的错误处理
    private fun speakWithErrorHandling(text: String) {
        Thread {
            try {
                tts.generateWithCallback(
                    text = text,
                    sid = 0,
                    speed = 1.0f,
                    callback = this::callback
                )
            } catch (e: Exception) {
                Log.e("TTS", "朗读出错", e)
                runOnUiThread {
                    Toast.makeText(this, "朗读失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}
```

**常见错误提示**：

1. **模型文件不存在**：
   ```
   TTS 初始化失败！
   错误信息: --matcha-acoustic-model: '.../model.onnx' does not exist
   ```

2. **缺少 vocoder**：
   ```
   Vocoder is not specified. Return an empty wave
   ```

3. **权限问题**：
   ```
   Permission denied: .../model.onnx
   ```

---

### 9. 资源管理

**功能说明**：正确管理 TTS 对象和 AudioTrack 资源，避免内存泄漏。

**完整代码示例**：

```kotlin
class TtsActivity : AppCompatActivity() {
    private lateinit var tts: OfflineTts
    private lateinit var track: AudioTrack

    // 重新初始化时的资源管理
    private fun reinitTts() {
        // 先释放旧对象
        if (::tts.isInitialized) {
            tts.release()
        }

        // 创建新对象
        val config = OfflineTtsConfig(/* ... */)
        tts = OfflineTts(assetManager = null, config = config)
    }

    // Activity 销毁时释放资源
    override fun onDestroy() {
        super.onDestroy()

        if (::track.isInitialized) {
            track.stop()
            track.release()
        }

        if (::tts.isInitialized) {
            tts.release()
        }
    }
}
```

---

## 🎨 完整的 UI 示例

### 推荐的界面布局

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:padding="16dp">

    <!-- 文本输入框 -->
    <EditText
        android:id="@+id/text_input"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:hint="请输入要朗读的文字"
        android:minHeight="100dp"
        android:gravity="top|start"
        android:inputType="textMultiLine"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

    <!-- 参数调节区 -->
    <LinearLayout
        android:id="@+id/slider_group"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginTop="16dp"
        android:orientation="vertical"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@+id/text_input">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="语速 (Speed):" />
        <SeekBar
            android:id="@+id/seek_speed"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:max="200"
            android:progress="100" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="情感起伏 (Noise Scale):" />
        <SeekBar
            android:id="@+id/seek_noise"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:max="200"
            android:progress="80" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="音长缩放 (Length Scale):" />
        <SeekBar
            android:id="@+id/seek_length"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:max="200"
            android:progress="105" />
    </LinearLayout>

    <!-- 控制按钮 -->
    <Button
        android:id="@+id/btn_speak"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginTop="24dp"
        android:text="开始合成"
        app:layout_constraintEnd_toStartOf="@+id/btn_stop"
        app:layout_constraintHorizontal_weight="1"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@+id/slider_group" />

    <Button
        android:id="@+id/btn_stop"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="16dp"
        android:enabled="false"
        android:text="停止"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintHorizontal_weight="1"
        app:layout_constraintStart_toEndOf="@+id/btn_speak"
        app:layout_constraintTop_toTopOf="@+id/btn_speak" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

### 界面效果

```
┌─────────────────────────────────────────────┐
│  文本输入框                                   │
│  ┌─────────────────────────────────────────┐│
│  │ 请输入要朗读的文字                        ││
│  │                                          ││
│  │ 你好，这是一个可以调节参数的语音合成示例。 ││
│  └─────────────────────────────────────────┘│
│                                             │
│  参数调节区                                  │
│  ┌─────────────────────────────────────────┐│
│  │ 语速 (Speed):                            ││
│  │ ├────────●──────────┤  [1.0x]           ││
│  │                                          ││
│  │ 情感起伏 (Noise Scale):                  ││
│  │ ├────────●──────────┤  [0.8]            ││
│  │                                          ││
│  │ 音长缩放 (Length Scale):                 ││
│  │ ├──────────●────────┤  [1.05]           ││
│  └─────────────────────────────────────────┘│
│                                             │
│  控制按钮                                    │
│  ┌──────────────┐  ┌──────────────┐        │
│  │  开始合成    │  │    停止      │        │
│  └──────────────┘  └──────────────┘        │
└─────────────────────────────────────────────┘
```

---

## 🔧 完整的 Activity 示例代码

将以上所有功能整合到一个完整的 Activity：

```kotlin
package com.example.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.k2fsa.sherpa.onnx.*
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var tts: OfflineTts
    private lateinit var textInput: EditText
    private lateinit var speakButton: Button
    private lateinit var stopButton: Button
    private lateinit var seekSpeed: SeekBar
    private lateinit var seekNoise: SeekBar
    private lateinit var seekLength: SeekBar

    private var isSpeaking: Boolean = false
    private lateinit var track: AudioTrack
    private var modelPath: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化 UI 控件
        textInput = findViewById(R.id.text_input)
        speakButton = findViewById(R.id.btn_speak)
        stopButton = findViewById(R.id.btn_stop)
        seekSpeed = findViewById(R.id.seek_speed)
        seekNoise = findViewById(R.id.seek_noise)
        seekLength = findViewById(R.id.seek_length)

        // 设置默认文本
        textInput.setText("你好，这是一个可以调节参数的语音合成示例。")

        // 设置模型路径
        modelPath = File(filesDir, "models/tts/matcha-icefall-zh-baker").absolutePath

        // 设置按钮点击事件
        speakButton.setOnClickListener { onClickSpeak() }
        stopButton.setOnClickListener { onClickStop() }

        // 初始化 TTS
        try {
            initTts()
            initAudioTrack()
            Toast.makeText(this, "TTS 初始化成功", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("TTS", "初始化失败", e)
            showInitError(e)
        }
    }

    private fun initTts() {
        // 获取当前参数
        val noise = seekNoise.progress / 100f
        val length = seekLength.progress / 100f

        // 检查规则文件
        val ruleFsts = listOf(
            "$modelPath/phone.fst",
            "$modelPath/date.fst",
            "$modelPath/number.fst"
        ).filter { File(it).exists() }.joinToString(",")

        // 如果已初始化，先释放
        if (::tts.isInitialized) {
            tts.release()
        }

        // 创建配置
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                matcha = OfflineTtsMatchaModelConfig(
                    acousticModel = "$modelPath/model.onnx",
                    vocoder = "$modelPath/vocos-22khz-univ.onnx",
                    lexicon = "$modelPath/lexicon.txt",
                    tokens = "$modelPath/tokens.txt",
                    dataDir = modelPath,
                    noiseScale = noise,
                    lengthScale = length
                ),
                numThreads = 4,
                debug = true,
                provider = "cpu"
            ),
            ruleFsts = ruleFsts,
            silenceScale = 0.6f
        )

        tts = OfflineTts(assetManager = null, config = config)
        Log.i("TTS", "TTS 初始化成功 - Noise: $noise, Length: $length")
    }

    private fun initAudioTrack() {
        val sampleRate = tts.sampleRate()
        val bufLength = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )

        val attr = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setSampleRate(sampleRate)
            .build()

        track = AudioTrack(
            attr,
            format,
            bufLength,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        track.setVolume(AudioTrack.getMaxVolume())
    }

    private fun callback(samples: FloatArray): Int {
        if (isSpeaking) {
            track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            return 1
        }
        return 0
    }

    private fun onClickSpeak() {
        val text = textInput.text.toString().trim()

        if (text.isBlank()) {
            Toast.makeText(this, "请输入要朗读的文字", Toast.LENGTH_SHORT).show()
            return
        }

        // 重新初始化以应用新参数
        try {
            initTts()
        } catch (e: Exception) {
            Log.e("TTS", "更新配置失败", e)
            Toast.makeText(this, "更新配置失败: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        val speed = seekSpeed.progress / 100f

        isSpeaking = true
        speakButton.isEnabled = false
        stopButton.isEnabled = true

        track.pause()
        track.flush()
        track.play()

        Thread {
            try {
                Log.i("TTS", "开始朗读: $text, 语速: $speed")

                tts.generateWithCallback(
                    text = text,
                    sid = 0,
                    speed = speed,
                    callback = this::callback
                )

                runOnUiThread {
                    onClickStop()
                    Toast.makeText(this, "朗读完成", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("TTS", "朗读出错", e)
                runOnUiThread {
                    onClickStop()
                    Toast.makeText(this, "朗读失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun onClickStop() {
        isSpeaking = false
        speakButton.isEnabled = true
        stopButton.isEnabled = false
        track.pause()
        track.flush()
    }

    private fun showInitError(e: Exception) {
        val errorMsg = """
            TTS 初始化失败！

            错误信息: ${e.message}

            请确保模型文件已放置在:
            $modelPath/

            需要的文件:
            - model.onnx
            - vocos-22khz-univ.onnx
            - lexicon.txt
            - tokens.txt
            - dict/ (目录)
            - phone.fst, date.fst, number.fst (可选)
        """.trimIndent()

        Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::track.isInitialized) track.release()
        if (::tts.isInitialized) tts.release()
    }
}
```

---

## 📋 集成所需的依赖

```kotlin
// app/build.gradle.kts
dependencies {
    // 引入 AAR 文件
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

---

## 💡 实际应用场景示例

### 场景 1: 新闻播报 App

```kotlin
// 快速浏览模式
fun setNewsReadingMode() {
    seekSpeed.progress = 150  // 1.5x 语速
    seekNoise.progress = 60   // 降低情感，更平稳
    seekLength.progress = 90  // 缩短音长

    speak("今日头条：人工智能技术取得重大突破...")
}
```

### 场景 2: 儿童故事 App

```kotlin
// 生动讲故事模式
fun setStoryMode() {
    seekSpeed.progress = 90   // 0.9x 稍慢，更清晰
    seekNoise.progress = 120  // 增强情感，更生动
    seekLength.progress = 110 // 拉长音长，更有节奏感

    speak("从前，有一个小兔子...")
}
```

### 场景 3: 视障辅助 App

```kotlin
// 清晰朗读模式
fun setAccessibilityMode() {
    seekSpeed.progress = 80   // 0.8x 慢速
    seekNoise.progress = 70   // 适中情感
    seekLength.progress = 105 // 标准音长

    speak("当前屏幕显示：设置菜单...")
}
```

### 场景 4: 学习 App

```kotlin
// 单词发音模式
fun setLearningMode() {
    seekSpeed.progress = 70   // 0.7x 很慢，便于学习
    seekNoise.progress = 80   // 自然情感
    seekLength.progress = 120 // 拉长音素，听清每个音

    speak("Hello - 你好")
}
```

---

## 📊 性能参数

| 参数 | 值 | 说明 |
|------|-----|------|
| **采样率** | 22050 Hz | `tts.sampleRate()` |
| **线程数** | 4 | 推荐值，加快生成速度 |
| **音频格式** | PCM_FLOAT | 32位浮点音频 |
| **声道** | MONO | 单声道 |
| **AAR 大小** | ~20 MB | 包含 arm64-v8a 和 armeabi-v7a |
| **模型大小** | ~50 MB | matcha-icefall-zh-baker 模型 |

---

## ⚠️ 重要注意事项

### 1. 模型文件位置

**必须**将模型文件推送到应用的私有目录：

```bash
# 推送模型文件
adb push matcha-icefall-zh-baker /data/local/tmp/
adb shell "su -c 'cp -r /data/local/tmp/matcha-icefall-zh-baker /data/data/YOUR_PACKAGE/files/models/tts/ && chmod -R 755 /data/data/YOUR_PACKAGE/files/models/tts/'"
```

### 2. 参数更新机制

- **Speed（语速）**：运行时直接传入 `generateWithCallback(speed = xxx)`
- **NoiseScale（情感）**：需要重新创建 `OfflineTts` 对象
- **LengthScale（音长）**：需要重新创建 `OfflineTts` 对象

**解决方案**：每次朗读前调用 `initTts()` 重新初始化。

### 3. 线程安全

TTS 生成是耗时操作，**必须**在子线程执行：

```kotlin
Thread {
    tts.generateWithCallback(...)
}.start()
```

### 4. 内存管理

**必须**在 `onDestroy()` 中释放资源：

```kotlin
override fun onDestroy() {
    super.onDestroy()
    if (::track.isInitialized) track.release()
    if (::tts.isInitialized) tts.release()
}
```

---

## 🚀 快速开始

### 1. 集成 AAR

```bash
# 复制 AAR 到项目
cp library-release.aar YourProject/app/libs/
```

### 2. 配置依赖

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(files("libs/library-release.aar"))
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // ... 其他依赖
}
```

### 3. 推送模型文件

```bash
# 解压模型
tar -xjf matcha-icefall-zh-baker.tar.bz2

# 推送到设备
adb push matcha-icefall-zh-baker /data/local/tmp/
adb shell "su -c 'cp -r /data/local/tmp/matcha-icefall-zh-baker /data/data/YOUR_PACKAGE/files/models/tts/ && chmod -R 755 /data/data/YOUR_PACKAGE/files/models/tts/'"
```

### 4. 复制代码

参考上面的"完整的 Activity 示例代码"章节，复制并修改即可使用。

---

## 📖 功能总结

| 功能 | 说明 | 代码位置 |
|------|------|---------|
| ✅ 文本转语音 | 将中文文本转换为语音 | `tts.generateWithCallback()` |
| ✅ 实时调节语速 | 0.5x - 2.0x 动态调整 | `speed` 参数 |
| ✅ 调节情感起伏 | 0.0 - 2.0 情感表现力 | `noiseScale` 配置 |
| ✅ 调节音长缩放 | 0.5 - 2.0 音素长度 | `lengthScale` 配置 |
| ✅ 流式播放 | 边生成边播放，低延迟 | `callback` 函数 |
| ✅ 播放控制 | 开始/停止/状态管理 | `onClickSpeak()` / `onClickStop()` |
| ✅ 规则处理 | 电话号码、日期、数字 | `ruleFsts` 配置 |
| ✅ 错误处理 | 完善的异常捕获和提示 | `try-catch` + `Toast` |
| ✅ 资源管理 | 避免内存泄漏 | `onDestroy()` |

通过集成这个 AAR 包，你可以快速为自己的 Android 应用添加高质量的中文语音合成功能！
