# StreamingASR AAR Demo

这是一个演示如何使用 StreamingASR AAR 包的独立应用程序。

## 项目结构

```
StreamingASRDemo/
├── app/
│   ├── libs/                    # 放置 AAR 文件的目录
│   │   └── .gitkeep            # (将 library-release.aar 放在这里)
│   ├── src/
│   │   └── main/
│   │       ├── java/com/example/aar/demo/
│   │       │   └── MainActivity.kt
│   │       ├── res/
│   │       └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## 使用步骤

### 1. 放置 AAR 文件

将构建好的 `library-release.aar` 文件复制到 `app/libs/` 目录：

```bash
cp /path/to/library-release.aar StreamingASRDemo/app/libs/
```

### 2. 准备模型文件

AAR 包不包含模型文件，需要手动部署模型到设备上。有以下几种方式：

#### 方式 1: 使用 adb push（推荐用于测试）

```bash
# 将模型文件推送到应用的私有目录
adb push /path/to/models /data/data/com.example.aar.demo/files/models/
```

示例（Transducer 模型）：
```bash
adb push encoder-epoch-99-avg-1.onnx /data/data/com.example.aar.demo/files/models/
adb push decoder-epoch-99-avg-1.onnx /data/data/com.example.aar.demo/files/models/
adb push joiner-epoch-99-avg-1.onnx /data/data/com.example.aar.demo/files/models/
adb push tokens.txt /data/data/com.example.aar.demo/files/models/
```

#### 方式 2: 应用内下载

在应用代码中添加网络下载逻辑，首次启动时从服务器下载模型到 `/data/data/com.example.aar.demo/files/models/`

#### 方式 3: 从 assets 复制

将模型文件放在 `app/src/main/assets/models/`，应用启动时复制到私有目录。

### 3. 构建和运行

在 Android Studio 中：
1. 打开 StreamingASRDemo 项目
2. 确保 `app/libs/library-release.aar` 存在
3. 同步 Gradle
4. 连接 Android 设备或启动模拟器
5. 运行应用

或使用命令行：
```bash
./gradlew assembleDebug
./gradlew installDebug
```

## 功能说明

### 主要功能

- **实时语音识别**: 点击"开始识别"按钮开始录音和实时识别
- **流式结果显示**: 识别结果实时更新显示
- **音频缓存**: 录音自动保存为 PCM 格式，可用于调试
- **清除缓存**: 清除已保存的音频文件和识别结果

### 使用的 AAR 组件

```kotlin
// 从 AAR 包导入的核心类
import com.example.streamingasr.AudioRecorder
import com.example.streamingasr.ModelManager
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineStream
```

### 核心代码示例

```kotlin
// 初始化模型管理器
modelManager = ModelManager(this)

// 自动检测并加载模型
recognizer = modelManager.createOnlineRecognizerAuto()

// 创建音频录制器
audioRecorder = AudioRecorder(SAMPLE_RATE, cacheDir)

// 开始录音
audioRecorder.startRecording(savePcm = true)

// 创建识别流
stream = recognizer?.createStream()

// 识别音频
stream?.acceptWaveform(samples, SAMPLE_RATE)
recognizer?.decode(stream)
val result = recognizer?.getResult(stream)
```

## 支持的模型类型

AAR 包自动检测以下模型类型：

1. **Transducer 模型**
   - encoder-*.onnx
   - decoder-*.onnx
   - joiner-*.onnx
   - tokens.txt

2. **Paraformer 模型**
   - paraformer-*.onnx 或 model.onnx
   - tokens.txt

3. **CTC 模型**
   - model.onnx
   - tokens.txt

## 权限要求

应用需要以下权限：
- `RECORD_AUDIO`: 录音权限（运行时请求）
- `INTERNET`: 网络权限（如果需要下载模型）

## 技术要求

- **minSdk**: 24 (Android 7.0)
- **targetSdk**: 34 (Android 14)
- **支持架构**: arm64-v8a, armeabi-v7a

## 故障排除

### 模型加载失败

如果看到"❌ 模型加载失败"，请检查：
1. 模型文件是否已推送到 `/data/data/com.example.aar.demo/files/models/`
2. 模型文件名称是否正确
3. 是否包含必需的 tokens.txt 文件

使用以下命令检查：
```bash
adb shell ls -la /data/data/com.example.aar.demo/files/models/
```

### 录音权限

首次启动时会请求录音权限，必须授权才能使用语音识别功能。

### AAR 文件缺失

如果构建失败提示找不到 AAR，确保 `app/libs/library-release.aar` 文件存在。

## 参考资料

- [sherpa-onnx GitHub](https://github.com/k2-fsa/sherpa-onnx)
- [模型下载](https://github.com/k2-fsa/sherpa-onnx/releases)

## License

本 Demo 应用仅用于演示目的。
