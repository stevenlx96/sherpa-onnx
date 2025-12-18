# Sherpa-ONNX 实时语音识别 AAR 使用文档

## 📖 目录

- [简介](#简介)
- [功能特性](#功能特性)
- [系统要求](#系统要求)
- [快速开始](#快速开始)
- [集成步骤](#集成步骤)
- [API 使用说明](#api-使用说明)
- [模型部署](#模型部署)
- [完整示例](#完整示例)
- [高级功能](#高级功能)
- [故障排查](#故障排查)
- [性能优化](#性能优化)
- [FAQ](#faq)

---

## 简介

Sherpa-ONNX 实时语音识别 AAR 是一个基于 sherpa-onnx 的 Android 语音识别库，提供：

- 🎙️ **实时流式语音识别**（边说边识别）
- 🔊 **唤醒词检测**（KWS，低功耗待机）
- ✂️ **VAD 智能断句**（基于语音段检测）
- 📝 **同音字自动纠正**（基于 replace.fst）
- 🔄 **自动休眠机制**（节省资源）
- 📦 **完全离线**（无需网络连接）

**包名**: `com.example.streamingasr`
**版本**: 1.0.0
**最低 Android SDK**: 24 (Android 7.0)
**目标 SDK**: 34 (Android 14)

---

## 功能特性

### 1. 实时流式识别
- 边说边识别，延迟低至 200ms
- 支持长时间连续识别（最长 10 秒/句）
- 自动处理音频缓冲和流控制

### 2. 唤醒词检测（KWS）
- 使用专用小模型（< 10MB），低功耗
- 支持自定义唤醒词列表
- 可调节检测灵敏度（threshold）

### 3. VAD 智能断句
- 基于 Silero VAD 模型
- 语音段队列机制（queue-based）
- 可配置静音时长判定（默认 1.0s）

### 4. 同音字纠正
- 基于 replace.fst 和 lexicon.txt
- 自动修正常见错误（如："在坐" → "在座"）
- 支持自定义纠正词典

### 5. 自动休眠
- 识别完成 5 秒无语音自动返回待机
- 自动释放 ASR 资源，保留 KWS
- 节省内存和电量

---

## 系统要求

| 项目 | 要求 |
|------|------|
| Android 版本 | ≥ Android 7.0 (API 24) |
| 架构支持 | arm64-v8a, armeabi-v7a |
| 权限 | RECORD_AUDIO（录音权限） |
| 磁盘空间 | 模型文件 ~100-500 MB |
| 内存 | 运行时 ~200-500 MB |

---

## 快速开始

### 1. 添加 AAR 到项目

将 `library-release.aar` 复制到你的项目 `app/libs/` 目录。

### 2. 配置 Gradle

在 `app/build.gradle.kts` 中添加：

```kotlin
dependencies {
    // AAR 文件
    implementation(files("libs/library-release.aar"))

    // 必需的外部依赖
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

### 3. 添加权限

在 `AndroidManifest.xml` 中添加：

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

### 4. 部署模型文件

将模型文件部署到设备：

```bash
# 方法 A：使用 adb（需要 root）
adb push models/ /sdcard/
adb shell
su
cp -r /sdcard/models /data/data/你的包名/files/

# 方法 B：首次运行时从 assets 复制
# 将模型放在 assets/models/ 目录，应用启动时复制到内部存储
```

---

## 集成步骤

### 步骤 1：初始化组件

```kotlin
import com.example.streamingasr.ModelManager
import com.example.streamingasr.AudioRecorder
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.KeywordSpotter

class MainActivity : AppCompatActivity() {
    private lateinit var modelManager: ModelManager
    private var recognizer: OnlineRecognizer? = null
    private var vad: Vad? = null
    private var keywordSpotter: KeywordSpotter? = null
    private var audioRecorder: AudioRecorder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化模型管理器
        modelManager = ModelManager(this)

        // 请求录音权限
        requestPermissions(
            arrayOf(Manifest.permission.RECORD_AUDIO),
            100
        )
    }
}
```

### 步骤 2：加载模型

```kotlin
lifecycleScope.launch(Dispatchers.IO) {
    // 检查是否有 KWS 模型
    val hasKeywords = modelManager.checkKwsExists()

    if (hasKeywords) {
        // KWS 模式：加载唤醒词识别器
        keywordSpotter = modelManager.createKeywordSpotter(
            keywordsFile = "keywords.txt",
            threshold = 0.5F,
            score = 1.0F
        )
    } else {
        // 直接识别模式：加载 ASR + VAD
        recognizer = modelManager.createOnlineRecognizer(
            modelType = ModelManager.ModelType.ZIPFORMER_TRANSDUCER
        )

        vad = modelManager.createVad(
            threshold = 0.5F,
            minSilenceDuration = 1.0F,  // 静音 1 秒算句子结束
            minSpeechDuration = 0.25F,
            maxSpeechDuration = 10.0F
        )
    }
}
```

### 步骤 3：启动识别

```kotlin
private fun startRecording() {
    // 创建音频录制器
    val cacheDir = File(filesDir, "audio_cache")
    audioRecorder = AudioRecorder(16000, cacheDir)

    // 创建识别流
    stream = recognizer?.createStream()

    // 开始录制
    audioRecorder?.startRecording(savePcm = true)

    // 启动识别任务
    startRecognitionTask()
}
```

### 步骤 4：处理识别结果

```kotlin
private fun startRecognitionTask() {
    lifecycleScope.launch(Dispatchers.IO) {
        while (isRecording) {
            // 读取音频数据
            val samples = audioRecorder?.readAudioData()

            if (samples != null && samples.isNotEmpty()) {
                // 送入 VAD
                vad?.acceptWaveform(samples)

                // 送入识别流
                stream?.acceptWaveform(samples, 16000)

                // 解码
                while (recognizer?.isReady(stream!!) == true) {
                    recognizer?.decode(stream!!)
                }

                // 获取结果
                val result = recognizer?.getResult(stream!!)
                val currentText = result?.text ?: ""

                // 检查是否断句
                if (vad?.empty() == false) {
                    val segment = vad?.front()
                    vad?.pop()

                    // 句子完成！
                    Log.i(TAG, "✓ 句子完成: $currentText")

                    // 🎯 这里可以发送给 LLM
                    sendToLLM(currentText)

                    // 重置流
                    recognizer?.reset(stream!!)
                    vad?.reset()
                }
            }
        }
    }
}
```

---

## API 使用说明

### ModelManager

模型管理器，负责加载和创建各种模型。

#### 构造函数

```kotlin
val modelManager = ModelManager(context: Context)
```

#### 方法

| 方法 | 说明 | 返回值 |
|------|------|--------|
| `getModelDir()` | 获取模型目录路径 | `File` |
| `checkModelExists(modelType)` | 检查 ASR 模型是否存在 | `Boolean` |
| `checkKwsExists()` | 检查 KWS 模型是否存在 | `Boolean` |
| `createOnlineRecognizer(modelType, numThreads)` | 创建在线识别器 | `OnlineRecognizer?` |
| `createVad(...)` | 创建 VAD 模型 | `Vad?` |
| `createKeywordSpotter(...)` | 创建唤醒词识别器 | `KeywordSpotter?` |

#### 示例

```kotlin
// 检查模型
if (!modelManager.checkModelExists(ModelManager.ModelType.ZIPFORMER_TRANSDUCER)) {
    Log.e(TAG, "模型不存在，路径: ${modelManager.getModelDir()}")
}

// 创建识别器
val recognizer = modelManager.createOnlineRecognizer(
    modelType = ModelManager.ModelType.ZIPFORMER_TRANSDUCER,
    numThreads = 4  // 使用 4 个线程
)
```

### AudioRecorder

音频录制器，负责录制和缓存音频数据。

#### 构造函数

```kotlin
val audioRecorder = AudioRecorder(
    sampleRate: Int = 16000,
    cacheDir: File
)
```

#### 方法

| 方法 | 说明 | 返回值 |
|------|------|--------|
| `startRecording(savePcm, cacheInMemory)` | 开始录制 | `Boolean` |
| `stopRecording()` | 停止录制 | `Unit` |
| `readAudioData()` | 读取音频数据（阻塞） | `FloatArray?` |
| `getCurrentPcmFile()` | 获取当前 PCM 文件 | `File?` |
| `getCacheSize()` | 获取缓存大小 | `Long` |
| `clearPcmCache()` | 清除内存缓存 | `Unit` |
| `deleteAllCacheFiles()` | 删除所有缓存文件 | `Unit` |

#### 示例

```kotlin
// 开始录制并保存 PCM
audioRecorder.startRecording(savePcm = true, cacheInMemory = true)

// 读取音频数据
val samples = audioRecorder.readAudioData()  // 返回 FloatArray

// 停止录制
audioRecorder.stopRecording()

// 获取录制的 PCM 文件
val pcmFile = audioRecorder.getCurrentPcmFile()
Log.i(TAG, "PCM 文件: ${pcmFile?.absolutePath}")
```

### OnlineRecognizer

在线识别器（来自 sherpa-onnx）。

#### 方法

| 方法 | 说明 |
|------|------|
| `createStream()` | 创建识别流 |
| `isReady(stream)` | 检查是否准备好解码 |
| `decode(stream)` | 解码一帧 |
| `getResult(stream)` | 获取识别结果 |
| `isEndpoint(stream)` | 检查是否到达端点（断句） |
| `reset(stream)` | 重置流（用于下一句） |
| `release()` | 释放资源 |

### Vad

语音活动检测（来自 sherpa-onnx）。

#### 方法

| 方法 | 说明 |
|------|------|
| `acceptWaveform(samples)` | 送入音频数据 |
| `empty()` | 检查队列是否为空 |
| `front()` | 获取队列头部的语音段 |
| `pop()` | 移除队列头部的语音段 |
| `reset()` | 重置 VAD 状态 |
| `release()` | 释放资源 |

#### VAD 断句机制

```kotlin
// VAD 内部维护一个语音段队列
vad?.acceptWaveform(samples)

// 检查是否有完整的语音段
if (vad?.empty() == false) {
    val segment = vad?.front()  // 获取语音段
    vad?.pop()  // 从队列移除

    // 触发断句
    Log.i(TAG, "检测到完整语音段，开始=${segment?.start}")
    handleSentenceComplete()
}
```

### KeywordSpotter

唤醒词识别器（来自 sherpa-onnx）。

#### 方法

| 方法 | 说明 |
|------|------|
| `createStream()` | 创建 KWS 流 |
| `isReady(stream)` | 检查是否准备好解码 |
| `decode(stream)` | 解码一帧 |
| `getResult(stream)` | 获取检测结果 |
| `reset(stream)` | 重置流 |
| `release()` | 释放资源 |

---

## 模型部署

### 模型文件结构

模型文件需要部署到设备的 `/data/data/你的包名/files/models/` 目录。

```
/data/data/com.example.yourapp/files/models/
├── asr/                              # ASR 模型目录
│   └── sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23/
│       ├── encoder-epoch-99-avg-1.onnx
│       ├── decoder-epoch-99-avg-1.onnx
│       ├── joiner-epoch-99-avg-1.onnx
│       ├── tokens.txt
│       └── replace.fst              # 同音字纠正（可选）
│
├── vad/                              # VAD 模型目录
│   └── silero_vad.onnx
│
└── kws/                              # KWS 模型目录（可选）
    └── sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01/
        ├── encoder-epoch-12-avg-2-chunk-16-left-64.onnx
        ├── decoder-epoch-12-avg-2-chunk-16-left-64.onnx
        ├── joiner-epoch-12-avg-2-chunk-16-left-64.onnx
        ├── tokens.txt
        └── keywords.txt              # 唤醒词列表
```

### 部署方法

#### 方法 1：使用 adb（需要 root）

```bash
# 1. 推送模型到 SD 卡
adb push models/ /sdcard/

# 2. 进入设备 shell
adb shell

# 3. 获取 root 权限
su

# 4. 复制到应用内部存储
cp -r /sdcard/models /data/data/com.example.yourapp/files/

# 5. 修改权限
chmod -R 755 /data/data/com.example.yourapp/files/models/
chown -R u0_a123:u0_a123 /data/data/com.example.yourapp/files/models/
```

#### 方法 2：从 assets 复制（推荐）

1. 将模型文件放在 `app/src/main/assets/models/` 目录
2. 应用启动时复制到内部存储：

```kotlin
private fun copyModelsFromAssets() {
    lifecycleScope.launch(Dispatchers.IO) {
        val modelDir = File(filesDir, "models")
        if (modelDir.exists()) {
            return@launch  // 已存在，不重复复制
        }

        try {
            copyAssetFolder("models", modelDir.absolutePath)
            Log.i(TAG, "模型复制成功: ${modelDir.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "模型复制失败", e)
        }
    }
}

private fun copyAssetFolder(srcName: String, dstPath: String) {
    val assetManager = assets
    val files = assetManager.list(srcName) ?: return

    val dstDir = File(dstPath)
    if (!dstDir.exists()) {
        dstDir.mkdirs()
    }

    for (filename in files) {
        val srcPath = "$srcName/$filename"
        val dstFilePath = "$dstPath/$filename"

        if (assetManager.list(srcPath)?.isNotEmpty() == true) {
            // 是目录，递归复制
            copyAssetFolder(srcPath, dstFilePath)
        } else {
            // 是文件，直接复制
            assetManager.open(srcPath).use { input ->
                File(dstFilePath).outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}
```

### 模型下载地址

- **ASR 模型**: [sherpa-onnx 官方模型库](https://github.com/k2-fsa/sherpa-onnx/releases)
- **VAD 模型**: [Silero VAD](https://github.com/snakers4/silero-vad)
- **KWS 模型**: [sherpa-onnx KWS 模型](https://github.com/k2-fsa/sherpa-onnx/releases/tag/kws-models)

推荐模型：
- ASR: `sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23`（中文，14MB）
- VAD: `silero_vad.onnx`（通用，2MB）
- KWS: `sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01`（中文，3.3MB）

---

## 完整示例

这里提供一个完整的 Activity 示例，包含所有功能：

```kotlin
package com.example.yourapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.streamingasr.AudioRecorder
import com.example.streamingasr.ModelManager
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.KeywordSpotter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ASRDemo"
        private const val SAMPLE_RATE = 16000
        private const val PERMISSION_REQUEST_CODE = 100
    }

    // UI 组件
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvResult: TextView

    // 核心组件
    private lateinit var modelManager: ModelManager
    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    private var vad: Vad? = null
    private var keywordSpotter: KeywordSpotter? = null
    private var kwsStream: OnlineStream? = null
    private var audioRecorder: AudioRecorder? = null

    // 状态
    private var isRecording = false
    private var isInKwsMode = false
    private var recognitionJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化 UI
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        tvStatus = findViewById(R.id.tvStatus)
        tvResult = findViewById(R.id.tvResult)

        // 初始化模型管理器
        modelManager = ModelManager(this)

        // 设置按钮事件
        btnStart.setOnClickListener { startRecognition() }
        btnStop.setOnClickListener { stopRecognition() }

        // 检查权限
        checkPermission()
    }

    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_CODE
            )
        } else {
            initializeModels()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeModels()
            } else {
                Toast.makeText(this, "需要录音权限", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initializeModels() {
        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                tvStatus.text = "正在加载模型..."
            }

            // 检查 KWS
            if (modelManager.checkKwsExists()) {
                keywordSpotter = modelManager.createKeywordSpotter(
                    keywordsFile = "keywords.txt",
                    threshold = 0.5F,
                    score = 1.0F
                )
                isInKwsMode = true
            }

            // 加载 ASR 和 VAD
            recognizer = modelManager.createOnlineRecognizer(
                modelType = ModelManager.ModelType.ZIPFORMER_TRANSDUCER
            )

            vad = modelManager.createVad(
                threshold = 0.5F,
                minSilenceDuration = 1.0F,
                minSpeechDuration = 0.25F,
                maxSpeechDuration = 10.0F
            )

            withContext(Dispatchers.Main) {
                if (recognizer != null) {
                    val modeText = if (isInKwsMode) "KWS + ASR 模式" else "直接识别模式"
                    tvStatus.text = "✓ 模型加载成功\n模式: $modeText\n点击开始"
                } else {
                    tvStatus.text = "❌ 模型加载失败\n请检查模型文件"
                }
            }
        }
    }

    private fun startRecognition() {
        if (recognizer == null) {
            Toast.makeText(this, "模型未就绪", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val cacheDir = File(filesDir, "audio_cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            audioRecorder = AudioRecorder(SAMPLE_RATE, cacheDir)
            stream = recognizer?.createStream()

            if (audioRecorder?.startRecording(savePcm = true) == true) {
                isRecording = true
                tvStatus.text = "🎙️ 正在识别..."
                tvResult.text = ""
                startRecognitionTask()
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动失败", e)
            Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecognition() {
        isRecording = false
        recognitionJob?.cancel()
        audioRecorder?.stopRecording()
        stream?.release()
        stream = null

        tvStatus.text = "已停止"
    }

    private fun startRecognitionTask() {
        recognitionJob = lifecycleScope.launch(Dispatchers.IO) {
            var lastText = ""
            val completedSentences = mutableListOf<String>()

            try {
                while (isActive && isRecording) {
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

                        // 获取结果
                        val result = recognizer?.getResult(stream!!)
                        val currentText = result?.text ?: ""

                        // 检查 VAD 断句
                        if (vad?.empty() == false) {
                            val segment = vad?.front()
                            vad?.pop()

                            if (currentText.isNotEmpty()) {
                                // 句子完成
                                completedSentences.add(currentText)
                                Log.i(TAG, "✓ 句子完成: $currentText")

                                withContext(Dispatchers.Main) {
                                    tvResult.text = completedSentences.joinToString("\n") { "✓ $it" }
                                }

                                // 🎯 这里可以发送给 LLM
                                // sendToLLM(currentText)

                                // 重置
                                recognizer?.reset(stream!!)
                                vad?.reset()
                                lastText = ""
                            }
                        } else if (currentText != lastText && currentText.length >= 3) {
                            // 实时更新
                            withContext(Dispatchers.Main) {
                                val displayText = completedSentences.joinToString("\n") { "✓ $it" } +
                                        if (completedSentences.isNotEmpty()) "\n" else "" +
                                        "⏳ $currentText"
                                tvResult.text = displayText
                            }
                            lastText = currentText
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "识别错误", e)
                withContext(Dispatchers.Main) {
                    tvStatus.text = "❌ 错误: ${e.message}"
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecognition()
        recognizer?.release()
        vad?.release()
        keywordSpotter?.release()
    }
}
```

### 布局文件示例

`res/layout/activity_main.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="实时语音识别"
        android:textSize="24sp"
        android:textStyle="bold"
        android:gravity="center"
        android:paddingBottom="16dp" />

    <TextView
        android:id="@+id/tvStatus"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="初始化中..."
        android:padding="12dp"
        android:background="@android:color/darker_gray"
        android:textColor="@android:color/white"
        android:layout_marginBottom="16dp" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:layout_marginBottom="16dp">

        <Button
            android:id="@+id/btnStart"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="开始识别"
            android:layout_marginEnd="8dp" />

        <Button
            android:id="@+id/btnStop"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="停止识别" />
    </LinearLayout>

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="识别结果:"
        android:textStyle="bold"
        android:paddingBottom="8dp" />

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:background="@android:color/white"
        android:padding="12dp">

        <TextView
            android:id="@+id/tvResult"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textSize="16sp"
            android:textColor="@android:color/black"
            android:hint="识别结果会显示在这里..." />
    </ScrollView>

</LinearLayout>
```

---

## 高级功能

### 1. KWS 唤醒词检测

使用唤醒词实现低功耗待机：

```kotlin
private fun startKwsMonitoring() {
    kwsStream = keywordSpotter?.createStream()

    // 开始录音（不保存，不缓存）
    audioRecorder?.startRecording(savePcm = false, cacheInMemory = false)

    lifecycleScope.launch(Dispatchers.IO) {
        while (isActive && isInKwsMode) {
            val samples = audioRecorder?.readAudioData()

            if (samples != null) {
                kwsStream?.acceptWaveform(samples, SAMPLE_RATE)

                while (keywordSpotter?.isReady(kwsStream!!) == true) {
                    keywordSpotter?.decode(kwsStream!!)
                }

                val result = keywordSpotter?.getResult(kwsStream!!)
                if (result != null && result.keyword.isNotEmpty()) {
                    Log.i(TAG, "🔊 检测到唤醒词: ${result.keyword}")

                    // 切换到 ASR 模式
                    switchToAsrMode()

                    keywordSpotter?.reset(kwsStream!!)
                }
            }
        }
    }
}

private fun switchToAsrMode() {
    // 停止 KWS
    kwsStream?.release()
    kwsStream = null

    // 启动 ASR
    stream = recognizer?.createStream()
    startRecognitionTask()
}
```

### 2. 自定义唤醒词

编辑 `keywords.txt` 文件：

```
你好小智
小爱同学
嗨小度
```

### 3. 同音字纠正

使用 `replace.fst` 和 `lexicon.txt` 自动纠正：

**replace.fst**（OpenFST 格式）:
```
0 1 在坐 在座
0 1 在做 在做
1
```

**lexicon.txt**:
```
在坐 z ai4 z uo4
在座 z ai4 z uo4
```

### 4. 自动休眠机制

识别完成后自动返回待机：

```kotlin
private var lastSpeechTime = 0L
private val IDLE_TIMEOUT_MS = 5000L  // 5 秒无语音

// 在识别循环中
if (currentText.isNotEmpty()) {
    lastSpeechTime = System.currentTimeMillis()
}

// 检查空闲时间
val idleTime = System.currentTimeMillis() - lastSpeechTime
if (idleTime > IDLE_TIMEOUT_MS && completedSentences.isNotEmpty()) {
    Log.i(TAG, "💤 空闲超时，返回待机")

    // 释放 ASR 资源
    stream?.release()
    recognizer?.release()
    vad?.release()

    // 返回 KWS 模式
    if (keywordSpotter != null) {
        startKwsMonitoring()
    }
}
```

### 5. 导出 PCM 音频

```kotlin
val pcmFile = audioRecorder?.getCurrentPcmFile()
Log.i(TAG, "PCM 文件: ${pcmFile?.absolutePath}")

// 转换为 WAV 格式
fun pcmToWav(pcmFile: File, wavFile: File) {
    val pcmData = pcmFile.readBytes()
    val sampleRate = 16000
    val channels = 1
    val bitsPerSample = 16

    wavFile.outputStream().use { output ->
        // 写入 WAV 头
        output.write("RIFF".toByteArray())
        output.writeInt(36 + pcmData.size)
        output.write("WAVE".toByteArray())
        output.write("fmt ".toByteArray())
        output.writeInt(16)  // fmt chunk size
        output.writeShort(1)  // PCM format
        output.writeShort(channels)
        output.writeInt(sampleRate)
        output.writeInt(sampleRate * channels * bitsPerSample / 8)
        output.writeShort(channels * bitsPerSample / 8)
        output.writeShort(bitsPerSample)
        output.write("data".toByteArray())
        output.writeInt(pcmData.size)
        output.write(pcmData)
    }
}
```

---

## 故障排查

### 问题 1: 模型加载失败

**症状**: `createOnlineRecognizer()` 返回 `null`

**原因**: 模型文件不存在或路径错误

**解决方案**:
```kotlin
val modelDir = modelManager.getModelDir()
Log.i(TAG, "模型路径: ${modelDir.absolutePath}")

// 检查文件
val asrDir = File(modelDir, "asr")
if (!asrDir.exists()) {
    Log.e(TAG, "ASR 目录不存在: ${asrDir.absolutePath}")
}
```

### 问题 2: 录音权限被拒绝

**症状**: `startRecording()` 返回 `false`

**原因**: 未授予 RECORD_AUDIO 权限

**解决方案**:
```kotlin
if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
    != PackageManager.PERMISSION_GRANTED) {
    ActivityCompat.requestPermissions(
        this,
        arrayOf(Manifest.permission.RECORD_AUDIO),
        100
    )
}
```

### 问题 3: VAD 不断句

**症状**: `vad?.empty()` 始终为 `true`

**原因**: `minSilenceDuration` 设置过长

**解决方案**:
```kotlin
// 减小静音时长判定
vad = modelManager.createVad(
    threshold = 0.5F,
    minSilenceDuration = 0.5F,  // 从 1.0s 改为 0.5s
    minSpeechDuration = 0.25F,
    maxSpeechDuration = 10.0F
)
```

### 问题 4: 识别结果为空

**症状**: `result?.text` 始终为空

**原因**:
1. 音频质量差
2. 模型不匹配（如英文模型识别中文）
3. 音频格式错误

**解决方案**:
```kotlin
// 检查音频格式
Log.i(TAG, "Sample rate: $SAMPLE_RATE")
Log.i(TAG, "Samples length: ${samples.size}")

// 确认模型类型
val modelType = ModelManager.ModelType.ZIPFORMER_TRANSDUCER
Log.i(TAG, "Model type: $modelType")
```

### 问题 5: 内存溢出 (OOM)

**症状**: 应用崩溃，日志显示 OutOfMemoryError

**原因**:
1. 同时加载多个大模型
2. 音频缓存过大

**解决方案**:
```kotlin
// 使用完及时释放
recognizer?.release()
vad?.release()
keywordSpotter?.release()

// 清除音频缓存
audioRecorder?.clearPcmCache()
audioRecorder?.deleteAllCacheFiles()

// 使用小模型
// ASR: 14M 模型而非 500M 模型
// KWS: 3.3M 模型
```

### 问题 6: so 库加载失败

**症状**: `UnsatisfiedLinkError`

**原因**: AAR 中缺少 .so 文件

**解决方案**:
```bash
# 检查 AAR 内容
unzip -l library-release.aar | grep .so

# 应该看到：
# jni/arm64-v8a/libsherpa-onnx-jni.so
# jni/armeabi-v7a/libsherpa-onnx-jni.so

# 如果没有，需要重新打包 AAR
# 将 .so 文件放到 library/src/main/jniLibs/
cp -r app/src/main/jniLibs/* library/src/main/jniLibs/
./gradlew :library:assembleRelease
```

---

## 性能优化

### 1. 线程数优化

```kotlin
val numThreads = Runtime.getRuntime().availableProcessors()
recognizer = modelManager.createOnlineRecognizer(
    modelType = ModelManager.ModelType.ZIPFORMER_TRANSDUCER,
    numThreads = numThreads.coerceIn(2, 4)  // 限制 2-4 个线程
)
```

### 2. 音频缓冲优化

```kotlin
// 不缓存到内存（节省内存）
audioRecorder?.startRecording(savePcm = false, cacheInMemory = false)

// 定期清理缓存
lifecycleScope.launch {
    while (true) {
        delay(60000)  // 每分钟
        audioRecorder?.clearPcmCache()
    }
}
```

### 3. 模型选择

| 模型大小 | 内存占用 | 识别速度 | 准确率 | 推荐场景 |
|---------|---------|---------|--------|---------|
| 14M | ~200MB | 快 | 中 | 移动设备、实时性要求高 |
| 140M | ~500MB | 中 | 高 | 高端设备、准确率要求高 |
| 500M | ~1GB | 慢 | 很高 | 离线场景、不限资源 |

**推荐**: 使用 14M 模型（`sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23`）

### 4. VAD 参数调优

```kotlin
// 快速响应（适合短句）
vad = modelManager.createVad(
    threshold = 0.3F,           // 降低阈值，更敏感
    minSilenceDuration = 0.5F,  // 短静音时长
    minSpeechDuration = 0.2F,
    maxSpeechDuration = 5.0F
)

// 稳定识别（适合长句）
vad = modelManager.createVad(
    threshold = 0.6F,           // 提高阈值，减少误判
    minSilenceDuration = 1.5F,  // 长静音时长
    minSpeechDuration = 0.3F,
    maxSpeechDuration = 15.0F
)
```

---

## FAQ

### Q1: AAR 支持哪些 Android 版本？

**A**: 最低支持 Android 7.0 (API 24)，推荐 Android 8.0+ (API 26+)

### Q2: AAR 文件多大？

**A**:
- 不含 .so 文件：~100KB
- 含 .so 文件：~50MB（arm64-v8a + armeabi-v7a）

### Q3: 模型文件可以放在 assets 吗？

**A**: 可以，但不推荐。模型文件较大（100MB+），会增加 APK 体积。推荐首次运行时从网络下载或使用 adb 部署。

### Q4: 支持离线使用吗？

**A**: 完全支持。所有识别都在本地进行，无需网络连接。

### Q5: 识别延迟多少？

**A**:
- 实时识别延迟：200-500ms
- VAD 断句延迟：1-2s（取决于 `minSilenceDuration`）

### Q6: 可以同时识别多路音频吗？

**A**: 可以，但需要创建多个 `OnlineRecognizer` 实例。注意内存和性能限制。

### Q7: 支持哪些语言？

**A**: 取决于模型。sherpa-onnx 支持中文、英文、日文、韩文等多种语言。请使用对应语言的模型。

### Q8: 如何提高识别准确率？

**A**:
1. 使用更大的模型（如 140M）
2. 优化 VAD 参数
3. 添加自定义词典（replace.fst）
4. 提高音频质量（降噪）

### Q9: 可以在后台运行吗？

**A**: 可以，但需要使用 Service 并申请前台服务权限。

```kotlin
class AsrService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 显示前台通知
        val notification = createNotification()
        startForeground(1, notification)

        // 启动识别
        startRecognition()

        return START_STICKY
    }
}
```

### Q10: 如何集成到现有项目？

**A**:
1. 复制 AAR 文件到 `app/libs/`
2. 添加 Gradle 依赖
3. 部署模型文件
4. 参考示例代码集成

---

## 许可证

本 AAR 基于 [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) 项目，遵循 Apache 2.0 许可证。

---

## 技术支持

- **项目地址**: [GitHub 仓库链接]
- **问题反馈**: [Issues 页面]
- **文档更新**: 2024-12-18

---

**版本**: 1.0.0
**最后更新**: 2024-12-18
