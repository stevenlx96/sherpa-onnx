# Silero VAD 集成说明

## ✅ 已完成的集成

StreamingASRApp 现已集成 **Silero VAD**，提供智能断句功能。

## 🎯 功能特性

### 三重断句策略（优先级递减）

1. **标点符号断句** - 优先级最高
   - 检测：`。！？.!?`
   - 适用场景：模型输出标点时（当前 Streaming Zipformer 不输出标点）

2. **VAD 静音检测** - 优先级次高 ⭐ **推荐**
   - 0.3 秒静音即可断句（比内置 endpoint 快 5 倍）
   - 神经网络模型，抗噪音能力强
   - 需要下载 `silero_vad.onnx` 模型

3. **内置 Endpoint** - 备用方案
   - 1.4-2.4 秒静音断句
   - 如果 VAD 模型不存在，自动降级使用此方案

## 📦 模型文件下载

### 必需：ASR 模型

```bash
# 下载中英文双语模型
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2
tar xvf sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2
```

需要的文件：
- `encoder-epoch-99-avg-1.onnx`
- `decoder-epoch-99-avg-1.onnx`
- `joiner-epoch-99-avg-1.onnx`
- `tokens.txt`

### 推荐：Silero VAD 模型

```bash
# 下载 VAD 模型
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx
```

## 📱 部署到手机

所有模型文件放到同一个目录：
```
/data/data/com.example.streamingasr/files/models/
```

### 使用 adb 推送

```bash
# 推送 ASR 模型
adb push encoder-epoch-99-avg-1.onnx /data/local/tmp/
adb push decoder-epoch-99-avg-1.onnx /data/local/tmp/
adb push joiner-epoch-99-avg-1.onnx /data/local/tmp/
adb push tokens.txt /data/local/tmp/

# 推送 VAD 模型
adb push silero_vad.onnx /data/local/tmp/

# 移动到应用目录
adb shell
run-as com.example.streamingasr
mkdir -p /data/data/com.example.streamingasr/files/models/
cp /data/local/tmp/*.onnx /data/data/com.example.streamingasr/files/models/
cp /data/local/tmp/tokens.txt /data/data/com.example.streamingasr/files/models/
exit
```

## ⚙️ VAD 参数调优

在 `MainActivity.kt:135-140` 中调整参数：

```kotlin
vad = modelManager.createVad(
    threshold = 0.5F,              // 语音检测阈值 (0.3-0.7)
    minSilenceDuration = 0.3F,     // 静音多久断句 (0.2-0.5秒)
    minSpeechDuration = 0.25F,     // 最短语音长度 (过滤杂音)
    maxSpeechDuration = 10.0F      // 最长一句话时间 (强制断句)
)
```

### 调优建议

| 场景 | minSilenceDuration | 说明 |
|------|-------------------|------|
| 快速对话 | 0.2-0.25s | 响应快，但可能误断句 |
| **正常对话** | **0.3-0.4s** | ⭐ **推荐** |
| 长句/演讲 | 0.5s+ | 避免误断句，但响应稍慢 |

## 🔍 日志查看

应用会输出详细的断句日志：

```
断句触发: 标点符号      // 检测到标点
断句触发: VAD 静音检测   // VAD 检测到静音
断句触发: 内置 endpoint  // 内置 endpoint 触发
✓ 句子完成: 这是一个测试
```

## 📊 工作原理

```
音频流
  ├─→ VAD 检测 (isSpeechDetected)
  ├─→ ASR 识别 (OnlineRecognizer)
  └─→ 断句判断
       ├─ 1️⃣ 有标点？→ 断句
       ├─ 2️⃣ VAD 检测到静音？→ 断句
       └─ 3️⃣ Endpoint 触发？→ 断句
```

## ❓ 常见问题

### Q: VAD 模型是必需的吗？
A: 不是。如果 `silero_vad.onnx` 不存在，应用会自动使用内置的 endpoint，功能正常但断句稍慢。

### Q: 如何知道 VAD 是否启用？
A: 启动应用后，状态栏会显示：
- `✓ VAD已启用 (智能断句)` - VAD 已加载
- `✗ VAD未加载 (使用内置endpoint)` - VAD 未加载

### Q: 断句太快/太慢怎么办？
A: 调整 `minSilenceDuration` 参数：
- 太快（误断句）→ 增大到 0.4-0.5s
- 太慢 → 减小到 0.2-0.25s

## 📝 修改的文件

1. `ModelManager.kt` - 添加 VAD 初始化方法
2. `MainActivity.kt` - 集成 VAD 到识别流程
3. `Vad.kt` - VAD 模型封装类（新增）

## 🚀 性能提升

| 指标 | 内置 Endpoint | Silero VAD | 提升 |
|------|--------------|------------|------|
| 断句延迟 | 1.4-2.4s | 0.3s | **5-8倍** |
| 抗噪能力 | 弱 | 强 | ⭐⭐⭐ |
| 准确度 | 一般 | 高 | ⭐⭐⭐ |
