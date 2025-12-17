# Sherpa ASR AAR 使用指南

## 📦 AAR 打包

### 1. 构建 AAR 文件

```bash
cd StreamingASRApp
./gradlew :library:assembleRelease
```

生成的 AAR 文件位置：
```
library/build/outputs/aar/library-release.aar
```

### 2. AAR 包含内容

- ✅ `SherpaAsrManager` - 高级 API 封装
- ✅ `ModelManager` - 模型管理
- ✅ `AudioRecorder` - 音频录制
- ✅ `Vad`, `KeywordSpotter`, `OnlineRecognizer` - Sherpa-ONNX 接口
- ⚠️  **不包含 JNI 库** - 需要单独部署（见下方说明）
- ⚠️  **不包含模型文件** - 需要单独部署到设备

---

## 🚀 在项目中使用 AAR

### 1. 导入 AAR

将 `library-release.aar` 复制到你的项目：

```
your-project/
  └── app/
      └── libs/
          └── sherpa-asr-release.aar
```

在 `app/build.gradle.kts` 中添加：

```kotlin
dependencies {
    implementation(files("libs/sherpa-asr-release.aar"))

    // 必需的依赖
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

### 2. 部署 Sherpa-ONNX JNI 库

**重要：** AAR 不包含 `.so` 文件，需要手动添加到项目中。

#### 方法 A：从 Maven 引入（推荐）

在 `app/build.gradle.kts` 添加：

```kotlin
dependencies {
    // Sherpa-ONNX JNI 库（替换为实际版本）
    implementation("com.k2fsa.sherpa:sherpa-onnx-android:1.x.x")
}
```

#### 方法 B：手动复制 .so 文件

1. 下载 Sherpa-ONNX 预编译库：
   ```
   https://github.com/k2-fsa/sherpa-onnx/releases
   ```

2. 复制到项目：
   ```
   your-project/app/src/main/jniLibs/
     ├── arm64-v8a/
     │   └── libsherpa-onnx-jni.so
     └── armeabi-v7a/
         └── libsherpa-onnx-jni.so
   ```

### 3. 部署模型文件

模型文件需要部署到设备的内部存储：

```
/data/data/你的包名/files/models/
  ├── kws/                          # KWS 唤醒模型（可选）
  │   ├── decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx
  │   ├── encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx
  │   ├── joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx
  │   ├── tokens.txt
  │   └── keywords.txt
  │
  ├── asr/                          # ASR 识别模型
  │   ├── encoder-epoch-99-avg-1.int8.onnx
  │   ├── decoder-epoch-99-avg-1.onnx
  │   ├── joiner-epoch-99-avg-1.int8.onnx
  │   ├── tokens.txt
  │   ├── lexicon.txt               # 同音字词典（可选）
  │   └── replace.fst               # 同音字替换规则（可选）
  │
  └── vad/                          # VAD 模型
      └── silero_vad.onnx
```

#### 部署方法

**方法 1：通过 adb 推送**

```bash
# 推送整个模型目录
adb push models/ /sdcard/models/

# 在设备上移动到应用私有目录
adb shell
su  # 需要 root 权限
cp -r /sdcard/models /data/data/你的包名/files/
chmod -R 755 /data/data/你的包名/files/models
```

**方法 2：在应用中从 assets 复制**

1. 将模型放入 `app/src/main/assets/models/`
2. 应用启动时复制到内部存储（会增加 APK 体积）

---

## 💻 代码示例

### 基础使用

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var asr: SherpaAsrManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 创建 ASR Manager
        asr = SherpaAsrManager(this)

        // 2. 设置回调
        setupCallbacks()

        // 3. 检查模型
        if (!asr.isModelReady()) {
            Log.e(TAG, "模型未就绪: ${asr.getModelDir()}")
            return
        }

        // 4. 开始监听
        asr.startListening()
    }

    private fun setupCallbacks() {
        // 唤醒词检测
        asr.onWakeWordDetected = { keyword ->
            Log.i(TAG, "检测到唤醒词: $keyword")
            runOnUiThread {
                tvStatus.text = "🔊 已唤醒"
            }
        }

        // 句子完成（发送给 LLM）
        asr.onSentenceComplete = { text ->
            Log.i(TAG, "句子完成: $text")

            // 🎯 发送给 LLM
            sendToLLM(text)
        }

        // 实时结果（显示在 UI）
        asr.onPartialResult = { text ->
            runOnUiThread {
                tvResult.text = text
            }
        }

        // 状态变化
        asr.onStateChanged = { state ->
            runOnUiThread {
                when (state) {
                    SherpaAsrManager.State.STANDBY -> {
                        tvStatus.text = "⏸️ 待机中，等待唤醒..."
                    }
                    SherpaAsrManager.State.ACTIVE -> {
                        tvStatus.text = "🎙️ 正在识别..."
                    }
                }
            }
        }

        // 错误处理
        asr.onError = { error ->
            Log.e(TAG, "错误: $error")
        }
    }

    private fun sendToLLM(text: String) {
        // 发送给 LLM 处理
        lifecycleScope.launch {
            val response = llmClient.chat(text)
            playTTS(response)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        asr.release()  // 释放资源
    }
}
```

