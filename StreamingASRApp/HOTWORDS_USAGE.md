# 热词（Hot Words）使用指南

## 概述

热词功能可以提高特定词语的识别准确度，特别适合：
- 专有名词（人名、地名、公司名）
- 行业术语
- 产品名称
- 需要优先识别的关键词

## 工作原理

当识别到**同音词**时，热词会获得更高的权重，使模型优先选择热词列表中的词语。

**示例：**
- 没有热词：识别到"小大"
- 有热词"小达"：识别到"小达"（优先匹配）

## 使用方式

### 方式1：全局热词文件（推荐）

适合固定的热词列表。

#### 1. 创建热词文件

创建 `hotwords.txt` 文件，每行一个热词：

```
小达
小智助手
人工智能
深度学习
sherpa-onnx
```

#### 2. 部署到手机

```bash
# 推送热词文件到 asr/ 目录
adb push hotwords.txt /data/local/tmp/

adb shell
run-as com.example.streamingasr
cp /data/local/tmp/hotwords.txt /data/data/com.example.streamingasr/files/models/asr/
cat /data/data/com.example.streamingasr/files/models/asr/hotwords.txt  # 验证
exit
```

#### 3. 在代码中启用

```kotlin
val recognizer = modelManager.createOnlineRecognizer(
    modelType = ModelManager.ModelType.ZIPFORMER_TRANSDUCER,
    hotwordsFile = "hotwords.txt",  // 热词文件名
    hotwordsScore = 1.5f             // 热词权重（1.0-3.0）
)
```

### 方式2：动态热词（推荐用于临时热词）

适合根据场景动态调整的热词。

```kotlin
// 创建识别器（不指定全局热词）
val recognizer = modelManager.createOnlineRecognizer()

// 创建流时动态指定热词（用空格或换行分隔）
val hotwords = "小达 小智助手 人工智能"
val stream = recognizer.createStream(hotwords = hotwords)

// 或根据上下文动态调整
val contextualHotwords = when (currentScene) {
    "customer_service" -> "退款 订单 物流"
    "smart_home" -> "开灯 关灯 空调 温度"
    else -> ""
}
val stream = recognizer.createStream(hotwords = contextualHotwords)
```

### 方式3：组合使用

全局热词 + 动态热词：

```kotlin
// 全局热词：固定的专有名词
val recognizer = modelManager.createOnlineRecognizer(
    hotwordsFile = "hotwords.txt",  // 包含：小达、小智助手
    hotwordsScore = 1.5f
)

// 动态热词：根据场景添加临时热词
val dynamicHotwords = "退款 订单 物流"  // 客服场景
val stream = recognizer.createStream(hotwords = dynamicHotwords)
```

## 参数调优

### hotwordsScore（热词权重）

控制热词的优先级：

| 值 | 效果 | 适用场景 |
|---|------|----------|
| 1.0 | 轻微提升 | 热词不太重要，只是稍微提示 |
| **1.5** | **适中提升**（默认） | ⭐ **推荐**，平衡识别准确度 |
| 2.0 | 强烈提升 | 热词非常重要，需要优先识别 |
| 2.5-3.0 | 极强提升 | 热词是唯一期望结果，可能影响其他词 |

**调优建议：**
- 从 1.5 开始测试
- 如果热词仍然识别错误，逐步增加到 2.0-2.5
- 如果过度匹配（误识别为热词），降低到 1.0-1.2

## 高级技巧

### 1. 负分热词（降低优先级）

如果某些词经常被误识别，可以降低其优先级：

```
# hotwords.txt
小达 1.5       # 提高优先级
小智助手 1.5
错误词 0.5     # 降低优先级
垃圾词 0.3     # 大幅降低
```

### 2. 多音字/同音词处理

```
# 针对同音词添加热词
小达        # vs 小大、小答
智能        # vs 至能、只能
人工智能    # vs 人工之能
```

### 3. 英文和中文混合

```
sherpa-onnx
API接口
deep learning
人工智能AI
```

### 4. 长短词组合

```
# 短词
小达

# 长词（更精确的上下文）
小达小达
你好小达
```

