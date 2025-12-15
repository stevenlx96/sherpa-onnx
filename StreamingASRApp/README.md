# StreamingASR - 实时语音识别 Android 应用

基于 [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) 的 Android 实时流式语音识别应用。

## ✨ 功能特性

- ✅ **实时流式识别** - 边说边识别，低延迟（~100-300ms）
- ✅ **智能断句** - VAD 语音活动检测，0.3秒静音断句
- ✅ **唤醒词检测** - KWS 模式，说"你好小智"唤醒
- ✅ **同音字纠正** - HomophoneReplacer 自动纠错（如 "在坐" → "在座"）
- ✅ **PCM 音频缓存** - 自动保存录音，可导出 WAV
- ✅ **双工作模式** - KWS 唤醒模式 / 直接识别模式

## 📋 技术规格

- **Target SDK**: 34 (Android 14)
- **Min SDK**: 24 (Android 7.0)
- **Java**: 17
- **音频**: PCM 16-bit, 16kHz, 单声道

## 🚀 快速开始

### 1. 下载 JNI 库

```bash
cd StreamingASRApp
./download-libs.sh
```

### 2. 下载模型文件

**必需：ASR 模型**（中英文双语，推荐）

```bash
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2
tar xvf sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2
```

需要的文件：
- encoder-epoch-99-avg-1.onnx
- decoder-epoch-99-avg-1.onnx
- joiner-epoch-99-avg-1.onnx
- tokens.txt

**可选：VAD 模型**（推荐，改善断句）

```bash
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx
```

**可选：KWS 模型**（启用唤醒词"你好小智"）

```bash
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01.tar.bz2
tar xvf sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01.tar.bz2
```

**可选：同音字替换**（自动纠正常见错误）

```bash
./download-homophone-files.sh
```

或手动下载：
```bash
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/hr-files/lexicon.txt
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/hr-files/replace.fst
```

### 3. 编译安装应用

```bash
./gradlew installDebug
```

### 4. 推送模型到设备

模型目录：`/data/data/com.example.streamingasr/files/models/`

```bash
# 推送 ASR 模型到临时目录
adb push encoder-epoch-99-avg-1.onnx /sdcard/
adb push decoder-epoch-99-avg-1.onnx /sdcard/
adb push joiner-epoch-99-avg-1.onnx /sdcard/
adb push tokens.txt /sdcard/

# 移动到应用目录 (无需 root)
adb shell run-as com.example.streamingasr mkdir -p files/models/asr
adb shell run-as com.example.streamingasr cp /sdcard/encoder-epoch-99-avg-1.onnx files/models/asr/
adb shell run-as com.example.streamingasr cp /sdcard/decoder-epoch-99-avg-1.onnx files/models/asr/
adb shell run-as com.example.streamingasr cp /sdcard/joiner-epoch-99-avg-1.onnx files/models/asr/
adb shell run-as com.example.streamingasr cp /sdcard/tokens.txt files/models/asr/

# 可选：推送 VAD 模型
adb push silero_vad.onnx /sdcard/
adb shell run-as com.example.streamingasr mkdir -p files/models/vad
adb shell run-as com.example.streamingasr cp /sdcard/silero_vad.onnx files/models/vad/

# 可选：推送 KWS 模型（唤醒词）
adb push keywords.txt /sdcard/
adb push encoder-epoch-12-avg-2-chunk-16-left-64.onnx /sdcard/
adb push decoder-epoch-12-avg-2-chunk-16-left-64.onnx /sdcard/
adb push joiner-epoch-12-avg-2-chunk-16-left-64.onnx /sdcard/
adb shell run-as com.example.streamingasr mkdir -p files/models/kws
adb shell run-as com.example.streamingasr cp /sdcard/keywords.txt files/models/kws/
adb shell run-as com.example.streamingasr cp /sdcard/*epoch-12*.onnx files/models/kws/

# 可选：推送同音字替换文件
adb push lexicon.txt /sdcard/
adb push replace.fst /sdcard/
adb shell run-as com.example.streamingasr cp /sdcard/lexicon.txt files/models/asr/
adb shell run-as com.example.streamingasr cp /sdcard/replace.fst files/models/asr/

# 清理临时文件
adb shell rm /sdcard/*.onnx /sdcard/*.txt /sdcard/*.fst
```

### 5. 使用应用

1. 打开应用，授予录音权限
2. 等待模型加载成功