### 与 LLM 集成示例

```kotlin
class VoiceAssistant(private val context: Context) {
    private val asr = SherpaAsrManager(context)
    private val llm = LLMClient()
    private var isLLMProcessing = false

    fun start() {
        asr.onSentenceComplete = { text ->
            if (!isLLMProcessing) {
                handleUserInput(text)
            } else {
                Log.i(TAG, "LLM 处理中，忽略: $text")
            }
        }

        asr.startListening()
    }

    private fun handleUserInput(text: String) {
        lifecycleScope.launch {
            isLLMProcessing = true

            try {
                // 发送给 LLM
                val response = llm.chat(text)

                // 播放 TTS
                tts.speak(response)

            } finally {
                isLLMProcessing = false
            }
        }
    }
}
```

### 自定义 VAD 参数

```kotlin
val asr = SherpaAsrManager(context)

// 配置 VAD（调整断句灵敏度）
asr.vadConfig = SherpaAsrManager.VadConfig(
    threshold = 0.5F,
    minSilenceDuration = 1.5F,  // 静音 1.5 秒才断句（更宽松）
    minSpeechDuration = 0.25F,
    maxSpeechDuration = 15.0F   // 最长支持 15 秒语音
)

// 配置 KWS
asr.kwsConfig = SherpaAsrManager.KwsConfig(
    keywordsFile = "keywords.txt",
    threshold = 0.6F  // 更高的唤醒阈值（减少误触发）
)

asr.startListening()
```

---

## 🔧 权限配置

在 `AndroidManifest.xml` 添加：

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

运行时请求权限：

```kotlin
if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
    != PackageManager.PERMISSION_GRANTED) {
    ActivityCompat.requestPermissions(
        this,
        arrayOf(Manifest.permission.RECORD_AUDIO),
        REQUEST_CODE
    )
}
```

---

## 📖 API 文档

### SherpaAsrManager

#### 构造函数
```kotlin
SherpaAsrManager(context: Context)
```

#### 回调接口
```kotlin
var onWakeWordDetected: ((keyword: String) -> Unit)?
var onSentenceComplete: ((text: String) -> Unit)?
var onPartialResult: ((text: String) -> Unit)?
var onStateChanged: ((state: State) -> Unit)?
var onError: ((error: String) -> Unit)?
```

#### 方法
```kotlin
fun startListening()               // 开始监听
fun stopListening()                // 停止监听
fun release()                      // 释放资源
fun isModelReady(): Boolean        // 检查模型是否就绪
fun getModelDir(): File            // 获取模型目录
fun getState(): State              // 获取当前状态
```

#### 配置
```kotlin
var vadConfig: VadConfig           // VAD 配置
var kwsConfig: KwsConfig           // KWS 配置
```

---

## ❓ 常见问题

### 1. AAR 找不到 JNI 库

**错误：** `UnsatisfiedLinkError: libsherpa-onnx-jni.so not found`

**解决：** 确保添加了 Sherpa-ONNX JNI 依赖或手动复制了 `.so` 文件。

### 2. 模型加载失败

**错误：** `模型未就绪`

**解决：**
1. 检查模型文件是否部署到 `/data/data/包名/files/models/`
2. 使用 `adb shell ls /data/data/包名/files/models/` 验证

### 3. 无法唤醒

**原因：** 没有 KWS 模型或 `keywords.txt` 文件

**解决：**
- 部署完整的 KWS 模型到 `models/kws/` 目录
- 或者直接使用 ASR 模式（不需要唤醒）

---

## 📝 模型下载

推荐模型：

**KWS 唤醒模型：**
```
https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01.tar.bz2
```

**ASR 识别模型：**
```
https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2
```

**VAD 模型：**
```
https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx
```

---

## 🎯 最佳实践

1. **模型部署**：应用首次启动时检查模型是否存在，提示用户下载
2. **权限管理**：在使用前检查并请求录音权限
3. **生命周期**：在 Activity/Fragment 销毁时调用 `asr.release()`
4. **错误处理**：设置 `onError` 回调，记录日志便于调试
5. **UI 更新**：回调可能在后台线程，使用 `runOnUiThread` 更新 UI
6. **资源管理**：长时间不使用时调用 `stopListening()` 节省电量

---

## 📄 许可证

本项目基于 Sherpa-ONNX 构建，遵循其开源许可证。
