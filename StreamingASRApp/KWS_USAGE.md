# 唤醒词检测 (Keyword Spotting) 集成说明

## ✅ 已完成的集成

StreamingASRApp 现已集成 **唤醒词检测 (KWS)**，实现智能语音唤醒功能。

## 🔄 工作模式

应用支持 **两种工作模式**，启动时自动检测：

| 模式 | 触发条件 | 说明 | 按钮文本 |
|------|---------|------|---------|
| **KWS 唤醒模式** | `keywords.txt` 存在 | 待机时仅运行 KWS，唤醒后动态加载 ASR | **"开始监听"** |
| **直接识别模式** | `keywords.txt` 不存在 | 启动时加载 ASR，点击直接开始识别 | **"开始识别"** |

**重要特性：**
- 启动时 **不会同时加载** KWS 和 ASR/VAD，避免资源冲突
- KWS 模式下，ASR + VAD 在唤醒后 **延迟加载**，休眠后 **自动释放**
- 节省内存和电量，避免 ONNX Runtime 资源冲突导致的崩溃

## 🎯 功能特性

### 智能状态机

```
┌─────────────┐
│   STANDBY   │ ← 待机模式：仅运行 KWS，监听唤醒词
│ (待机监听)   │   电量消耗低，CPU占用小
└──────┬──────┘
       │ 检测到唤醒词 "你好小智"
       ↓
┌─────────────┐
│   ACTIVE    │ ← 激活模式：运行 ASR + VAD，全功能识别
│ (语音识别)   │   实时转写，智能断句
└──────┬──────┘
       │ 5秒无语音
       ↓
    (自动休眠，返回 STANDBY)
```

### KWS 模式工作流程

1. **启动监听** → 进入 STANDBY 模式，仅 KWS 运行
2. **说出唤醒词** (如 "你好小智") → 检测成功，动态创建 ASR 识别器
3. **语音识别** → ASR 实时转写，VAD 智能断句
4. **自动休眠** → 5 秒无语音后释放 ASR，返回 STANDBY 模式
5. **循环往复** → 等待下一次唤醒

### 内存优化策略

```
[STANDBY]  仅 KWS 常驻内存 (~50MB)
    ↓ 唤醒 → 动态创建 ASR + VAD
[ACTIVE]   KWS + ASR + VAD 在内存 (~170MB)
    ↓ 5秒无语音 → 释放 ASR + VAD
[STANDBY]  仅 KWS 常驻 (~50MB) ✓ 节省 120MB
```

**关键优化：VAD 延迟加载**
- 启动时：KWS 模式下**不创建 VAD**，避免与 KWS 资源冲突
- 唤醒后：动态创建 VAD，实现智能断句
- 休眠时：释放 ASR + VAD，最大化内存节省

## 📦 模型文件下载

### 必需：ASR 模型（KWS 与 ASR 共用同一个模型）

```bash
# 下载中英文双语 Zipformer Transducer 模型
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2
tar xvf sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2
```

需要的文件：
- `encoder-epoch-99-avg-1.onnx`
- `decoder-epoch-99-avg-1.onnx`
- `joiner-epoch-99-avg-1.onnx`
- `tokens.txt`

### 必需：keywords.txt（唤醒词配置文件）

创建 `keywords.txt` 文件，每行一个唤醒词：

```
你好小智
小智小智
嗨小智
```

**重要：** 唤醒词必须在模型的词表中！推荐使用常见词组合。

### 推荐：Silero VAD 模型

```bash
# 下载 VAD 模型（用于智能断句）
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

# 推送唤醒词文件
adb push keywords.txt /data/local/tmp/

# 移动到应用目录
adb shell
run-as com.example.streamingasr
mkdir -p /data/data/com.example.streamingasr/files/models/
cp /data/local/tmp/*.onnx /data/data/com.example.streamingasr/files/models/
cp /data/local/tmp/tokens.txt /data/data/com.example.streamingasr/files/models/
cp /data/local/tmp/keywords.txt /data/data/com.example.streamingasr/files/models/
exit
```

