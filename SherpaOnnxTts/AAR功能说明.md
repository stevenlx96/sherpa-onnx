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

**实际场景**：
- 📚 **阅读辅助**：为视障用户朗读屏幕内容
- 📱 **通知播报**：语音播报新闻、消息、提醒
- 🎓 **教育应用**：课文朗读、单词发音
- 🚗 **导航播报**：语音导航、路况播报
- 🤖 **智能助手**：虚拟客服、语音交互

**Demo 中的实现**：

```kotlin
// MainActivity.kt 第 182-187 行
tts.generateWithCallback(
    text = "你好，这是一个可以调节参数的语音合成示例。",
    sid = 0,
    speed = 1.0f,
    callback = this::callback
)
```

**UI 界面**：

```
┌─────────────────────────────────────┐
│ 请输入要朗读的文字                    │
│ ┌─────────────────────────────────┐ │
│ │ 你好，这是一个可以调节参数的语音 │ │
│ │ 合成示例。                       │ │
│ └─────────────────────────────────┘ │
│                                     │
│ 【开始合成】    【停止】             │
└─────────────────────────────────────┘
```

### 2. 实时调节语速

**功能说明**：通过滑动条动态调整朗读速度，范围 0.5x - 2.0x。

**使用场景**：
- 👴 **老年用户**：降低语速，听得更清楚
- 📖 **快速浏览**：提高语速，节省时间
- 🎧 **有声书**：根据用户习惯调整舒适语速

**Demo 中的实现**：

```kotlin
// MainActivity.kt 第 168 行
val currentSpeed = seekSpeed.progress / 100f // 默认 100 -> 1.0f

// activity_main.xml 第 31 行
<SeekBar
    android:id="@+id/seek_speed"
    android:max="200"
    android:progress="100" />
```

**参数范围**：

| SeekBar 值 | 实际语速 | 效果描述 |
|-----------|---------|---------|
| 50        | 0.5x    | 慢速（适合学习） |
| 80        | 0.8x    | 较慢 |
| 100       | 1.0x    | 正常速度（默认） |
| 120       | 1.2x    | 较快 |
| 200       | 2.0x    | 快速（适合浏览） |

**界面展示**：

```
语速 (Speed):
├────────●───────┤  [进度: 100 = 1.0x]
0.5x           2.0x
```

### 3. 调节情感起伏（Noise Scale）

**功能说明**：控制语音的情感表现力，调整音调变化的随机性。

**使用场景**：
- 📻 **播客节目**：增加情感，更生动
- 📄 **文档朗读**：降低情感，更专业
- 🎭 **有声剧**：根据角色调整情感表现

**Demo 中的实现**：

```kotlin
// MainActivity.kt 第 108 行
val noise = seekNoise.progress / 100f  // 默认 80 -> 0.8f

// MainActivity.kt 第 130 行 - 在初始化时设置
noiseScale = noise
```

**注意**：此参数需要重新初始化 TTS 对象才能生效（Demo 在每次点击"开始合成"时自动重新初始化）。

**参数范围**：

| SeekBar 值 | Noise Scale | 效果描述 |
|-----------|-------------|---------|
| 0         | 0.0         | 完全平淡，机器感强 |
| 60        | 0.6         | 较为平稳 |
| 80        | 0.8         | 自然情感（默认） |
| 100       | 1.0         | 情感丰富 |
| 200       | 2.0         | 情感夸张 |

**界面展示**：

```
情感起伏 (Noise Scale):
├────────●───────┤  [进度: 80 = 0.8]
0.0            2.0
```

### 4. 调节音长缩放（Length Scale）

**功能说明**：控制音素的持续时间，影响语音的节奏感。

**使用场景**：
- 🎤 **演讲练习**：拉长音素，更清晰
- ⏱️ **时间控制**：缩短音素，快速播报
- 🎵 **节奏调整**：配合背景音乐调整节奏

**Demo 中的实现**：

