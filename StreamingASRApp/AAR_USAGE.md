# Streaming ASR Library AAR 使用指南

本文档说明如何打包和使用 Streaming ASR Library AAR。

## 📦 打包 AAR

### 方式1：使用脚本（推荐）

```bash
cd StreamingASRApp
./build-aar.sh
```

### 方式2：手动构建

```bash
# 构建 Release AAR
./gradlew :app:assembleRelease

# 构建 Debug AAR
./gradlew :app:assembleDebug

# 输出目录
# app/build/outputs/aar/app-release.aar
# app/build/outputs/aar/app-debug.aar
```

### 方式3：发布到Maven本地仓库

```bash
./gradlew publishToMavenLocal
```

---

## 🔧 在其他项目中使用 AAR

### 方法1：直接引用AAR文件

1. **复制AAR文件到项目**

```bash
cp app/build/outputs/aar/app-release.aar /path/to/your/project/app/libs/
```

2. **在 build.gradle.kts 中添加依赖**

```kotlin
dependencies {
    // 引用 AAR
    implementation(files("libs/app-release.aar"))

    // AAR 的依赖项也需要添加
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

### 方法2：从Maven本地仓库引用

如果你使用了 `publishToMavenLocal`：

```kotlin
repositories {
    mavenLocal()
}

dependencies {
    implementation("com.example:streaming-asr:1.0.0")
}
```

---

## 📖 API 使用说明

### 1. 初始化 ModelManager

```kotlin
import com.example.streamingasr.ModelManager
import com.example.streamingasr.ModelFiles
import com.example.streamingasr.AudioRecorder

val modelManager = ModelManager(context)
```

### 2. 创建识别器（三种方式）

#### 方式1：使用预定义的模型类型（需要特定文件名）

```kotlin
val recognizer = modelManager.createOnlineRecognizer(
    modelType = ModelManager.ModelType.ZIPFORMER_TRANSDUCER,
    numThreads = 4
)
```

**需要的文件名**：
- `encoder-epoch-99-avg-1.onnx`
- `decoder-epoch-99-avg-1.onnx`
- `joiner-epoch-99-avg-1.onnx`
- `tokens.txt`

#### 方式2：使用自定义模型文件名 ✨ NEW

```kotlin
val modelFiles = ModelFiles(
    encoder = "my_encoder.onnx",
    decoder = "my_decoder.onnx",
    joiner = "my_joiner.onnx",
    tokens = "tokens.txt",
    lexicon = "lexicon.txt",      // 可选
    replaceFst = "replace.fst"     // 可选
)

val recognizer = modelManager.createOnlineRecognizer(
    modelFiles = modelFiles,
    numThreads = 4
)
```

#### 方式3：自动检测模型 ✨ NEW

```kotlin
// 自动扫描 models 目录，根据文件名模式匹配模型
val recognizer = modelManager.createOnlineRecognizerAuto(
    numThreads = 4
)
```

**自动检测规则**：
- 查找文件名包含 `encoder` 的 .onnx 文件
- 查找文件名包含 `decoder` 的 .onnx 文件
- 查找文件名包含 `joiner` 的 .onnx 文件
- 如果找到以上三个文件 → Transducer 模型
- 否则查找其他 .onnx 文件 → Paraformer/CTC 模型

### 3. 音频录制

```kotlin
val audioRecorder = AudioRecorder(
    sampleRate = 16000,
    cacheDir = File(context.filesDir, "audio_cache")
)

// 开始录制
audioRecorder.startRecording(savePcm = true)

// 读取音频数据
val samples = audioRecorder.readAudioData()

// 停止录制
audioRecorder.stopRecording()
```

### 4. 流式识别

```kotlin
val stream = recognizer.createStream()

// 识别循环
while (isRecording) {
    val samples = audioRecorder.readAudioData()
    if (samples != null && samples.isNotEmpty()) {
        stream.acceptWaveform(samples, 16000)

        while (recognizer.isReady(stream)) {
            recognizer.decode(stream)
        }

        val result = recognizer.getResult(stream)
        val text = result.text

        // 检查句子是否结束
        if (recognizer.isEndpoint(stream)) {
            println("句子结束: $text")
            recognizer.reset(stream)
        }
    }
}

