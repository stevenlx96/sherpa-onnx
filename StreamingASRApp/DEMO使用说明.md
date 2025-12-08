# Demo 应用使用指南

## 🎯 项目结构

```
StreamingASRApp/
├── app/          # ✅ 原始应用（功能完整，可直接运行）
├── library/      # 📦 AAR 打包 module（用于生成 AAR）
└── demo/         # 🆕 Demo 测试应用（测试 AAR 功能）
```

## 🚀 快速开始

### 1. 在 Android Studio 中打开项目

打开 `/StreamingASRApp` 目录（不是根目录）

### 2. Sync 项目

点击 **Sync Project with Gradle Files**

### 3. 选择运行配置

在顶部工具栏选择：
- **app** - 运行原始应用
- **demo** - 运行 Demo 应用（测试 AAR 功能）

### 4. 准备模型文件

#### 方法 A：使用 adb 推送模型（推荐）

```bash
# Demo 应用的模型路径
adb shell mkdir -p /data/data/com.example.demo.streamingasr/files/models

# 推送模型文件
adb push encoder-epoch-99-avg-1.onnx /data/data/com.example.demo.streamingasr/files/models/
adb push decoder-epoch-99-avg-1.onnx /data/data/com.example.demo.streamingasr/files/models/
adb push joiner-epoch-99-avg-1.onnx /data/data/com.example.demo.streamingasr/files/models/
adb push tokens.txt /data/data/com.example.demo.streamingasr/files/models/

# 验证
adb shell ls -lh /data/data/com.example.demo.streamingasr/files/models/
```

#### 方法 B：从原 app 复制模型（如果原 app 已有模型）

```bash
# 在设备上复制模型文件
adb shell "mkdir -p /data/data/com.example.demo.streamingasr/files/models && \
           cp -r /data/data/com.example.streamingasr/files/models/* \
                 /data/data/com.example.demo.streamingasr/files/models/"

# 修复权限
adb shell "chmod -R 755 /data/data/com.example.demo.streamingasr/files/"
```

### 5. 运行应用

1. 选择 **demo** 配置
2. 点击运行按钮（绿色三角形）
3. 授予录音权限
4. 点击"开始识别"按钮

## 📋 三个 Module 的区别

| Module | 用途 | 依赖方式 | 运行方式 |
|--------|------|----------|----------|
| **app** | 原始应用 | 直接包含源代码 | 可直接运行 |
| **library** | AAR 打包 | 无（library 本身） | 不能运行，用于打包 AAR |
| **demo** | 测试 AAR | 依赖 library module | 可运行，测试 AAR 功能 |

## 🎓 Demo 的作用

### 1. 开发测试

在开发过程中：
- 修改 `library/` 中的代码
- 直接运行 `demo` 查看效果
- 无需每次打包 AAR

### 2. 功能验证

验证 AAR 打包后：
- ModelManager 能否正常加载模型
- AudioRecorder 能否正常录音
- 识别功能是否正常

### 3. 集成示例

展示如何：
- 依赖 library（模拟 AAR）
- 使用 ModelManager 管理模型
- 使用 AudioRecorder 录制音频
- 实现流式语音识别

## 💡 开发流程

### 正常开发流程

```
1. 在 library/ 中修改代码
   ↓
2. 运行 demo 测试功能
   ↓
3. 确认功能正常
   ↓
4. 构建 AAR: ./build-library-aar.sh
   ↓
5. 分发 AAR 给使用者
```

### 切换到使用 AAR

如果要测试实际的 AAR 文件：

1. 构建 AAR：
   ```bash
   ./build-library-aar.sh
   ```

2. 修改 `demo/build.gradle.kts`：
   ```kotlin
   dependencies {
       // 注释掉 library module 依赖
       // implementation(project(":library"))

       // 使用 AAR 文件
       implementation(files("../library/build/outputs/aar/library-release.aar"))

       // ...其他依赖保持不变
   }
   ```

3. Sync 并运行

## 🔍 关键代码示例

### MainActivity.kt

```kotlin
// 使用 ModelManager（来自 AAR/library）
val modelManager = ModelManager(context)
val recognizer = modelManager.createOnlineRecognizerAuto()

// 使用 AudioRecorder（来自 AAR/library）
val audioRecorder = AudioRecorder(sampleRate, cacheDir)
audioRecorder.startRecording()
```

## 📱 应用包名

- **原 app**: `com.example.streamingasr`
- **Demo**: `com.example.demo.streamingasr`

**注意**：包名不同，模型文件路径也不同！

## ⚠️ 常见问题

### Q: Demo 提示"模型加载失败"？

A: 检查模型文件是否已推送到正确路径：
```bash
adb shell ls /data/data/com.example.demo.streamingasr/files/models/
```

### Q: 如何同时运行 app 和 demo？

A: 可以同时安装，因为包名不同。但模型文件需要分别推送到各自的目录。

### Q: Demo 和 app 功能有区别吗？

A: 完全相同。唯一区别是 demo 依赖 library module，app 直接包含源码。

### Q: 为什么创建 demo 而不是直接修改 app？

A:
- 保持原 app 不变
- 更好地模拟实际使用 AAR 的场景
- 可以同时运行两个应用对比

## 📚 相关文档

- `demo/README.md` - Demo 详细说明
- `AAR打包说明.md` - AAR 打包和使用指南
- `library/README.md` - Library 模块说明

---

**开始使用 Demo 测试 AAR 功能吧！** 🚀