**KWS 唤醒模式**（如果有 keywords.txt）：
- 点击"开始监听"进入待机
- 说"你好小智"唤醒
- 系统自动开始识别
- 5 秒无语音自动休眠

**直接识别模式**（无 keywords.txt）：
- 点击"开始识别"
- 直接说话，实时显示结果
- 点击"停止识别"结束

## 📁 目录结构

```
StreamingASRApp/
├── app/                          # 主应用
│   └── src/main/
│       ├── java/com/
│       │   ├── example/streamingasr/
│       │   │   ├── MainActivity.kt      # 主Activity
│       │   │   ├── AudioRecorder.kt     # 音频录制（PCM缓存）
│       │   │   └── ModelManager.kt      # 模型管理
│       │   └── k2fsa/sherpa/onnx/       # Sherpa-ONNX API
│       ├── res/                         # 资源文件
│       └── jniLibs/                     # JNI 库（需下载）
├── library/                      # AAR 库模块
├── download-libs.sh              # 下载 JNI 库脚本
├── download-homophone-files.sh   # 下载同音字替换文件脚本
└── README.md                     # 本文档
```

## 🎯 工作模式详解

### KWS 唤醒模式

**触发条件**: `keywords.txt` 文件存在

**工作流程**：
```
待机(STANDBY) → 说"你好小智" → 激活(ACTIVE) → 5秒无语音 → 自动休眠
     ↑                                                          ↓
     └──────────────────────────────────────────────────────────┘
```

**优势**：
- 低功耗待机，仅运行轻量级 KWS 模型
- 唤醒后动态加载 ASR 识别器
- 自动休眠释放资源

### 直接识别模式

**触发条件**: `keywords.txt` 文件不存在

**工作流程**：
- 启动时加载 ASR + VAD
- 点击开始即可识别
- 适合频繁使用场景

## 🔧 高级功能

### 1. 同音字纠正 (HomophoneReplacer)

基于 FST 规则的后处理，自动纠正常见同音字错误：

- ❌ "在坐的各位" → ✅ "在座的各位"
- ❌ "因该这样做" → ✅ "应该这样做"

**配置文件**：
- `lexicon.txt` - 词典
- `replace.fst` - 替换规则

**启用**：文件存在自动启用，日志显示 `HomophoneReplacer enabled`

### 2. VAD 智能断句

使用 Silero VAD 神经网络模型：

- 0.3 秒静音即可断句（比内置 endpoint 快 5 倍）
- 抗噪音能力强
- 提高断句准确度

**启用**：`silero_vad.onnx` 存在自动启用

### 3. PCM 音频缓存

自动保存所有录音：

- 缓存位置：`/data/data/com.example.streamingasr/files/audio_cache/`
- 格式：PCM 16-bit
- 支持导出 WAV 格式

## 📦 打包 AAR 库

```bash
cd library
../gradlew assembleRelease

# 输出文件：
# library/build/outputs/aar/library-release.aar
```

在其他项目中使用：

```kotlin
dependencies {
    implementation(files("libs/library-release.aar"))
}
```

详见 `library/README.md`

## 🐛 常见问题

### 模型加载失败

**错误**: "Model files not found"

**解决**:
```bash
# 检查文件是否存在
adb shell run-as com.example.streamingasr ls -lh files/models/asr/

# 如果为空，重新推送模型
```

### JNI 库加载失败

**错误**: "UnsatisfiedLinkError: libsherpa-onnx-jni.so"

**解决**:
```bash
./download-libs.sh
```

### 识别结果为空

**原因**：
- 麦克风权限未授予 → 检查应用权限
- 麦克风音量太小 → 靠近设备
- 环境噪音过大 → 在安静环境测试

### 应用闪退

**解决**:
```bash
# 查看日志
adb logcat | grep -E "StreamingASR|sherpa"
```

## 📊 性能指标

| 指标 | 值 |
|------|-----|
| 识别延迟 | 100-300ms |
| CPU 使用 | 15-30% (单核) |
| 内存占用 | 150-250 MB |
| 模型大小 | 70 MB (双语模型) |

## 📱 设备要求

- Android 7.0 (API 24) 或更高
- 至少 2GB RAM
- 麦克风权限
- 存储空间 50-200 MB

## 🔗 相关链接

- [sherpa-onnx 官方仓库](https://github.com/k2-fsa/sherpa-onnx)
- [模型下载](https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models)
- [在线文档](https://k2-fsa.github.io/sherpa/onnx/)

## 📄 许可证

Apache 2.0 License

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！