```kotlin
// MainActivity.kt 第 109 行
val length = seekLength.progress / 100f // 默认 105 -> 1.05f

// MainActivity.kt 第 131 行 - 在初始化时设置
lengthScale = length
```

**注意**：此参数也需要重新初始化 TTS 对象才能生效。

**参数范围**：

| SeekBar 值 | Length Scale | 效果描述 |
|-----------|--------------|---------|
| 50        | 0.5          | 音素极短，语速极快 |
| 90        | 0.9          | 音素稍短 |
| 105       | 1.05         | 标准长度（默认） |
| 120       | 1.2          | 音素拉长 |
| 200       | 2.0          | 音素极长，语速极慢 |

**Length Scale vs Speed 的区别**：

- **Speed**：改变整体播放速度，运行时调整，无需重新初始化
- **Length Scale**：改变音素本身的长度，需要重新初始化

**界面展示**：

```
音长缩放 (Length Scale):
├──────────●─────┤  [进度: 105 = 1.05]
0.5            2.0
```

### 5. 流式音频播放

**功能说明**：边生成边播放，无需等待全部合成完成，实现低延迟播放。

**技术优势**：
- ⚡ **低延迟**：点击"开始"后立即开始播放
- 💾 **内存友好**：不需要缓存完整音频数据
- 🔄 **实时反馈**：可以随时停止

**Demo 中的实现**：

```kotlin
// MainActivity.kt 第 144-150 行 - 回调函数
private fun callback(samples: FloatArray): Int {
    if (isSpeaking) {
        track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        return 1  // 继续生成
    }
    return 0  // 停止生成
}
```

**工作流程**：

```
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│  输入文本     │ -> │  TTS 生成     │ -> │  AudioTrack  │
│  "你好世界"   │    │  音频片段     │    │  实时播放     │
└──────────────┘    └──────────────┘    └──────────────┘
                          ↑                     ↓
                          └─── callback ────────┘
                          (每次生成一小段音频就回调一次)
```

### 6. 播放控制

**功能说明**：完整的播放控制功能，包括开始、停止、暂停等。

**Demo 中的实现**：

**开始播放**：
```kotlin
// MainActivity.kt 第 152-201 行
private fun onClickSpeak() {
    // 1. 检查输入
    if (textStr.isBlank()) {
        Toast.makeText(this, "请输入要朗读的文字", Toast.LENGTH_SHORT).show()
        return
    }

    // 2. 更新配置（根据当前滑块值）
    updateConfigAndRestartTts()

    // 3. 开始播放
    isSpeaking = true
    track.play()

    // 4. 异步生成音频
    Thread {
        tts.generateWithCallback(...)
    }.start()
}
```

**停止播放**：
```kotlin
// MainActivity.kt 第 203-209 行
private fun onClickStop() {
    isSpeaking = false  // 标记停止
    track.pause()       // 暂停播放
    track.flush()       // 清空缓冲区
}
```

**按钮状态管理**：

| 状态     | 开始按钮 | 停止按钮 |
|---------|---------|---------|
| 未播放   | ✅ 启用  | ❌ 禁用  |
| 播放中   | ❌ 禁用  | ✅ 启用  |

### 7. 自动规则处理

**功能说明**：自动识别和处理电话号码、日期、数字等特殊文本格式。

**支持的规则**（需要模型文件包含对应的 `.fst` 文件）：

- 📞 **电话号码**：`phone.fst` - "138-0013-8000" → "一三八零零一三八零零零"
- 📅 **日期格式**：`date.fst` - "2025-01-15" → "二零二五年一月十五日"
- 🔢 **数字朗读**：`number.fst` - "12345" → "一万两千三百四十五"

**Demo 中的实现**：

```kotlin
// MainActivity.kt 第 111-115 行
val ruleFsts = listOf(
    "$modelPathString/phone.fst",
    "$modelPathString/date.fst",
    "$modelPathString/number.fst"
).filter { File(it).exists() }.joinToString(",")

// MainActivity.kt 第 137 行
ruleFsts = ruleFsts
```

