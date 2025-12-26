# HomophoneReplacer (同音字替换) 使用说明

---

## 概述

HomophoneReplacer 是 Sherpa-ONNX 的同音字自动纠正功能，可以自动修正识别结果中的常见同音字错误。

**示例**:
- "在坐" → "在座"
- "因该" → "应该"
- "做作业" → "做作业"（不改，因为正确）

---

## 📁 文件要求

需要两个文件：

| 文件 | 说明 |
|------|------|
| `lexicon.txt` | 词典文件（拼音映射） |
| `replace.fst` | 替换规则（OpenFST 格式） |

**存放位置**: `/data/data/你的包名/files/models/asr/`

---

## ✅ 使用方法（超简单！）

### 步骤1: 下载文件

从 sherpa-onnx 官方下载：
```
https://github.com/k2-fsa/sherpa-onnx/releases/tag/hr-files
```

下载这两个文件：
- `lexicon.txt`
- `replace.fst`

### 步骤2: 部署文件

```bash
# 推送到设备
adb push lexicon.txt /data/data/你的包名/files/models/asr/
adb push replace.fst /data/data/你的包名/files/models/asr/
```

### 步骤3: 使用（不需要改代码！）

```kotlin
val recognizer = modelManager.createOnlineRecognizer(
    modelType = ModelManager.ModelType.ZIPFORMER_TRANSDUCER
)

// 就这样！如果 lexicon.txt 和 replace.fst 存在，自动启用！
```

**日志确认**:
```
I/ModelManager: HomophoneReplacer enabled: lexicon=/data/data/.../lexicon.txt, fst=/data/data/.../replace.fst
```

---

## 🔍 工作原理

ModelManager 会自动检测这两个文件：

```kotlin
// ModelManager 内部代码（你不需要写）
private fun createHomophoneReplacerConfig(): HomophoneReplacerConfig {
    val lexiconFile = File(asrDir, "lexicon.txt")
    val replaceFstFile = File(asrDir, "replace.fst")

    return if (lexiconFile.exists() && replaceFstFile.exists()) {
        // ✅ 两个文件都存在 → 自动启用
        HomophoneReplacerConfig(
            lexicon = lexiconFile.absolutePath,
            ruleFsts = replaceFstFile.absolutePath
        )
    } else {
        // ❌ 文件不存在 → 不启用
        HomophoneReplacerConfig()
    }
}
```

**重点**: 你不需要调用任何额外代码，ModelManager 会自动处理！

---

## 📂 完整目录结构

```
/data/data/你的包名/files/models/
├── asr/
│   ├── encoder-epoch-99-avg-1.onnx
│   ├── decoder-epoch-99-avg-1.onnx
│   ├── joiner-epoch-99-avg-1.onnx
│   ├── tokens.txt
│   ├── lexicon.txt       ← 同音字词典
│   └── replace.fst       ← 替换规则
├── kws/
│   └── ...
└── vad/
    └── silero_vad.onnx
```

---

## 📝 文件格式说明

### lexicon.txt 格式

词典文件，拼音映射：

```
在坐 z ai4 z uo4
在座 z ai4 z uo4
因该 y in1 g ai1
应该 y ing1 g ai1
```

**格式**: `词语 拼音1 拼音2 拼音3 ...`

### replace.fst 格式

替换规则文件（OpenFST 格式）：

```
0 1 在坐 在座
0 1 因该 应该
1
```

**格式**: `起始状态 目标状态 输入词 输出词`

**注意**: 这是二进制格式，需要用 OpenFST 工具编译生成，或直接下载官方提供的文件。

---

## ⚙️ 自定义替换规则

### 方法1: 修改文本文件（推荐）

1. 编辑 `lexicon.txt`，添加新词:
   ```
   做站 z uo4 z han4
   做站 z uo4 z han4
   ```

2. 重新编译 `replace.fst`（需要 OpenFST 工具）

3. 替换文件，重启应用

### 方法2: 使用官方工具