## 文件格式详解

### 基本格式

```
热词1
热词2
热词3
```

### 带权重格式

```
热词1 1.5
热词2 2.0
热词3 1.2
```

### 带注释

```
# 这是注释
小达        # 人名
小智助手    # 产品名
# 以 # 开头的行会被忽略
```

## 检查热词是否生效

### 方法1：查看日志

```bash
adb logcat | grep "Hotwords"
```

看到类似输出说明已启用：
```
ModelManager: Hotwords enabled: /data/.../asr/hotwords.txt, score=1.5
```

### 方法2：代码检查

```kotlin
val modelManager = ModelManager(context)

if (modelManager.checkHotwordsExists("hotwords.txt")) {
    Log.i(TAG, "热词文件已就绪")
} else {
    Log.w(TAG, "热词文件不存在")
}
```

## 常见问题

### Q: 热词和 HomophoneReplacer 有什么区别？

- **热词（Hotwords）**: 在**识别阶段**提高特定词的优先级，影响声学模型的解码
- **HomophoneReplacer**: 在**后处理阶段**替换同音字错误，基于规则的文本替换

两者可以同时使用，互补效果更好。

### Q: 热词列表应该有多大？

- **推荐**：10-100 个热词
- **可接受**：100-500 个
- **过多**（>1000）：可能影响性能和准确度

### Q: 热词不生效怎么办？

1. 检查文件路径和文件名是否正确
2. 检查热词文件编码是否为 UTF-8（无 BOM）
3. 尝试增大 `hotwordsScore` 值
4. 检查热词是否在模型的词表中

### Q: 动态热词和文件热词可以同时使用吗？

可以！两者会合并生效。

### Q: 热词会影响性能吗？

轻微影响，但通常可以忽略不计。影响程度取决于热词数量。

## 最佳实践

✅ **推荐做法：**
- 热词列表保持精简（10-100个）
- 使用文件热词作为基础，动态热词根据场景调整
- hotwordsScore 从 1.5 开始调优
- 热词文件使用 UTF-8 编码，无 BOM

❌ **避免：**
- 热词列表过大（>1000 个）
- hotwordsScore 设置过高（>3.0）
- 将所有词都加入热词（失去作用）
- 热词文件包含特殊字符或非文本内容

## 示例场景

### 1. 客服场景

```
# hotwords.txt
退款
订单
物流
发货
售后
质量问题
```

### 2. 智能家居

```
# hotwords.txt
开灯
关灯
空调
温度
窗帘
扫地机器人
```

### 3. 医疗场景

```
# hotwords.txt
高血压
糖尿病
心脏病
处方药
CT检查
核磁共振
```

### 4. 会议记录

```
# hotwords.txt
张总
李经理
王工
产品路线图
季度目标
KPI
```

## 完整使用示例

```kotlin
class MyActivity : AppCompatActivity() {

    private lateinit var modelManager: ModelManager
    private var recognizer: OnlineRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        modelManager = ModelManager(this)

        // 方式1：全局热词文件
        if (modelManager.checkHotwordsExists("hotwords.txt")) {
            recognizer = modelManager.createOnlineRecognizer(
                hotwordsFile = "hotwords.txt",
                hotwordsScore = 1.5f
            )
        } else {
            Log.w(TAG, "热词文件不存在，使用默认识别")
            recognizer = modelManager.createOnlineRecognizer()
        }

        // 方式2：动态热词
        val contextualHotwords = getCurrentSceneHotwords()
        val stream = recognizer?.createStream(hotwords = contextualHotwords)
    }

    private fun getCurrentSceneHotwords(): String {
        return when (currentScene) {
            "customer_service" -> "退款 订单 物流 发货"
            "smart_home" -> "开灯 关灯 空调 温度"
            "meeting" -> "张总 李经理 产品路线图 季度目标"
            else -> ""
        }
    }
}
```

## 参考文档

- [sherpa-onnx 官方文档](https://k2-fsa.github.io/sherpa/onnx/)
- [Contextual Biasing 说明](https://github.com/k2-fsa/sherpa-onnx)