**示例效果**：

| 输入文本 | 实际朗读 |
|---------|---------|
| "我的电话是138-0013-8000" | "我的电话是一三八零零一三八零零零" |
| "今天是2025年1月15日" | "今天是二零二五年一月十五日" |
| "金额是12345元" | "金额是一万两千三百四十五元" |

### 8. 错误处理与用户提示

**功能说明**：完善的错误处理机制，帮助用户快速定位问题。

**Demo 中的实现**：

```kotlin
// MainActivity.kt 第 56-80 行 - 初始化错误处理
try {
    updateConfigAndRestartTts()
    initAudioTrack()
    Toast.makeText(this, "TTS 初始化成功", Toast.LENGTH_SHORT).show()
} catch (e: Exception) {
    val errorMsg = """
        TTS 初始化失败！

        错误信息: ${e.message}

        请确保模型文件已放置在:
        $modelPathString/

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
```

**常见错误提示**：

1. **模型文件不存在**：
   ```
   TTS 初始化失败！
   错误信息: --matcha-acoustic-model: '/data/user/0/.../model.onnx' does not exist
   ```

2. **朗读出错**：
   ```
   朗读失败: [具体错误信息]
   ```

3. **配置更新失败**：
   ```
   更新配置失败: [具体错误信息]
   ```

### 9. 资源管理

**功能说明**：正确管理 TTS 对象和 AudioTrack 资源，避免内存泄漏。

**Demo 中的实现**：

```kotlin
// MainActivity.kt 第 211-215 行
override fun onDestroy() {
    super.onDestroy()
    if (::track.isInitialized) track.release()  // 释放 AudioTrack
    if (::tts.isInitialized) tts.release()      // 释放 TTS 对象
}
```

**重新初始化机制**：

```kotlin
// MainActivity.kt 第 117-120 行
if (::tts.isInitialized) {
    tts.release()  // 先释放旧对象
}
// 然后创建新对象
tts = OfflineTts(assetManager = null, config = config)
```

---

## 📋 集成所需的依赖

根据 Demo 项目的 `build.gradle.kts` (第 40-56 行)，集成此 AAR 需要以下依赖：

```kotlin
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

## 🎨 完整的 UI 界面

根据 Demo 项目的 `activity_main.xml`，完整的 UI 界面包含：

### 布局结构

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

### 组件列表

| 组件 ID | 类型 | 功能 |
|--------|------|------|
| `text_input` | EditText | 多行文本输入框 |
| `seek_speed` | SeekBar | 语速调节（0-200，默认100） |
| `seek_noise` | SeekBar | 情感调节（0-200，默认80） |
| `seek_length` | SeekBar | 音长调节（0-200，默认105） |
| `btn_speak` | Button | 开始合成按钮 |
| `btn_stop` | Button | 停止按钮 |

---

## 🔧 核心 API 使用示例

### 完整的使用流程（基于 Demo 项目）

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var tts: OfflineTts
    private lateinit var track: AudioTrack
    private var isSpeaking: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 配置模型路径
        val modelPath = File(filesDir, "models/tts/matcha-icefall-zh-baker").absolutePath

        // 2. 创建配置
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

        // 3. 初始化 TTS
        tts = OfflineTts(assetManager = null, config = config)

        // 4. 初始化 AudioTrack
        val sampleRate = tts.sampleRate()
        // ... (参考 MainActivity.kt 第 83-104 行)
    }

    // 5. 开始朗读
    fun speak(text: String, speed: Float = 1.0f) {
        isSpeaking = true
        track.play()

        Thread {
            tts.generateWithCallback(
                text = text,
                sid = 0,
                speed = speed,
                callback = { samples ->
                    if (isSpeaking) {
                        track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                        1  // 继续
                    } else {
                        0  // 停止
                    }
                }
            )
        }.start()
    }

    // 6. 停止朗读
    fun stop() {
        isSpeaking = false
        track.pause()
        track.flush()
    }

    // 7. 释放资源
    override fun onDestroy() {
        super.onDestroy()
        track.release()
        tts.release()
    }
}
```