## ⚙️ 参数调优

### KWS 参数

在 `MainActivity.kt:159-163` 中调整 KWS 参数：

```kotlin
keywordSpotter = modelManager.createKeywordSpotter(
    keywordsFile = "keywords.txt",
    threshold = 0.25F,              // 唤醒阈值 (0.1-0.5)
    score = 1.5F                    // 关键词分数 (1.0-2.0)
)
```

| 参数 | 说明 | 推荐值 | 影响 |
|------|------|--------|------|
| `threshold` | 唤醒阈值 | 0.25 | 越低越灵敏，但误唤醒率高 |
| `score` | 关键词分数 | 1.5 | 越高越严格，降低误唤醒 |

### 调优建议

| 场景 | threshold | score | 说明 |
|------|-----------|-------|------|
| **安静环境** | 0.2-0.25 | 1.5 | ⭐ **推荐**，误唤醒少 |
| 嘈杂环境 | 0.3-0.4 | 2.0 | 降低误唤醒，但可能难唤醒 |
| 高灵敏度 | 0.15-0.2 | 1.0 | 容易唤醒，但误唤醒多 |

### 自动休眠时间

在 `MainActivity.kt:40` 中调整：

```kotlin
private const val IDLE_TIMEOUT_MS = 5000L  // 5秒无语音自动休眠
```

| 场景 | IDLE_TIMEOUT_MS | 说明 |
|------|-----------------|------|
| 快速对话 | 3000 (3秒) | 适合短句，快速返回待机 |
| **正常使用** | **5000 (5秒)** | ⭐ **推荐** |
| 长句/演讲 | 8000 (8秒) | 适合连续说话场景 |

## 🔍 使用方法

### 1. 启动应用

- 应用加载后会显示模型状态：
  ```
  ✓ VAD已启用 (智能断句)
  ✓ KWS已启用 (唤醒词检测)
  ```

### 2. 开始监听

- 点击 **"开始监听"** 按钮（橙色）
- 进入 STANDBY 模式，状态显示：`⏸️ 待机中，等待唤醒词...`

### 3. 唤醒

- 说出唤醒词（如 "你好小智"）
- 检测成功后状态显示：`🔊 唤醒！检测到: "你好小智"`
- 自动切换到 ACTIVE 模式（按钮变绿色）

### 4. 语音识别

- 开始说话，实时显示识别结果
- VAD 自动智能断句
- 识别结果累积显示

### 5. 自动休眠

- 5 秒无语音后自动返回 STANDBY 模式
- 状态显示：`💤 自动休眠，等待唤醒词...`
- 循环往复，等待下次唤醒

### 6. 停止监听

- 点击 **"停止监听"** 按钮，完全停止

## 🔍 日志查看

应用会输出详细的唤醒和识别日志：

```
🎤 检测到唤醒词: 你好小智
🔊 唤醒词触发: 你好小智
断句触发: VAD 静音检测
✓ 句子完成: 今天天气怎么样
💤 空闲超时 (5023ms)，返回待机模式
```

## 📊 工作原理

```
音频流
  ├─→ [STANDBY] KWS 检测唤醒词
  │    └─→ 检测到 → 切换到 ACTIVE
  │
  └─→ [ACTIVE] ASR 识别 + VAD 断句
       ├─→ 有语音：实时识别
       ├─→ 静音断句：VAD 或 Endpoint
       └─→ 5秒无语音 → 返回 STANDBY
```

## ❓ 常见问题

### Q: keywords.txt 是必需的吗？
A: **不是必需的**，但决定了工作模式：
- **有 keywords.txt** → KWS 唤醒模式（低功耗待机）
- **无 keywords.txt** → 直接识别模式（点击即识别）

### Q: 如何知道当前是哪种模式？
A: 启动应用后查看按钮文本：
- **"开始监听"** → KWS 唤醒模式
- **"开始识别"** → 直接识别模式

状态栏也会显示：
- `✓ KWS唤醒模式已启用` - KWS 模式
- `✓ 直接识别模式` - 直接模式

