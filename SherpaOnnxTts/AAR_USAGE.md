# SherpaOnnxTts AAR 使用文档

## 简介

SherpaOnnxTts 是一个基于 sherpa-onnx 的 Android 文本转语音（TTS）库，支持中文语音合成。

## 构建 AAR

### 1. 下载依赖库

首先需要下载 sherpa-onnx 的 native 库文件：

```bash
cd SherpaOnnxTts
./download-libs.sh
```

这会下载 .so 文件到 app 和 library 模块。

### 2. 构建 AAR 包

```bash
./build-library-aar.sh
```

构建成功后，AAR 文件位于：`library/build/outputs/aar/library-release.aar`

## 在项目中使用 AAR

### 1. 导入 AAR 文件

将 `library-release.aar` 复制到你的项目的 `app/libs/` 目录。

### 2. 添加依赖

在你的 `app/build.gradle.kts` 中添加：

```kotlin
dependencies {
    implementation(files("libs/library-release.aar"))

    // 必要的依赖（如果你的项目还没有）
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

### 3. 准备模型文件

将 TTS 模型文件推送到设备：

```bash
# 下载模型（选择一个）
# Matcha 模型（推荐）
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/matcha-icefall-zh-baker.tar.bz2
tar -xjf matcha-icefall-zh-baker.tar.bz2

# VITS 模型
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-melo-tts-zh_en.tar.bz2
tar -xjf vits-melo-tts-zh_en.tar.bz2

# 推送到设备
adb shell mkdir -p /data/data/你的包名/files/models/tts
adb push matcha-icefall-zh-baker /data/data/你的包名/files/models/tts/
```

### 4. 在代码中使用

#### 基本使用

```kotlin
import com.k2fsa.sherpa.onnx.tts.TtsManager

class MainActivity : AppCompatActivity() {
    private lateinit var ttsManager: TtsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化 TTS
        ttsManager = TtsManager(this)

        // 使用 Matcha 模型
        ttsManager.initialize("matcha-icefall-zh-baker", "matcha")

        // 或使用 VITS 模型
        // ttsManager.initialize("vits-melo-tts-zh_en", "vits")

        // 朗读文本
        ttsManager.speak("你好，这是一个简单的中文语音合成示例。")
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsManager.release()
    }
}
```

#### 高级使用

```kotlin
// 自定义语速和说话人
ttsManager.speak(
    text = "你好",
    speed = 0.8f,  // 0.5-2.0，默认 0.8
    sid = 0        // 说话人ID，默认 0
)

// 使用自定义音频回调
ttsManager.speak(
    text = "你好",
    callback = { samples ->
        // 处理音频数据
        // samples 是 FloatArray
        0 // 返回 0 继续，返回 1 停止
    }
)

// 停止朗读
ttsManager.stop()

// 获取说话人数量
val numSpeakers = ttsManager.getNumSpeakers()

// 获取采样率
val sampleRate = ttsManager.getSampleRate()
```

## API 文档

### TtsManager

主要的 TTS 管理类。

#### 构造函数

```kotlin
TtsManager(context: Context)
```

#### 方法

- `initialize(modelDir: String, modelType: String = "matcha")`
  - 初始化 TTS 模型
  - `modelDir`: 模型目录名称（相对于 `/data/data/包名/files/models/tts/`）
  - `modelType`: 模型类型，"vits" 或 "matcha"

- `speak(text: String, speed: Float = 0.8f, sid: Int = 0, callback: ((FloatArray) -> Unit)? = null)`
  - 朗读文本
  - `text`: 要朗读的文本
  - `speed`: 语速，范围 0.5-2.0
  - `sid`: 说话人ID
  - `callback`: 可选的音频回调函数

- `stop()`
  - 停止朗读

- `release()`
  - 释放资源

- `getNumSpeakers(): Int`
  - 获取说话人数量

- `getSampleRate(): Int`
  - 获取采样率

## 支持的模型

### Matcha 模型（推荐）

- **matcha-icefall-zh-baker**: 中文女声
- 下载地址: https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/matcha-icefall-zh-baker.tar.bz2

### VITS 模型

- **vits-melo-tts-zh_en**: 中英混合
- 下载地址: https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-melo-tts-zh_en.tar.bz2

## 注意事项

1. **模型文件位置**: 模型必须放在 `/data/data/你的包名/files/models/tts/` 目录
2. **权限**: 不需要额外的权限（音频播放是内置的）
3. **线程安全**: TtsManager 不是线程安全的，请在主线程使用
4. **资源释放**: 使用完毕后记得调用 `release()` 释放资源

## 常见问题

### Q: 如何修改音量？

A: TtsManager 已经将音量设置为最大。如果需要调整，可以使用 Android 的 AudioManager。

### Q: 如何更换模型？

A: 调用 `release()` 后，重新 `initialize()` 即可。

### Q: 支持多说话人吗？

A: 部分模型支持多说话人，可以通过 `getNumSpeakers()` 查询，通过 `speak()` 的 `sid` 参数切换。

## 示例项目

参考 `app` 模块中的 `MainActivity.kt` 查看完整示例。

## 许可证

本项目基于 sherpa-onnx，遵循其开源许可证。