---

## 💡 实际应用场景示例

### 场景 1: 新闻播报 App

```kotlin
// 快速浏览模式
seekSpeed.progress = 150  // 1.5x 语速
seekNoise.progress = 60   // 降低情感，更平稳
seekLength.progress = 90  // 缩短音长

speak("今日头条：人工智能技术取得重大突破...")
```

### 场景 2: 儿童故事 App

```kotlin
// 生动讲故事模式
seekSpeed.progress = 90   // 0.9x 稍慢，更清晰
seekNoise.progress = 120  // 增强情感，更生动
seekLength.progress = 110 // 拉长音长，更有节奏感

speak("从前，有一个小兔子...")
```

### 场景 3: 视障辅助 App

```kotlin
// 清晰朗读模式
seekSpeed.progress = 80   // 0.8x 慢速
seekNoise.progress = 70   // 适中情感
seekLength.progress = 105 // 标准音长

speak("当前屏幕显示：设置菜单...")
```

### 场景 4: 学习 App

```kotlin
// 单词发音模式
seekSpeed.progress = 70   // 0.7x 很慢，便于学习
seekNoise.progress = 80   // 自然情感
seekLength.progress = 120 // 拉长音素，听清每个音

speak("Hello - 你好")
```

---

## 📊 性能参数

根据 Demo 项目配置：

| 参数 | 值 | 说明 |
|------|-----|------|
| **采样率** | 22050 Hz | `tts.sampleRate()` |
| **线程数** | 4 | 加快生成速度 |
| **音频格式** | PCM_FLOAT | 32位浮点音频 |
| **声道** | MONO | 单声道 |
| **AAR 大小** | ~20 MB | 包含 arm64-v8a 和 armeabi-v7a |
| **模型大小** | ~50 MB | matcha-icefall-zh-baker 模型 |

---

## ⚠️ 注意事项

### 1. 模型文件位置

**必须**将模型文件推送到应用的私有目录：

```bash
# Demo 应用的模型路径
/data/data/com.example.demo.tts/files/models/tts/matcha-icefall-zh-baker/
```

### 2. 参数更新机制

- **Speed（语速）**：可以运行时直接传入 `generateWithCallback(speed = xxx)`
- **NoiseScale（情感）**：需要重新创建 `OfflineTts` 对象
- **LengthScale（音长）**：需要重新创建 `OfflineTts` 对象

Demo 的解决方案：每次点击"开始合成"时，调用 `updateConfigAndRestartTts()` 重新初始化。

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

### 4. 参考 Demo 代码

完整代码参考：
- `demo/app/src/main/java/com/example/demo/tts/MainActivity.kt`
- `demo/app/src/main/res/layout/activity_main.xml`

---

## 📖 总结

这个 AAR 包通过 Demo 项目展示了以下核心能力：

| 功能 | Demo 中的体现 |
|------|--------------|
| ✅ 文本转语音 | 输入框 + 开始按钮 |
| ✅ 实时调节语速 | Speed SeekBar (0.5x - 2.0x) |
| ✅ 调节情感起伏 | Noise SeekBar (0.0 - 2.0) |
| ✅ 调节音长缩放 | Length SeekBar (0.5 - 2.0) |
| ✅ 流式播放 | callback 函数实时写入 AudioTrack |
| ✅ 播放控制 | 开始/停止按钮 + 状态管理 |
| ✅ 错误处理 | try-catch + Toast 提示 |
| ✅ 资源管理 | onDestroy 释放资源 |

通过集成这个 AAR 包，你可以快速为自己的 Android 应用添加高质量的中文语音合成功能！
