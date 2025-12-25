# SherpaOnnx TTS AAR 使用说明

## 📦 AAR 包简介

本 AAR 包（`tts.aar`）提供离线 TTS（文本转语音）功能，基于 sherpa-onnx 引擎，支持 Matcha-TTS 模型。

**核心特性**：
- 离线运行，无需联网
- 高质量中文语音合成
- 可调参数：语速、情感起伏、音长缩放
- 流式音频播放

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

## 📥 AAR 构建流程

### 步骤 1: 下载 JNI 库

```bash
cd SherpaOnnxTts
./download-libs.sh
```

此脚本会下载并提取 `libsherpa-onnx-jni.so` 文件到 `library/src/main/jniLibs/` 目录。

### 步骤 2: 构建 AAR 包

```bash
./build-library-aar.sh
```

构建成功后，AAR 文件位置：`library/build/outputs/aar/library-release.aar`

### 步骤 3: 重命名 AAR（建议）

```bash
cp library/build/outputs/aar/library-release.aar tts.aar
```

---

## 📦 集成 AAR

### 方法 1: 本地 AAR 文件集成

**1. 复制 AAR 文件**

将 `tts.aar` 复制到项目：

```bash
cp tts.aar YourProject/app/libs/
```

**2. 配置依赖**

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

**重要**：模型文件直接放在 `models/tts/` 目录下，不需要 `matcha-icefall-zh-baker` 子目录。

**验证**：

```bash
adb shell "ls -la /data/data/YOUR_PACKAGE/files/models/tts/"
```

应该看到：`model.onnx`、`vocos-22khz-univ.onnx`、`lexicon.txt`、`tokens.txt`、`dict/` 等文件。

---

## 🎯 核心功能

### 1. 基础初始化

```kotlin
import com.k2fsa.sherpa.onnx.*

// 模型路径（直接指向 tts 目录）
val modelPath = File(filesDir, "models/tts").absolutePath

// 创建配置
val config = OfflineTtsConfig(
    model = OfflineTtsModelConfig(
        matcha = OfflineTtsMatchaModelConfig(
            acousticModel = "$modelPath/model.onnx",
            vocoder = "$modelPath/vocos-22khz-univ.onnx",
            lexicon = "$modelPath/lexicon.txt",
            tokens = "$modelPath/tokens.txt",
            dataDir = modelPath,
            noiseScale = 0.8f,      // 情感起伏
            lengthScale = 1.05f     // 音长缩放
        ),
        numThreads = 4,
        provider = "cpu"
    ),
    ruleFsts = "",              // 规则文件路径（可选）
    silenceScale = 0.6f         // 停顿时长
)

// 初始化 TTS
val tts = OfflineTts(assetManager = null, config = config)
```

### 2. 语音合成

**方式 1：流式回调**（推荐）

```kotlin
tts.generateWithCallback(
    text = "你好世界",
    sid = 0,
    speed = 1.0f,           // 语速控制
    callback = { samples ->
        // 处理音频数据
        audioTrack.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        1  // 返回 1 继续，返回 0 停止
    }
)
```

**方式 2：一次性生成**

```kotlin
val audio = tts.generate(
    text = "你好世界",
    sid = 0,
    speed = 1.0f
)
// audio 是完整的音频数据
```

### 3. 参数调节

#### 语速（Speed）

- **位置**：`generateWithCallback()` 或 `generate()` 的 `speed` 参数
- **范围**：0.5 - 2.0
- **效果**：运行时动态调整，无需重新初始化
- **示例**：
  ```kotlin
  tts.generateWithCallback(text = "测试", speed = 1.5f, ...)  // 1.5倍速
  ```

#### 情感起伏（Noise Scale）

- **位置**：`OfflineTtsMatchaModelConfig` 的 `noiseScale` 参数
- **范围**：0.0 - 2.0（推荐 0.6 - 1.0）
- **效果**：控制音调变化，值越大情感越丰富
- **注意**：需要重新创建 `OfflineTts` 对象
- **示例**：
  ```kotlin
  noiseScale = 0.8f  // 自然情感
  ```

#### 音长缩放（Length Scale）

