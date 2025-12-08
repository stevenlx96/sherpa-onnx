# Demo App - AAR 功能测试

这是一个测试 StreamingASR AAR 功能的 Demo 应用。

## 📋 项目说明

此 Demo 演示如何使用 `library` module（模拟使用 AAR 的效果）：

- ✅ 使用 `ModelManager` 从数据目录加载模型
- ✅ 使用 `AudioRecorder` 录制音频
- ✅ 实现实时流式语音识别
- ✅ 功能与原 app 完全一致

## 🎯 目的

1. **开发测试**：在开发过程中直接依赖 `library` module，无需每次打包 AAR
2. **功能验证**：确保 AAR 打包后的功能正常
3. **使用示例**：展示如何集成和使用 AAR

## 🏗️ 依赖配置

### 当前配置（开发模式）

```kotlin
dependencies {
    // 直接依赖 library module
    implementation(project(":library"))

    // 必需的外部依赖
    implementation("androidx.core:core-ktx:1.12.0")
    // ...
}
```

### 使用 AAR 时的配置

如果要使用打包好的 AAR，修改为：

```kotlin
dependencies {
    // 使用 AAR 文件
    implementation(files("libs/library-release.aar"))

    // 必需的外部依赖（保持不变）
    implementation("androidx.core:core-ktx:1.12.0")
    // ...
}
```

## 🚀 运行 Demo

### 1. 准备模型文件

模型文件需要放在：
```
/data/data/com.example.demo.streamingasr/files/models/
```

使用 adb 推送模型：

```bash
# 创建目录
adb shell mkdir -p /data/data/com.example.demo.streamingasr/files/models

# 推送模型文件
adb push encoder-epoch-99-avg-1.onnx /data/data/com.example.demo.streamingasr/files/models/
adb push decoder-epoch-99-avg-1.onnx /data/data/com.example.demo.streamingasr/files/models/
adb push joiner-epoch-99-avg-1.onnx /data/data/com.example.demo.streamingasr/files/models/
adb push tokens.txt /data/data/com.example.demo.streamingasr/files/models/

# 验证
adb shell ls -lh /data/data/com.example.demo.streamingasr/files/models/
```

### 2. 运行应用

在 Android Studio 中：
1. 同步项目：Sync Project with Gradle Files
2. 选择 `demo` 配置
3. 运行到设备

## 📦 代码结构

```
demo/
├── src/main/
│   ├── java/com/example/demo/
│   │   └── MainActivity.kt          # 主界面（使用 AAR 中的类）
│   ├── res/
│   │   ├── layout/
│   │   │   └── activity_main.xml   # 界面布局
│   │   └── values/
│   │       ├── strings.xml
│   │       ├── colors.xml
│   │       └── themes.xml
│   └── AndroidManifest.xml
└── build.gradle.kts                 # 依赖配置
```

## 💡 关键代码

### 使用 ModelManager 加载模型

```kotlin
// 创建 ModelManager（来自 AAR）
val modelManager = ModelManager(context)

// 自动检测并加载模型
val recognizer = modelManager.createOnlineRecognizerAuto()

if (recognizer != null) {
    // 模型加载成功，开始识别
} else {
    // 模型加载失败，显示说明
    val instructions = modelManager.getModelDownloadInstructions()
}
```

### 使用 AudioRecorder 录制音频

```kotlin
// 创建 AudioRecorder（来自 AAR）
val audioRecorder = AudioRecorder(sampleRate, cacheDir)

// 开始录制
audioRecorder.startRecording(savePcm = true)

// 读取音频数据
val samples = audioRecorder.readAudioData()

// 停止录制
audioRecorder.stopRecording()
```

## 🔍 与原 app 的区别

| 项目 | 原 app | Demo |
|------|--------|------|
| 包名 | com.example.streamingasr | com.example.demo.streamingasr |
| 依赖方式 | 直接包含源代码 | 依赖 library module（模拟 AAR） |
| 功能 | 完全相同 | 完全相同 |
| 模型路径 | /data/data/com.example.streamingasr/files/models/ | /data/data/com.example.demo.streamingasr/files/models/ |

## ✅ 功能清单

- [x] 实时流式语音识别
- [x] 自动检测模型文件
- [x] 支持 Transducer/Paraformer/CTC 模型
- [x] PCM 音频缓存
- [x] 句子断句（标点符号 + 静音检测）
- [x] 自我修正检测
- [x] 权限管理
- [x] 错误处理和提示

## 📝 注意事项

1. **模型文件**：Demo 使用独立的应用包名，模型文件路径不同于原 app
2. **依赖方式**：开发时依赖 `library` module，实际使用时改为 AAR 文件
3. **权限**：需要录音权限（RECORD_AUDIO）

## 🎓 学习要点

通过此 Demo 可以学习：

1. 如何集成和使用 StreamingASR AAR
2. 如何使用 ModelManager 管理模型
3. 如何实现实时流式语音识别
4. 如何处理音频录制和识别结果

---

**运行 Demo 前，记得先推送模型文件到设备！** 🚀
