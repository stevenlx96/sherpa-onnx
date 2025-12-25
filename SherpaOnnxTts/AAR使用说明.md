# SherpaOnnx TTS AAR 使用说明

## 目录

1. [项目概述](#项目概述)
2. [环境要求](#环境要求)
3. [AAR 构建流程](#aar-构建流程)
4. [AAR 集成到新项目](#aar-集成到新项目)
5. [模型文件准备](#模型文件准备)
6. [API 使用指南](#api-使用指南)
7. [参数调节说明](#参数调节说明)
8. [故障排查](#故障排查)

---

## 项目概述

本项目提供了基于 sherpa-onnx 的 TTS（文字转语音）功能的 Android AAR 库，支持：

- **Matcha-TTS 模型**：高质量中文语音合成
- **流式音频回调**：实时播放合成音频
- **参数可调**：支持动态调整语速、情感起伏、音长缩放
- **独立打包**：可作为 AAR 库集成到任何 Android 项目

### 项目结构

```
SherpaOnnxTts/
├── app/                          # 主应用模块（完整功能演示）
├── library/                      # AAR 库模块（可打包发布）
│   ├── src/main/
│   │   ├── java/com/k2fsa/sherpa/onnx/
│   │   │   ├── Tts.kt           # sherpa-onnx Kotlin API
│   │   │   └── tts/
│   │   │       └── TtsManager.kt # TTS 管理封装类
│   │   └── jniLibs/             # JNI 本地库
│   │       ├── arm64-v8a/
│   │       └── armeabi-v7a/
│   └── build.gradle.kts         # 库模块配置
├── demo/                         # 独立 Demo 项目（使用 AAR）
│   ├── app/
│   │   ├── libs/
│   │   │   └── library-release.aar  # 复制到这里的 AAR
│   │   └── build.gradle.kts
│   └── settings.gradle.kts
├── download-libs.sh              # 下载 JNI 库脚本
├── build-library-aar.sh          # 构建 AAR 脚本
└── copy-aar-to-demo.sh           # 复制 AAR 到 Demo 脚本
```

---

## 环境要求

### 开发环境

- **JDK**: 17 或更高版本
- **Gradle**: 8.13
- **Android Gradle Plugin (AGP)**: 8.4.0
- **Kotlin**: 1.9.23

### Android 要求

- **minSdk**: 21 (Android 5.0)
- **targetSdk**: 34 (Android 14)
- **compileSdk**: 34

### 依赖库

AAR 需要以下外部依赖（使用时必须添加）：

```kotlin
implementation("androidx.core:core-ktx:1.12.0")
implementation("androidx.appcompat:appcompat:1.6.1")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
```

---

## AAR 构建流程

### 步骤 1: 下载 JNI 本地库

```bash
cd SherpaOnnxTts
./download-libs.sh
```

此脚本会：
- 从 GitHub 下载 sherpa-onnx v1.12.18 的 AAR 包
- 解压并提取 `libsherpa-onnx-jni.so` 文件
- 将 `.so` 文件复制到 `library/src/main/jniLibs/` 目录

**验证结果**：

```bash
ls library/src/main/jniLibs/arm64-v8a/
# 应输出: libsherpa-onnx-jni.so

ls library/src/main/jniLibs/armeabi-v7a/
# 应输出: libsherpa-onnx-jni.so
```

### 步骤 2: 构建 AAR 包

```bash
./build-library-aar.sh
```

构建成功后，AAR 文件位置：

```
library/build/outputs/aar/library-release.aar
```

**AAR 文件大小**：约 19-20 MB（包含 arm64-v8a 和 armeabi-v7a 两个架构的 .so 文件）

### 步骤 3: 复制 AAR 到 Demo 项目（可选）

```bash
./copy-aar-to-demo.sh
```

此脚本会将 AAR 复制到 `demo/app/libs/library-release.aar`，供 Demo 项目使用。

---

## AAR 集成到新项目

### 方法 1: 本地 AAR 文件集成

#### 1. 复制 AAR 文件

将 `library-release.aar` 复制到你的项目的 `app/libs/` 目录：

```bash
mkdir -p YourProject/app/libs
cp library/build/outputs/aar/library-release.aar YourProject/app/libs/
```

#### 2. 修改 build.gradle.kts

在你的 `app/build.gradle.kts` 中添加：

```kotlin
dependencies {
    // 引入本地 AAR 文件
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

### 方法 2: Maven 本地仓库集成

#### 1. 发布 AAR 到本地 Maven

```bash
cd SherpaOnnxTts
./gradlew :library:publishToMavenLocal
```

AAR 会发布到 `~/.m2/repository/com/k2fsa/sherpa-onnx-tts/1.0.0/`

#### 2. 在新项目中引用

在 `settings.gradle.kts` 中添加：

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}
```

在 `app/build.gradle.kts` 中添加：

```kotlin
dependencies {
    implementation("com.k2fsa:sherpa-onnx-tts:1.0.0")
    // ... 其他依赖
}
```

---

## 模型文件准备

### 支持的模型

本 AAR 库支持两种 TTS 模型：

1. **Matcha-TTS**（推荐）
   - 模型示例：`matcha-icefall-zh-baker`
   - 高质量中文女声

2. **VITS**
   - 模型示例：`vits-zh-aishell3`
   - 支持多说话人

### Matcha 模型文件结构

下载 `matcha-icefall-zh-baker.tar.bz2` 后解压，包含以下文件：

```
matcha-icefall-zh-baker/
├── model.onnx                    # 必需: 声学模型
├── vocos-22khz-univ.onnx         # 必需: 声码器
├── lexicon.txt                   # 必需: 词典
├── tokens.txt                    # 必需: 音素列表
├── dict/                         # 必需: 字典目录
├── phone.fst                     # 可选: 电话号码规则
├── date.fst                      # 可选: 日期规则
└── number.fst                    # 可选: 数字规则
```

### 将模型推送到设备

**对于主应用 (com.example.sherpaonnxtts)**:

```bash
# 解压模型文件
tar -xjf matcha-icefall-zh-baker.tar.bz2

# 推送到设备
adb push matcha-icefall-zh-baker /data/local/tmp/
adb shell
su
cp -r /data/local/tmp/matcha-icefall-zh-baker /data/data/com.example.sherpaonnxtts/files/models/tts/
chmod -R 755 /data/data/com.example.sherpaonnxtts/files/models/tts/
exit
```

**对于 Demo 应用 (com.example.demo.tts)**:

```bash
adb push matcha-icefall-zh-baker /data/local/tmp/
adb shell
su
cp -r /data/local/tmp/matcha-icefall-zh-baker /data/data/com.example.demo.tts/files/models/tts/
chmod -R 755 /data/data/com.example.demo.tts/files/models/tts/
exit
```

**验证文件是否存在**：

```bash
adb shell "ls -la /data/data/com.example.demo.tts/files/models/tts/matcha-icefall-zh-baker/"
```

---

## API 使用指南

### 方式 1: 使用 TtsManager 封装类（简单）

`TtsManager` 提供了更高级的封装，适合快速集成。

#### 初始化

```kotlin
import com.k2fsa.sherpa.onnx.tts.TtsManager

class MainActivity : AppCompatActivity() {
    private lateinit var ttsManager: TtsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val modelDir = File(filesDir, "models/tts/matcha-icefall-zh-baker")

        ttsManager = TtsManager.Builder(this)
            .setModelType(TtsManager.ModelType.MATCHA)
            .setModelPath(modelDir)
            .build()
    }
}
```

#### 生成语音（阻塞式）

```kotlin
fun generateAudio() {
    val text = "你好，这是一个语音合成示例。"
    val audioData = ttsManager.generate(text, speed = 1.0f)

    // audioData 是 FloatArray，可以直接播放或保存
    playAudio(audioData)
}
```

#### 生成语音（流式回调）

```kotlin
fun generateAudioStreaming() {
    val text = "你好，这是一个语音合成示例。"

    ttsManager.generateWithCallback(
        text = text,
        speed = 1.0f,
        callback = { samples ->
            // 实时播放音频片段
            audioTrack.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            1 // 返回 1 继续，返回 0 停止
        }
    )
}
```

#### 释放资源

```kotlin
override fun onDestroy() {
    super.onDestroy()
    ttsManager.release()
}
```

### 方式 2: 直接使用 OfflineTts API（高级）

直接使用底层 API 可以获得更细粒度的控制。

#### 完整示例

```kotlin
import com.k2fsa.sherpa.onnx.*
import android.media.AudioTrack
import android.media.AudioFormat
import android.media.AudioAttributes
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var tts: OfflineTts
    private lateinit var audioTrack: AudioTrack
    private var isSpeaking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 配置模型
        val modelPath = File(filesDir, "models/tts/matcha-icefall-zh-baker")

        val ruleFsts = listOf(
            File(modelPath, "phone.fst"),
            File(modelPath, "date.fst"),
            File(modelPath, "number.fst")
        ).filter { it.exists() }.joinToString(",") { it.absolutePath }

        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                matcha = OfflineTtsMatchaModelConfig(
                    acousticModel = "${modelPath.absolutePath}/model.onnx",
                    vocoder = "${modelPath.absolutePath}/vocos-22khz-univ.onnx",
                    lexicon = "${modelPath.absolutePath}/lexicon.txt",
                    tokens = "${modelPath.absolutePath}/tokens.txt",
                    dataDir = modelPath.absolutePath,
                    noiseScale = 0.8f,      // 情感起伏 (0.0 - 2.0)
                    lengthScale = 1.05f     // 音长缩放 (0.5 - 2.0)
                ),
                numThreads = 4,
                debug = true,
                provider = "cpu"
            ),
            ruleFsts = ruleFsts,
            silenceScale = 0.6f
        )

        // 2. 初始化 TTS
        tts = OfflineTts(assetManager = null, config = config)

        // 3. 初始化 AudioTrack
        initAudioTrack()
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

        audioTrack = AudioTrack(
            attr,
            format,
            bufLength,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
    }

    private fun callback(samples: FloatArray): Int {
        if (isSpeaking) {
            audioTrack.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            return 1 // 继续生成
        }
        return 0 // 停止生成
    }

    fun speak(text: String, speed: Float = 1.0f) {
        isSpeaking = true
        audioTrack.play()

        Thread {
            tts.generateWithCallback(
                text = text,
                sid = 0,
                speed = speed,
                callback = this::callback
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
        audioTrack.release()
        tts.release()
    }
}
```

---

## 参数调节说明

### 1. 语速 (Speed)

控制朗读速度。

- **范围**: `0.5 - 2.0`
- **默认值**: `1.0`
- **建议范围**: `0.8 - 1.2`
- **效果**:
  - `< 1.0`: 放慢语速
  - `= 1.0`: 正常语速
  - `> 1.0`: 加快语速

**代码示例**：

```kotlin
// 慢速朗读
tts.generateWithCallback(text, sid = 0, speed = 0.8f, callback = this::callback)

// 快速朗读
tts.generateWithCallback(text, sid = 0, speed = 1.2f, callback = this::callback)
```

**UI 实现（SeekBar）**：

```xml
<SeekBar
    android:id="@+id/seek_speed"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:max="200"
    android:progress="100" />
```

```kotlin
val speed = seekSpeed.progress / 100f  // 100 -> 1.0f
```

### 2. 情感起伏 (Noise Scale)

控制音调变化的随机性，影响语音的情感表现。

- **范围**: `0.0 - 2.0`
- **默认值**: `0.8`
- **建议范围**: `0.6 - 1.0`
- **效果**:
  - `= 0.0`: 完全平淡，无情感起伏
  - `0.6 - 0.8`: 自然的情感表达（推荐）
  - `> 1.0`: 情感非常夸张

**注意**: 此参数需要在初始化 TTS 时设置，运行时修改需要重新创建 `OfflineTts` 对象。

**代码示例**：

```kotlin
// 方式 1: 初始化时设置
val config = OfflineTtsConfig(
    model = OfflineTtsModelConfig(
        matcha = OfflineTtsMatchaModelConfig(
            // ...
            noiseScale = 0.8f  // 设置情感起伏
        ),
        // ...
    ),
    // ...
)

// 方式 2: 运行时修改（需要重新初始化）
fun updateNoiseScale(newNoise: Float) {
    tts.release()  // 释放旧对象

    val config = OfflineTtsConfig(
        model = OfflineTtsModelConfig(
            matcha = OfflineTtsMatchaModelConfig(
                // ...
                noiseScale = newNoise
            ),
            // ...
        ),
        // ...
    )

    tts = OfflineTts(assetManager = null, config = config)
}
```

**UI 实现（SeekBar）**：

```xml
<SeekBar
    android:id="@+id/seek_noise"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:max="200"
    android:progress="80" />
```

```kotlin
val noise = seekNoise.progress / 100f  // 80 -> 0.8f
```

### 3. 音长缩放 (Length Scale)

控制音素的持续时间。

- **范围**: `0.5 - 2.0`
- **默认值**: `1.05`
- **建议范围**: `0.9 - 1.2`
- **效果**:
  - `< 1.0`: 音素缩短，语速加快
  - `= 1.0`: 标准音素长度
  - `> 1.0`: 音素拉长，语速放慢

**注意**: 此参数也需要在初始化时设置。与 Speed 参数的区别：
- **lengthScale**: 改变音素本身的长度
- **speed**: 改变整体播放速度

**代码示例**：

```kotlin
val config = OfflineTtsConfig(
    model = OfflineTtsModelConfig(
        matcha = OfflineTtsMatchaModelConfig(
            // ...
            lengthScale = 1.05f  // 稍微拉长音素
        ),
        // ...
    ),
    // ...
)
```

**UI 实现（SeekBar）**：

```xml
<SeekBar
    android:id="@+id/seek_length"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:max="200"
    android:progress="105" />
```

```kotlin
val length = seekLength.progress / 100f  // 105 -> 1.05f
```

### 4. 静音缩放 (Silence Scale)

控制句子之间的停顿长度。

- **范围**: `0.0 - 1.0`
- **默认值**: `0.6`
- **效果**:
  - `= 0.0`: 无停顿
  - `= 0.6`: 适中停顿（推荐）
  - `= 1.0`: 完整停顿

**代码示例**：

```kotlin
val config = OfflineTtsConfig(
    model = OfflineTtsModelConfig(
        // ...
    ),
    ruleFsts = ruleFsts,
    silenceScale = 0.6f  // 设置停顿时长
)
```

### 完整的参数调节示例

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var tts: OfflineTts
    private lateinit var seekSpeed: SeekBar
    private lateinit var seekNoise: SeekBar
    private lateinit var seekLength: SeekBar
    private var modelPath: File? = null

    private fun updateConfigAndRestartTts() {
        // 从 SeekBar 获取当前值
        val noise = seekNoise.progress / 100f   // 默认 80 -> 0.8f
        val length = seekLength.progress / 100f // 默认 105 -> 1.05f

        // 释放旧的 TTS 对象
        if (::tts.isInitialized) {
            tts.release()
        }

        // 创建新配置
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                matcha = OfflineTtsMatchaModelConfig(
                    acousticModel = "${modelPath!!.absolutePath}/model.onnx",
                    vocoder = "${modelPath!!.absolutePath}/vocos-22khz-univ.onnx",
                    lexicon = "${modelPath!!.absolutePath}/lexicon.txt",
                    tokens = "${modelPath!!.absolutePath}/tokens.txt",
                    dataDir = modelPath!!.absolutePath,
                    noiseScale = noise,
                    lengthScale = length
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

    private fun speak(text: String) {
        // 朗读前先更新配置
        updateConfigAndRestartTts()

        // 获取当前语速
        val speed = seekSpeed.progress / 100f

        Thread {
            tts.generateWithCallback(
                text = text,
                sid = 0,
                speed = speed,
                callback = this::callback
            )
        }.start()
    }
}
```

---

## 故障排查

### 1. 构建错误: 找不到 libsherpa-onnx-jni.so

**错误信息**：
```
❌ 错误: 缺少 library/src/main/jniLibs/arm64-v8a/libsherpa-onnx-jni.so
```

**解决方法**：
```bash
./download-libs.sh
```

### 2. 运行时错误: java.lang.UnsatisfiedLinkError

**错误信息**：
```
java.lang.UnsatisfiedLinkError: dlopen failed: library "libsherpa-onnx-jni.so" not found
```

**原因**: AAR 中缺少 JNI 库文件

**解决方法**:
1. 检查 AAR 是否包含 .so 文件：
   ```bash
   unzip -l library-release.aar | grep libsherpa-onnx-jni.so
   ```
2. 如果没有，重新构建 AAR：
   ```bash
   ./download-libs.sh
   ./build-library-aar.sh
   ```

### 3. 模型文件不存在

**错误信息**：
```
--matcha-acoustic-model: '/data/user/0/com.example.demo.tts/files/models/tts/matcha-icefall-zh-baker/model.onnx' does not exist
```

**解决方法**：

参考 [模型文件准备](#模型文件准备) 章节，使用 adb 推送模型文件到正确的目录。

### 4. 缺少 vocoder 导致无限循环

**错误信息**：
```
Vocoder is not specified. Return an empty wave
```

**原因**: Matcha 模型必须指定 vocoder

**解决方法**：

确保配置中包含 `vocoder` 参数：

```kotlin
matcha = OfflineTtsMatchaModelConfig(
    acousticModel = "$modelPath/model.onnx",
    vocoder = "$modelPath/vocos-22khz-univ.onnx",  // 必需！
    // ...
)
```

### 5. Demo 项目无法编译

**错误信息**：
```
Unable to find Gradle tasks to build
```

**原因**: Demo 是独立项目，不应包含在主项目的 settings.gradle.kts 中

**解决方法**:

检查主项目的 `settings.gradle.kts`，确保没有包含 demo：

```kotlin
rootProject.name = "SherpaOnnxTts"
include(":app")
include(":library")
// 不要包含 include(":demo:app")
```

单独构建 Demo：

```bash
cd demo
./gradlew :app:assembleDebug
```

### 6. 播放音频时没有声音

**检查清单**：

1. **音量设置**: 确保设备音量已打开
2. **AudioTrack 初始化**: 检查采样率是否正确
   ```kotlin
   val sampleRate = tts.sampleRate()  // 通常是 22050 Hz
   ```
3. **AudioTrack 状态**: 确保调用了 `play()`
   ```kotlin
   audioTrack.play()
   ```
4. **权限**: 检查是否有音频播放权限（通常不需要额外权限）

### 7. 类型不匹配错误

**错误信息**：
```
Type mismatch: inferred type is (FloatArray) -> Any but (FloatArray) -> Int was expected
```

**原因**: 回调函数签名不正确

**解决方法**：

确保回调函数返回 `Int`：

```kotlin
private fun callback(samples: FloatArray): Int {
    if (isSpeaking) {
        audioTrack.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        return 1  // 继续生成
    }
    return 0  // 停止生成
}
```

### 8. 内存泄漏

**症状**: 应用长时间运行后崩溃或卡顿

**原因**: 未正确释放资源

**解决方法**：

确保在 `onDestroy()` 中释放资源：

```kotlin
override fun onDestroy() {
    super.onDestroy()
    if (::audioTrack.isInitialized) {
        audioTrack.stop()
        audioTrack.release()
    }
    if (::tts.isInitialized) {
        tts.release()  // 或 tts.free()
    }
}
```

---

## 完整示例项目

### Demo 项目地址

本仓库包含完整的 Demo 示例：

```
SherpaOnnxTts/demo/
```

运行 Demo：

```bash
# 1. 构建并复制 AAR
cd SherpaOnnxTts
./download-libs.sh
./build-library-aar.sh
./copy-aar-to-demo.sh

# 2. 推送模型文件
tar -xjf matcha-icefall-zh-baker.tar.bz2
adb push matcha-icefall-zh-baker /data/local/tmp/
adb shell "su -c 'cp -r /data/local/tmp/matcha-icefall-zh-baker /data/data/com.example.demo.tts/files/models/tts/ && chmod -R 755 /data/data/com.example.demo.tts/files/models/tts/'"

# 3. 构建并安装 Demo
cd demo
./gradlew :app:installDebug
```

### 主要文件

- `demo/app/src/main/java/com/example/demo/tts/MainActivity.kt`: 完整的 UI 和 TTS 逻辑
- `demo/app/src/main/res/layout/activity_main.xml`: UI 布局（包含三个 SeekBar）
- `demo/app/build.gradle.kts`: AAR 依赖配置

---

## 许可证

本项目基于 sherpa-onnx 开源项目，遵循 Apache 2.0 许可证。

---

## 联系与支持

- **sherpa-onnx GitHub**: https://github.com/k2-fsa/sherpa-onnx
- **问题反馈**: 请在项目 Issues 中提交

---

## 更新日志

### v1.0.0 (2025-01-XX)

- ✅ 初始版本发布
- ✅ 支持 Matcha-TTS 和 VITS 模型
- ✅ 提供 TtsManager 封装类
- ✅ 支持流式音频回调
- ✅ 完整的 Demo 示例
- ✅ 详细的使用文档
