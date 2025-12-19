# StreamingASR - 实时语音识别 Android 应用

基于 [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) 的 Android 实时流式语音识别应用，支持智能唤醒、VAD断句和同音字纠正。

## ✨ 功能特性

- 🎙️ **KWS 唤醒检测** - 说"你好小智"唤醒，低功耗待机
- 🔊 **实时流式识别** - 边说边识别，延迟低于 300ms
- ✂️ **VAD 智能断句** - 0.3秒静音断句，抗噪音强
- 📝 **同音字纠正** - HomophoneReplacer 自动纠错（"在坐" → "在座"）
- 💾 **PCM 音频缓存** - 自动保存录音，可导出 WAV
- 🔄 **双工作模式** - KWS 唤醒模式 / 直接识别模式

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

#### 必需：ASR 模型（中英文双语）

```bash
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2
tar xvf sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2
```

需要的文件：
- encoder-epoch-99-avg-1.onnx
- decoder-epoch-99-avg-1.onnx
- joiner-epoch-99-avg-1.onnx
- tokens.txt
- bpe.vocab

#### 可选但推荐：VAD 模型（智能断句）

```bash
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx
```

#### 可选但推荐：KWS 模型（唤醒词"你好小智"）

```bash
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01.tar.bz2
tar xvf sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01.tar.bz2
```

#### 可选：同音字替换（自动纠错）

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
# 推送 ASR 模型
adb push encoder-epoch-99-avg-1.onnx /sdcard/
adb push decoder-epoch-99-avg-1.onnx /sdcard/
adb push joiner-epoch-99-avg-1.onnx /sdcard/
adb push tokens.txt /sdcard/
adb push bpe.vocab /sdcard/

# 移动到应用目录（无需 root）
adb shell run-as com.example.streamingasr mkdir -p files/models/asr
adb shell run-as com.example.streamingasr cp /sdcard/encoder-epoch-99-avg-1.onnx files/models/asr/
adb shell run-as com.example.streamingasr cp /sdcard/decoder-epoch-99-avg-1.onnx files/models/asr/
adb shell run-as com.example.streamingasr cp /sdcard/joiner-epoch-99-avg-1.onnx files/models/asr/
adb shell run-as com.example.streamingasr cp /sdcard/tokens.txt files/models/asr/
adb shell run-as com.example.streamingasr cp /sdcard/bpe.vocab files/models/asr/

# 可选：推送 VAD 模型
adb push silero_vad.onnx /sdcard/
adb shell run-as com.example.streamingasr mkdir -p files/models/vad
adb shell run-as com.example.streamingasr cp /sdcard/silero_vad.onnx files/models/vad/