- **位置**：`OfflineTtsMatchaModelConfig` 的 `lengthScale` 参数
- **范围**：0.5 - 2.0（推荐 0.9 - 1.2）
- **效果**：控制音素持续时间，值越大音素越长
- **注意**：需要重新创建 `OfflineTts` 对象
- **示例**：
  ```kotlin
  lengthScale = 1.05f  // 标准长度
  ```

#### 停顿时长（Silence Scale）

- **位置**：`OfflineTtsConfig` 的 `silenceScale` 参数
- **范围**：0.0 - 1.0
- **效果**：控制句子间停顿
- **示例**：
  ```kotlin
  silenceScale = 0.6f
  ```

### 4. 规则处理

**功能**：自动处理电话号码、日期、数字等特殊格式

**配置**：
```kotlin
val ruleFsts = listOf(
    "$modelPath/phone.fst",
    "$modelPath/date.fst",
    "$modelPath/number.fst"
).filter { File(it).exists() }.joinToString(",")

// 在 OfflineTtsConfig 中设置
ruleFsts = ruleFsts
```

**效果**：
- "138-0013-8000" → "一三八零零一三八零零零"
- "2025-01-15" → "二零二五年一月十五日"
- "12345" → "一万两千三百四十五"

### 5. 音频播放

**初始化 AudioTrack**：

```kotlin
val sampleRate = tts.sampleRate()  // 获取采样率（22050 Hz）

val track = AudioTrack(
    AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .build(),
    AudioFormat.Builder()
        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
        .setSampleRate(sampleRate)
        .build(),
    AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT),
    AudioTrack.MODE_STREAM,
    AudioManager.AUDIO_SESSION_ID_GENERATE
)
```

**播放**：

```kotlin
track.play()

Thread {
    tts.generateWithCallback(
        text = "要朗读的文字",
        sid = 0,
        speed = 1.0f,
        callback = { samples ->
            track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            1
        }
    )
}.start()
```

### 6. 资源释放

```kotlin
override fun onDestroy() {
    super.onDestroy()
    track.release()
    tts.release()
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

### 新闻播报（快速 + 平稳）

```kotlin
speed = 1.5f
noiseScale = 0.6f
lengthScale = 0.9f
```

### 儿童故事（慢速 + 生动）

```kotlin
speed = 0.9f
noiseScale = 1.2f
lengthScale = 1.1f
```

### 视障辅助（慢速 + 清晰）

```kotlin
speed = 0.8f
noiseScale = 0.7f
lengthScale = 1.05f
```

### 学习发音（极慢 + 拉长）

```kotlin
speed = 0.7f
noiseScale = 0.8f
lengthScale = 1.2f
```

---

## 📝 注意事项

1. **AAR 文件名**：建议重命名为 `tts.aar`
2. **模型文件路径**：`/data/data/YOUR_PACKAGE/files/models/tts/`（直接在 tts 目录下）
3. **必需文件**：`model.onnx`、`vocos-22khz-univ.onnx`、`lexicon.txt`、`tokens.txt`、`dict/`
4. **可选文件**：`phone.fst`、`date.fst`、`number.fst`
5. **线程要求**：TTS 生成必须在子线程执行
6. **参数更新**：修改 `noiseScale` 或 `lengthScale` 需要重新创建 `OfflineTts` 对象
7. **资源释放**：使用完毕后必须调用 `tts.release()` 和 `track.release()`

---

## 🔍 故障排查

### 模型文件不存在

**错误**：`--matcha-acoustic-model: '.../model.onnx' does not exist`

**解决**：检查模型文件是否正确推送到 `/data/data/YOUR_PACKAGE/files/models/tts/` 目录

### 缺少 vocoder

**错误**：`Vocoder is not specified. Return an empty wave`

**解决**：确保配置中设置了 `vocoder = "$modelPath/vocos-22khz-univ.onnx"`

### JNI 库缺失

**错误**：`java.lang.UnsatisfiedLinkError: dlopen failed: library "libsherpa-onnx-jni.so" not found`

**解决**：
1. 检查 AAR 是否包含 .so 文件：`unzip -l tts.aar | grep libsherpa-onnx-jni.so`
2. 如果缺失，重新运行 `./download-libs.sh` 和 `./build-library-aar.sh`