// 释放资源
stream.release()
```

---

## 🎯 完整示例

```kotlin
class MyActivity : AppCompatActivity() {
    private lateinit var modelManager: ModelManager
    private lateinit var audioRecorder: AudioRecorder
    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化
        modelManager = ModelManager(this)
        audioRecorder = AudioRecorder(
            sampleRate = 16000,
            cacheDir = File(filesDir, "audio_cache")
        )

        // 方式1: 自动检测模型（最灵活）
        recognizer = modelManager.createOnlineRecognizerAuto()

        // 方式2: 指定自定义文件名
        // val modelFiles = ModelFiles(
        //     encoder = "my_encoder_v2.onnx",
        //     decoder = "my_decoder_v2.onnx",
        //     joiner = "my_joiner_v2.onnx"
        // )
        // recognizer = modelManager.createOnlineRecognizer(modelFiles)

        if (recognizer == null) {
            Log.e(TAG, "Failed to create recognizer")
            return
        }

        stream = recognizer?.createStream()
    }

    fun startRecognition() {
        audioRecorder.startRecording(savePcm = true)

        lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                val samples = audioRecorder.readAudioData()
                if (samples != null) {
                    stream?.acceptWaveform(samples, 16000)

                    while (recognizer?.isReady(stream!!) == true) {
                        recognizer?.decode(stream!!)
                    }

                    val result = recognizer?.getResult(stream!!)
                    val text = result?.text ?: ""

                    withContext(Dispatchers.Main) {
                        // 更新 UI
                        updateText(text)
                    }
                }
            }
        }
    }

    fun stopRecognition() {
        audioRecorder.stopRecording()
    }

    override fun onDestroy() {
        super.onDestroy()
        stream?.release()
        audioRecorder.release()
    }
}
```

---

## 📋 模型文件要求

### 模型文件位置

AAR 使用者需要将模型文件放在：
```
/data/data/<你的包名>/files/models/
```

### 支持的模型类型

1. **Transducer 模型**（需要3个文件）
   - encoder 文件（任意名称，包含 "encoder"）
   - decoder 文件（任意名称，包含 "decoder"）
   - joiner 文件（任意名称，包含 "joiner"）
   - tokens.txt

2. **Paraformer / CTC 模型**（单文件）
   - model 文件（任意 .onnx 文件）
   - tokens.txt

3. **HomophoneReplacer**（可选）
   - lexicon.txt
   - replace.fst

### 推送模型到设备

```bash
# 推送到临时目录
adb push encoder.onnx /data/local/tmp/
adb push decoder.onnx /data/local/tmp/
adb push joiner.onnx /data/local/tmp/
adb push tokens.txt /data/local/tmp/

# 复制到应用目录
adb shell "run-as <你的包名> mkdir -p /data/data/<你的包名>/files/models/"
adb shell "run-as <你的包名> cp /data/local/tmp/*.onnx /data/data/<你的包名>/files/models/"
adb shell "run-as <你的包名> cp /data/local/tmp/tokens.txt /data/data/<你的包名>/files/models/"
```

---

## 🆕 新功能亮点

### 1. 灵活的模型文件名
- ✅ 不再限制特定文件名
- ✅ 支持任意命名的模型文件
- ✅ 自动检测模型类型

### 2. 自动模型检测
- ✅ 自动扫描并匹配模型文件
- ✅ 智能识别 Transducer / Paraformer / CTC
- ✅ 无需手动指定文件名

### 3. 同音字纠正
- ✅ 自动纠正常见同音字错误
- ✅ 基于 FST 规则，速度极快
- ✅ 可选功能，不影响正常使用

### 4. 语义断句
- ✅ 基于标点符号的智能断句
- ✅ 优于传统的静音检测
- ✅ 识别结果更符合语义

---

## 🔗 相关资源

- [sherpa-onnx 官方仓库](https://github.com/k2-fsa/sherpa-onnx)
- [模型下载](https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models)
- [HomophoneReplacer 文件](https://github.com/k2-fsa/sherpa-onnx/releases/tag/hr-files)

---

## 📄 许可证

本库基于 Apache 2.0 许可证。
sherpa-onnx 使用 Apache 2.0 许可证。
