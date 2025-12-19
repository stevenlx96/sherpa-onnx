# SherpaAsrManager AAR 测试项目

这是一个**完全独立**的 Android 项目，用于测试 `SherpaAsrManager` AAR 的功能。

**重要特性：**
- ✅ 独立的 Gradle 项目（有自己的 wrapper）
- ✅ 通过 AAR 文件使用功能，不依赖源码
- ✅ 可以单独克隆和运行
- ✅ 真实模拟用户使用 AAR 的场景

## 🚀 快速开始

### 方法 1：作为独立项目使用

```bash
cd demo

# 1. 准备 AAR 文件（从父项目复制或自己编译）
# 将 library-release.aar 放到 app/libs/ 目录

# 2. 编译项目
./gradlew assembleDebug

# 3. 安装到设备
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 方法 2：从父项目一键测试

```bash
cd StreamingASRApp

# 运行完整流程脚本（编译 AAR + 复制 + 编译 demo）
./build-and-test-demo.sh
```

## 功能测试

此 Demo 测试了 AAR 的所有核心功能：

- ✅ **SherpaAsrManager 高级 API** - 简洁的回调接口
- ✅ **KWS 唤醒检测** - 自动进入待机/激活模式
- ✅ **VAD 智能断句** - 队列机制检测句子完成
- ✅ **实时识别** - 边说边显示部分结果
- ✅ **句子完成回调** - 适合 LLM 对接
- ✅ **状态管理** - STANDBY / ACTIVE 自动切换
- ✅ **错误处理** - 完整的错误回调

## 使用步骤

### 0. 准备 AAR 文件（首次或更新后）

```bash
# 步骤 1：编译 library 生成 AAR
./gradlew :library:assembleRelease

# 步骤 2：复制 AAR 到 demo/libs/（使用脚本）
./copy-aar-to-demo.sh

# 或手动复制：
# cp library/build/outputs/aar/library-release.aar demo/libs/
```

### 1. 部署模型文件

模型需要部署到设备：

```bash
# 推送到 sdcard
adb push models/ /sdcard/

# 复制到应用数据目录
adb shell
su
cp -r /sdcard/models /data/data/com.example.demo.streamingasr/files/
chmod -R 755 /data/data/com.example.demo.streamingasr/files/models
```

### 2. 编译和安装

```bash
cd StreamingASRApp

# 编译 demo
./gradlew :demo:assembleDebug

# 安装到设备
adb install -r demo/build/outputs/apk/debug/demo-debug.apk
```

### 3. 运行测试

1. 授予录音权限
2. 点击"开始监听"
3. 如果有 KWS 模型：说唤醒词激活
4. 说话测试识别
5. 观察 VAD 断句效果
6. 查看句子完成回调日志

## 代码示例

Demo 展示了如何使用 SherpaAsrManager：

```kotlin
// 创建 ASR Manager
val asr = SherpaAsrManager(context)

// 配置 VAD 参数（可选）
asr.vadConfig = SherpaAsrManager.VadConfig(
    minSilenceDuration = 1.0F  // 静音 1 秒算句子结束
)

// 设置回调
asr.onWakeWordDetected = { keyword ->
    Log.i(TAG, "唤醒: $keyword")
}

asr.onSentenceComplete = { text ->
    // 🎯 发送给 LLM
    sendToLLM(text)
}

asr.onPartialResult = { text ->
    // 实时显示
    updateUI(text)
}

asr.onStateChanged = { state ->
    when (state) {
        State.STANDBY -> Log.i(TAG, "待机...")
        State.ACTIVE -> Log.i(TAG, "识别中...")
    }
}

// 开始监听
asr.startListening()

// 停止监听
asr.stopListening()

// 释放资源
asr.release()
```

## 测试要点

### 1. KWS 唤醒测试

如果部署了 KWS 模型：
- 启动后自动进入待机模式
- 说唤醒词"你好小智"
- 应显示"🔊 已唤醒"
- 自动切换到识别模式

### 2. VAD 断句测试

- 说一句话后停顿 1 秒
- 应该触发句子完成
- 结果前面显示 "✓" 标记
- 查看 Logcat 确认 VAD 队列触发

### 3. 实时识别测试

- 说话时应该实时显示部分结果
- 结果前面显示 "⏳" 标记
- 断句后变成完成状态 "✓"

### 4. 同音字纠正测试

如果部署了 `replace.fst` 和 `lexicon.txt`：
- 说"在坐的各位"
- 应该自动纠正为"在座的各位"

### 5. LLM 对接测试

在句子完成回调中：
```kotlin
asr.onSentenceComplete = { text ->
    // 这里可以发送给 LLM
    Log.i(TAG, "📤 可以发送给 LLM: $text")
}
```

## 日志输出

Demo 会输出详细的日志：

```
I/AARDemo: 🔊 检测到唤醒词: 你好小智
I/AARDemo: ✓ 句子完成: 今天天气真好
I/AARDemo: 📤 可以发送给 LLM: 今天天气真好
```

## 目录结构

```
demo/
  ├── build.gradle.kts          # 构建配置（依赖 library 模块）
  ├── src/main/
  │   ├── AndroidManifest.xml   # 权限配置
  │   ├── java/com/example/demo/
  │   │   └── MainActivity.kt   # 测试代码
  │   └── res/layout/
  │       └── activity_main.xml # UI 布局
  └── README.md                  # 本文档
```

## 依赖说明

**Demo 完全独立使用 AAR 文件：**

```kotlin
dependencies {
    // ✅ 使用 AAR 文件（独立测试）
    implementation(files("libs/library-release.aar"))

    // 必需的外部依赖
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

**这是真正测试 AAR 的方式！** demo 不依赖 library 源码，完全通过 AAR 使用功能。

## 工作原理

```
StreamingASRApp/
  ├── library/                       # 源码模块
  │   └── build/outputs/aar/
  │       └── library-release.aar   # 编译生成的 AAR
  │
  └── demo/                          # 独立测试项目
      ├── libs/
      │   └── library-release.aar   # 从 library 复制过来
      └── build.gradle.kts           # 依赖 AAR 文件
```

每次修改 library 代码后：
1. 重新编译 AAR：`./gradlew :library:assembleRelease`
2. 复制到 demo：`./copy-aar-to-demo.sh`
3. 编译 demo 测试：`./gradlew :demo:assembleDebug`

## 故障排查

### AAR 文件不存在

**错误：** 编译 demo 时提示找不到 AAR 文件

**解决：**
```bash
# 1. 编译 library 生成 AAR
./gradlew :library:assembleRelease

# 2. 复制 AAR 到 demo/libs/
./copy-aar-to-demo.sh

# 3. 验证文件存在
ls -lh demo/libs/library-release.aar
```

### 模型未就绪

确保模型文件部署到：
```
/data/data/com.example.demo.streamingasr/files/models/
```

使用 `adb shell ls` 验证文件存在。

### 权限被拒绝

在应用设置中手动授予录音权限。

### 无法唤醒

1. 检查 KWS 模型是否部署
2. 检查 `keywords.txt` 文件
3. 尝试提高唤醒阈值

### VAD 不断句

调整 VAD 参数：
```kotlin
asr.vadConfig = SherpaAsrManager.VadConfig(
    minSilenceDuration = 0.8F  // 降低阈值更敏感
)
```

---

**提示**：此 Demo 是测试 AAR 功能的最简示例，展示了如何正确使用 SherpaAsrManager 高级 API。