sherpa-onnx 提供了工具来生成自定义规则：

```bash
# 安装 OpenFST
sudo apt-get install libfst-dev

# 使用 sherpa-onnx 工具生成 replace.fst
# 详见: https://k2-fsa.github.io/sherpa/onnx/hotwords/
```

---

## 🧪 测试效果

### 测试代码

```kotlin
val recognizer = modelManager.createOnlineRecognizer()
val stream = recognizer?.createStream()

// 说："我在坐在这里"
// 期望输出："我在座在这里"（"在坐" 被纠正为 "在座"）
```

### 检查是否启用

查看 Logcat 日志：

```
✅ 已启用:
I/ModelManager: HomophoneReplacer enabled: lexicon=/data/data/.../lexicon.txt

❌ 未启用:
D/ModelManager: HomophoneReplacer disabled: lexicon.txt not found
D/ModelManager: HomophoneReplacer disabled: replace.fst not found
```

---

## ❓ 常见问题

### Q1: HomophoneReplacer 不生效？

**检查清单**:
1. ✅ 文件路径正确？
   ```bash
   adb shell ls /data/data/你的包名/files/models/asr/lexicon.txt
   adb shell ls /data/data/你的包名/files/models/asr/replace.fst
   ```

2. ✅ 两个文件都存在？（缺一个都不行）

3. ✅ 查看日志是否有 "HomophoneReplacer enabled"

### Q2: 需要重启应用吗？

**答**: 需要重新加载识别器

```kotlin
// 释放旧识别器
recognizer?.release()

// 重新创建（会重新检测文件）
recognizer = modelManager.createOnlineRecognizer()
```

### Q3: 可以不用 HomophoneReplacer 吗？

**答**: 可以！如果不放这两个文件，功能就不会启用，不影响正常使用。

### Q4: 文件放错位置会怎样？

**答**: ModelManager 找不到文件，功能不会启用，但不会报错。日志会显示：
```
D/ModelManager: HomophoneReplacer disabled: lexicon.txt not found
```

### Q5: 可以动态切换替换规则吗？

**答**: 可以，但需要：
1. 替换文件（lexicon.txt 和 replace.fst）
2. 释放旧识别器
3. 重新创建识别器

```kotlin
// 替换文件
val newLexicon = File(modelManager.getModelDir(), "asr/lexicon.txt")
newLexicon.writeText("新的词典内容")

// 重新加载
recognizer?.release()
recognizer = modelManager.createOnlineRecognizer()
```

---

## 📊 性能影响

- **CPU 占用**: 几乎无影响（< 1%）
- **内存占用**: 约 1-5 MB（取决于规则数量）
- **识别延迟**: 几乎无影响（后处理，不影响实时性）

---

## 🎯 最佳实践

### 1. 使用官方文件

推荐直接使用 sherpa-onnx 官方提供的文件，已经包含常见的同音字错误。

### 2. 定期更新

根据实际识别错误，定期更新替换规则。

### 3. 测试验证

更新文件后，测试常见句子确保规则生效。

### 4. 备份原文件

修改前备份原文件，避免出错。

---

## 📋 总结

### 使用 HomophoneReplacer 的完整步骤

1. **下载文件**
   ```
   https://github.com/k2-fsa/sherpa-onnx/releases/tag/hr-files
   ```

2. **部署文件**
   ```bash
   adb push lexicon.txt /data/data/你的包名/files/models/asr/
   adb push replace.fst /data/data/你的包名/files/models/asr/
   ```

3. **正常使用**
   ```kotlin
   val recognizer = modelManager.createOnlineRecognizer()
   // 自动启用！
   ```

4. **验证日志**
   ```
   I/ModelManager: HomophoneReplacer enabled: lexicon=...
   ```

### 关键点

✅ **不需要写额外代码**
✅ **直接放文件就能用**
✅ **自动检测并启用**
✅ **不影响性能**

---

**最后更新**: 2024-12-25