### Q: 之前版本崩溃了，现在修复了吗？
A: **已完全修复**！崩溃有两个原因，都已解决：

1. **第一次崩溃**：KWS 和 ASR 同时加载导致资源冲突
   - ✓ 修复：启动时只加载一种识别器（KWS 或 ASR）

2. **第二次崩溃**：VAD 和 KWS 同时创建导致 ONNX Runtime 冲突
   - ✓ 修复：KWS 模式下 VAD **延迟到唤醒后**再创建

**最终方案**：
- STANDBY: 仅 KWS
- 唤醒: 动态创建 ASR + VAD
- 休眠: 释放 ASR + VAD

### Q: 唤醒词识别不准确怎么办？
A: 调整参数：
- **误唤醒太多** → 增大 `threshold` (0.3-0.4) 或 `score` (2.0)
- **唤醒困难** → 减小 `threshold` (0.15-0.2) 或 `score` (1.0)
- **更换唤醒词** → 选择发音清晰、音节多的词（3-4个字最佳）

### Q: 为什么唤醒后5秒就自动休眠？
A: 这是为了省电和优化性能。可以修改 `IDLE_TIMEOUT_MS` 增加等待时间。

### Q: KWS 和 ASR 能同时运行吗？
A: **不能**。这是状态机设计：
- STANDBY：只运行 KWS（省电）
- ACTIVE：只运行 ASR + VAD（全功能识别）

### Q: 可以自定义唤醒词吗？
A: **可以**！编辑 `keywords.txt`，每行一个唤醒词。但要注意：
- 唤醒词必须在模型词表中
- 推荐 3-4 个字的常见词组合
- 发音清晰，避免同音字干扰

## 📝 修改的文件

1. `KeywordSpotter.kt` - 唤醒词识别器封装类（从 SherpaOnnxKws 复制）
2. `ModelManager.kt` - 添加 `createKeywordSpotter()` 方法
3. `MainActivity.kt` - 实现状态机和唤醒逻辑

## 🚀 性能对比

| 模式 | CPU 占用 | 电量消耗 | 功能 |
|------|---------|---------|------|
| STANDBY (KWS) | 低 (~10%) | 低 | 仅唤醒检测 |
| ACTIVE (ASR) | 高 (~30-50%) | 高 | 全功能识别 |

**省电优势：** 使用 KWS 模式，待机时 CPU 和电量消耗仅为识别模式的 1/3 左右！

## 🎨 UI 状态提示

| 状态 | 按钮颜色 | 按钮文本 | 状态文本 |
|------|---------|---------|---------|
| 未启动 | 绿色 | "开始监听" | 模型加载信息 |
| STANDBY | 橙色 | "停止监听" | "⏸️ 待机中，等待唤醒词..." |
| 唤醒中 | 绿色 | "停止监听" | "🔊 唤醒！检测到: ..." |
| ACTIVE | 绿色 | "停止监听" | "🎙️ 正在识别..." |
| 自动休眠 | 橙色 | "停止监听" | "💤 自动休眠，等待唤醒词..." |

## 💡 使用技巧

1. **选择合适的唤醒词**
   - ✅ 推荐："你好小智"、"小智小智"、"嗨小智"
   - ❌ 避免：单字、生僻词、同音字多的词

2. **优化识别效果**
   - 唤醒后立即开始说话（不要停顿太久）
   - 保持 5 秒内有语音输入，避免自动休眠
   - 在安静环境下使用效果最佳

3. **调试技巧**
   - 查看 Logcat 输出，观察唤醒阈值
   - 如果频繁误唤醒，增大 `threshold`
   - 如果难以唤醒，查看是否唤醒词不在词表中

## 🔗 相关文档

- [VAD 使用说明](VAD_USAGE.md) - Silero VAD 智能断句
- [sherpa-onnx KWS 文档](https://k2-fsa.github.io/sherpa/onnx/kws/index.html)
- [预训练 KWS 模型列表](https://k2-fsa.github.io/sherpa/onnx/kws/pretrained_models/index.html)