# 可选：推送 KWS 模型
adb push keywords.txt /sdcard/
adb push encoder-epoch-12-avg-2-chunk-16-left-64.onnx /sdcard/
adb push decoder-epoch-12-avg-2-chunk-16-left-64.onnx /sdcard/
adb push joiner-epoch-12-avg-2-chunk-16-left-64.onnx /sdcard/
adb shell run-as com.example.streamingasr mkdir -p files/models/kws
adb shell run-as com.example.streamingasr cp /sdcard/keywords.txt files/models/kws/
adb shell run-as com.example.streamingasr cp /sdcard/tokens.txt files/models/kws/
adb shell run-as com.example.streamingasr cp /sdcard/*epoch-12*.onnx files/models/kws/

# 可选：推送同音字替换文件
adb push lexicon.txt /sdcard/
adb push replace.fst /sdcard/
adb shell run-as com.example.streamingasr cp /sdcard/lexicon.txt files/models/asr/
adb shell run-as com.example.streamingasr cp /sdcard/replace.fst files/models/asr/

# 清理临时文件
adb shell rm /sdcard/*.onnx /sdcard/*.txt /sdcard/*.fst /sdcard/*.vocab
```

### 5. 使用应用

1. 打开应用，授予录音权限
2. 等待模型加载

**KWS 唤醒模式**（推荐，如果有 keywords.txt）：
- 点击"开始监听"进入待机模式
- 说"你好小智"唤醒
- 系统自动开始识别
- 5 秒无语音自动休眠

**直接识别模式**（无 keywords.txt）：
- 点击"开始识别"
- 直接说话，实时显示结果
- 点击"停止识别"结束

## 🎯 核心功能详解

### 1. KWS 唤醒检测

**智能状态机**：
```
待机(STANDBY) → 说"你好小智" → 激活(ACTIVE) → 5秒无语音 → 自动休眠
     ↑                                                          ↓
     └──────────────────────────────────────────────────────────┘
```

**优势**：
- 低功耗待机，仅运行轻量级 KWS 模型（3.3MB）
- 唤醒后动态加载 ASR 识别器和 VAD
- 自动休眠释放资源，避免内存占用
- 避免 ONNX Runtime 资源冲突

**配置文件**：`files/models/kws/keywords.txt`
```
你好小智
小智小智
```

**详细说明**：见 [KWS_USAGE.md](KWS_USAGE.md)

### 2. VAD 智能断句

**三重断句策略**（优先级递减）：

1. **标点符号断句** - 检测：`。！？.!?`
2. **VAD 静音检测** - 0.3 秒静音断句（推荐）
3. **内置 Endpoint** - 1.4-2.4 秒静音断句（备用）

**Silero VAD 优势**：
- 神经网络模型，抗噪音能力强
- 比内置 endpoint 快 5 倍（0.3s vs 1.4s）
- 避免句子中间误断句

**配置**：
- 文件：`files/models/vad/silero_vad.onnx`
- 如果不存在，自动降级使用内置 endpoint

**详细说明**：见 [VAD_USAGE.md](VAD_USAGE.md)

### 3. 同音字纠正（HomophoneReplacer）

**工作原理**：
- 使用 FST (Finite State Transducer) 进行规则替换
- 在 `getResult()` 返回结果前自动应用
- 速度极快（< 1ms），不影响流式识别

**配置文件**：
- `lexicon.txt` - 词典文件，定义发音到文字的映射
- `replace.fst` - 替换规则 FST

**示例**：
- ❌ "在坐的各位" → ✅ "在座的各位"
- ❌ "因该这样做" → ✅ "应该这样做"
- ❌ "金安达" → ✅ "津安达"（可自定义规则）

**启用/禁用**：
- 文件存在 → 自动启用（日志：`HomophoneReplacer enabled`）
- 文件不存在 → 自动禁用

**自定义规则**：
1. 编辑 `lexicon.txt` 添加词条
2. 使用工具生成新的 `replace.fst`
3. 推送到设备

### 4. PCM 音频缓存

自动保存所有录音：

- 缓存位置：`/data/data/com.example.streamingasr/files/audio_cache/`
- 格式：PCM 16-bit
- 支持导出 WAV 格式

## 📁 目录结构

```
StreamingASRApp/
├── app/                              # 主应用
│   └── src/main/
│       ├── java/com/
│       │   ├── example/streamingasr/
│       │   │   ├── MainActivity.kt      # 主Activity（唤醒+识别逻辑）
│       │   │   ├── AudioRecorder.kt     # 音频录制（PCM缓存）
│       │   │   └── ModelManager.kt      # 模型管理（KWS/ASR/VAD）
│       │   └── k2fsa/sherpa/onnx/       # Sherpa-ONNX API
│       ├── res/                         # 资源文件
│       └── jniLibs/                     # JNI 库（需下载）
├── library/                          # AAR 库模块（暂未使用）
├── download-libs.sh                  # 下载 JNI 库脚本
├── download-homophone-files.sh       # 下载同音字替换文件脚本
├── KWS_USAGE.md                      # KWS 使用详解
├── VAD_USAGE.md                      # VAD 使用详解
└── README.md                         # 本文档
```

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

### 唤醒词不响应

**原因**：
- keywords.txt 不存在 → 应用会自动切换到直接识别模式
- KWS 模型文件缺失 → 检查 files/models/kws/ 目录
- 环境噪音过大 → 在安静环境测试

### 识别结果为空

**原因**：
- 麦克风权限未授予 → 检查应用权限
- 麦克风音量太小 → 靠近设备
- 环境噪音过大 → 在安静环境测试

### 应用闪退

**解决**:
```bash
# 查看日志
adb logcat | grep -E "StreamingASR|sherpa|ONNX"
```

## 📊 性能指标

| 指标 | 值 |
|------|-----|
| 识别延迟 | 100-300ms |
| CPU 使用 | 15-30% (单核) |
| 内存占用 | 150-250 MB（激活模式）|
| 内存占用 | 50-80 MB（待机模式）|
| KWS 模型 | 3.3 MB |
| ASR 模型 | 70 MB |
| VAD 模型 | 1.8 MB |

## 📱 设备要求

- Android 7.0 (API 24) 或更高
- 至少 2GB RAM
- 麦克风权限
- 存储空间 100-200 MB

## 🔗 相关链接

- [sherpa-onnx 官方仓库](https://github.com/k2-fsa/sherpa-onnx)
- [模型下载](https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models)
- [在线文档](https://k2-fsa.github.io/sherpa/onnx/)

## 📄 许可证

Apache 2.0 License

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！
