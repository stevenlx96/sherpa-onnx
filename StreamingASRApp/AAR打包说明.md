# StreamingASR AAR 打包完整指南

## 📁 项目结构

```
StreamingASRApp/
├── app/                          # ✅ 原始应用（未修改，可正常运行）
│   └── ...
├── library/                      # 🆕 用于打包 AAR 的 module
│   ├── src/
│   │   └── main/
│   │       ├── java/            # 源代码（从 app 复制，包含 ModelManager）
│   │       ├── jniLibs/         # ⚠️ 需要你手动放置 .so 文件
│   │       │   ├── arm64-v8a/
│   │       │   └── armeabi-v7a/
│   │       ├── res/             # 资源文件
│   │       └── AndroidManifest.xml
│   ├── build.gradle.kts         # Library 配置
│   └── README.md                # Library 详细说明
├── build-library-aar.sh         # 🚀 一键构建脚本
└── AAR打包说明.md               # 本文件
```

## 🎯 关键特性：模型文件动态加载

**重要：** 此 AAR **不打包**模型文件，而是在运行时从应用数据目录加载模型。

### 模型加载路径

模型文件需要放在：
```
/data/data/<应用包名>/files/models/
```

例如，如果你的应用包名是 `com.yourcompany.yourapp`，模型路径为：
```
/data/data/com.yourcompany.yourapp/files/models/
```

### 优点

- ✅ **AAR 体积小**：只包含代码和 native 库（约 10-50 MB），不包含模型（通常几百 MB）
- ✅ **灵活更新**：模型可以独立更新，无需重新打包 AAR
- ✅ **多应用共享**：不同应用可以使用不同的模型
- ✅ **用户自定义**：用户可以自己选择和替换模型

## 🚀 快速开始

### 第一步：准备 Native 库文件

将你的 `.so` 文件复制到：

```bash
# arm64-v8a 架构
cp /path/to/libsherpa-onnx-jni.so library/src/main/jniLibs/arm64-v8a/
cp /path/to/libonnxruntime.so library/src/main/jniLibs/arm64-v8a/

# armeabi-v7a 架构
cp /path/to/libsherpa-onnx-jni.so library/src/main/jniLibs/armeabi-v7a/
cp /path/to/libonnxruntime.so library/src/main/jniLibs/armeabi-v7a/
```

### 第二步：构建 AAR

#### 方法一：使用一键脚本（推荐）

```bash
cd StreamingASRApp
./build-library-aar.sh
```

脚本会自动：
- ✅ 检查 .so 文件是否存在
- ✅ 清理旧的构建
- ✅ 构建 AAR
- ✅ 显示构建结果和文件大小

#### 方法二：手动构建

```bash
cd StreamingASRApp

# 清理旧构建
./gradlew :library:clean

# 构建 release AAR
./gradlew :library:assembleRelease

# 查看构建结果
ls -lh library/build/outputs/aar/library-release.aar
```

### 第三步：获取 AAR

构建成功后，AAR 文件位于：

```
library/build/outputs/aar/library-release.aar
```

## 📦 使用 AAR

### 在其他项目中集成

#### 1. 复制 AAR 文件

将 `library-release.aar` 复制到目标项目：

```
TargetProject/
└── app/
    └── libs/
        └── library-release.aar  # 放这里
```

#### 2. 配置依赖

在目标项目的 `app/build.gradle.kts` 中：

```kotlin
android {
    // ... 其他配置
}

dependencies {
    // 添加 AAR 依赖
    implementation(files("libs/library-release.aar"))

    // 必需的外部依赖
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

#### 3. 添加权限

在目标项目的 `AndroidManifest.xml` 中：

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
```

#### 4. 准备模型文件

**方法 A：使用 adb 推送模型**

```bash
# 创建模型目录
adb shell mkdir -p /data/data/com.yourcompany.yourapp/files/models

# 推送模型文件
adb push encoder-epoch-99-avg-1.onnx /data/data/com.yourcompany.yourapp/files/models/
adb push decoder-epoch-99-avg-1.onnx /data/data/com.yourcompany.yourapp/files/models/
adb push joiner-epoch-99-avg-1.onnx /data/data/com.yourcompany.yourapp/files/models/
adb push tokens.txt /data/data/com.yourcompany.yourapp/files/models/

# 验证文件已推送
adb shell ls -lh /data/data/com.yourcompany.yourapp/files/models/
```

**方法 B：在应用内下载模型**

```kotlin
// 在你的应用中实现模型下载逻辑
class YourActivity : AppCompatActivity() {
    private suspend fun downloadModels() {
        val modelManager = ModelManager(this)
        val modelDir = modelManager.getModelDir()

        // 下载模型文件到 modelDir
        // 例如从你的服务器下载
        downloadFile("https://yourserver.com/encoder.onnx", File(modelDir, "encoder.onnx"))
        downloadFile("https://yourserver.com/decoder.onnx", File(modelDir, "decoder.onnx"))
        // ...
    }
}
```

**方法 C：从 assets 复制到数据目录**

如果你想在应用首次运行时从 assets 复制模型：

```kotlin
class YourActivity : AppCompatActivity() {
    private fun copyModelsFromAssets() {
        val modelManager = ModelManager(this)
        val modelDir = modelManager.getModelDir()

        // 从你的应用的 assets 复制模型文件
        copyAssetFile("models/encoder.onnx", File(modelDir, "encoder.onnx"))
        copyAssetFile("models/decoder.onnx", File(modelDir, "decoder.onnx"))
        // ...
    }

    private fun copyAssetFile(assetPath: String, destFile: File) {
        assets.open(assetPath).use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
    }
}
```

#### 5. 使用代码

```kotlin
import com.example.streamingasr.ModelManager
import com.example.streamingasr.AudioRecorder

class YourActivity : AppCompatActivity() {
    private lateinit var modelManager: ModelManager
    private var recognizer: OnlineRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 创建 ModelManager
        modelManager = ModelManager(this)

        // 方式一：自动检测模型（推荐）
        recognizer = modelManager.createOnlineRecognizerAuto()

        // 方式二：指定模型类型
        // recognizer = modelManager.createOnlineRecognizer(
        //     ModelManager.ModelType.ZIPFORMER_TRANSDUCER
        // )

        // 方式三：自定义模型文件名
        // val modelFiles = ModelFiles(
        //     encoder = "your-encoder.onnx",
        //     decoder = "your-decoder.onnx",
        //     joiner = "your-joiner.onnx",
        //     tokens = "tokens.txt"
        // )
        // recognizer = modelManager.createOnlineRecognizer(modelFiles)

        if (recognizer == null) {
            // 模型加载失败，显示提示
            showModelInstructions()
        } else {
            // 开始使用语音识别
            startRecognition()
        }
    }

    private fun showModelInstructions() {
        val instructions = modelManager.getModelDownloadInstructions()
        AlertDialog.Builder(this)
            .setTitle("需要模型文件")
            .setMessage(instructions)
            .setPositiveButton("确定", null)
            .show()
    }
}
```

#### 6. Sync 并运行

点击 Android Studio 的 **Sync Project with Gradle Files**，然后就可以运行了！

## ✅ AAR 包含内容

打包后的 AAR 包含：

- ✅ 所有源代码（编译后的 .class 文件）
  - `ModelManager` - 模型管理类（自动从数据目录加载模型）
  - `AudioRecorder` - 音频录制类
  - `MainActivity` - 示例界面（可选使用）
- ✅ Native 库文件（libsherpa-onnx-jni.so, libonnxruntime.so）
- ✅ 资源文件（layouts, values, drawables 等）
- ✅ AndroidManifest.xml（包含权限声明）
- ❌ **不包含**模型文件（需要运行时提供）

使用者**需要**：
- ✅ 将模型文件放到 `/data/data/包名/files/models/` 目录
- ✅ 使用 `ModelManager` 类加载模型

使用者**不需要**：
- ❌ 单独配置 .so 文件路径（已打包在 AAR 中）
- ❌ 手动创建 OnlineRecognizer 配置（`ModelManager` 自动处理）

## 🎉 优点

1. **AAR 体积小**：不包含模型，只有 10-50 MB
2. **灵活部署**：模型可以通过网络下载、adb 推送、或从 assets 复制
3. **模型可更新**：无需重新打包 AAR 即可更换模型
4. **自动化加载**：`ModelManager` 自动检测和加载模型
5. **支持多种模型**：Transducer、Paraformer、CTC 等
6. **保持原项目完整**：原 app 目录未被修改，可以继续开发和测试

## 📋 ModelManager 功能

`ModelManager` 类提供了强大的模型管理功能：

### 1. 自动检测模型

```kotlin
val recognizer = modelManager.createOnlineRecognizerAuto()
```

自动扫描模型目录，识别文件名模式并加载相应的模型。

### 2. 指定模型类型

```kotlin
val recognizer = modelManager.createOnlineRecognizer(
    ModelManager.ModelType.ZIPFORMER_TRANSDUCER
)
```

支持的模型类型：
- `ZIPFORMER_TRANSDUCER` - Transducer 模型
- `PARAFORMER` - Paraformer 模型
- `ZIPFORMER_CTC` - CTC 模型

### 3. 自定义文件名

```kotlin
val modelFiles = ModelFiles(
    encoder = "my-encoder.onnx",
    decoder = "my-decoder.onnx",
    joiner = "my-joiner.onnx",
    tokens = "my-tokens.txt"
)
val recognizer = modelManager.createOnlineRecognizer(modelFiles)
```

灵活指定模型文件名。

### 4. 获取模型目录

```kotlin
val modelDir = modelManager.getModelDir()
// 返回: /data/data/包名/files/models/
```

### 5. 检查模型是否存在

```kotlin
val exists = modelManager.checkModelExists(ModelManager.ModelType.ZIPFORMER_TRANSDUCER)
```

### 6. 列出模型文件

```kotlin
val files = modelManager.listModelFiles()
// 返回模型目录中的所有文件名
```

### 7. 获取下载说明

```kotlin
val instructions = modelManager.getModelDownloadInstructions()
// 返回详细的模型下载和使用说明
```

## ⚠️ 注意事项

### 1. 模型文件路径

模型文件**必须**放在：
```
/data/data/<你的应用包名>/files/models/
```

不能放在其他位置（如 SD 卡、外部存储等），因为 `ModelManager` 固定使用 `context.filesDir`。

### 2. 文件名要求

**Transducer 模型**需要：
- encoder-epoch-99-avg-1.onnx（或包含 "encoder" 的 .onnx 文件）
- decoder-epoch-99-avg-1.onnx（或包含 "decoder" 的 .onnx 文件）
- joiner-epoch-99-avg-1.onnx（或包含 "joiner" 的 .onnx 文件）
- tokens.txt

**Paraformer 模型**需要：
- encoder.int8.onnx
- decoder.int8.onnx
- tokens.txt

**CTC 模型**需要：
- model.int8.onnx（或其他单个 .onnx 文件）
- tokens.txt

使用自动检测模式时，`ModelManager` 会根据文件名模式识别模型类型。

### 3. 权限问题

应用需要有读写 `/data/data/包名/files/` 的权限，这是默认拥有的（应用内部存储）。

如果从外部存储复制模型，需要申请存储权限。

### 4. 首次运行

建议在应用首次运行时检查模型是否存在，如果不存在则提示用户下载或从 assets 复制。

```kotlin
if (!modelManager.checkModelExists(ModelManager.ModelType.ZIPFORMER_TRANSDUCER)) {
    // 显示提示或自动下载
    showModelMissingDialog()
}
```

## 🔧 自定义配置

如需修改 AAR 配置，编辑 `library/build.gradle.kts`：

```kotlin
android {
    namespace = "com.example.streamingasr"
    compileSdk = 34

    defaultConfig {
        minSdk = 24  // 修改最低支持的 Android 版本

        ndk {
            // 修改支持的架构
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
        }
    }
}
```

## 🆘 常见问题

**Q: 为什么不打包模型到 AAR？**
A: 模型文件通常很大（几百 MB），打包到 AAR 会导致：
   - AAR 文件太大，不便传输
   - 模型更新需要重新打包 AAR
   - 无法灵活选择不同的模型

**Q: 如何让用户下载模型？**
A: 可以在应用中实现模型下载功能：
   ```kotlin
   suspend fun downloadModel() {
       val modelDir = modelManager.getModelDir()
       // 从你的服务器下载到 modelDir
   }
   ```

**Q: 能否支持从 SD 卡加载模型？**
A: 当前 `ModelManager` 固定使用 `context.filesDir`。如需支持外部存储，需要修改 `ModelManager` 代码。

**Q: AAR 大小大概多少？**
A: 只包含 .so 文件的 AAR 约 10-50 MB（取决于架构数量）。

**Q: 如何验证模型已正确加载？**
A: 使用 `createOnlineRecognizer()` 返回值检查：
   ```kotlin
   val recognizer = modelManager.createOnlineRecognizerAuto()
   if (recognizer != null) {
       Log.i(TAG, "模型加载成功")
   } else {
       Log.e(TAG, "模型加载失败")
   }
   ```

**Q: 能否同时维护 App 和 Library？**
A: 可以！`app/` 目录保持不变，继续开发；需要更新 AAR 时，将改动同步到 `library/` 并重新构建。

---

**祝你打包顺利！** 🎉

## 📚 相关文档

- `library/README.md` - Library 详细说明
- `app/src/main/java/com/example/streamingasr/ModelManager.kt` - 模型管理源码
- 原项目 README - 应用使用说明
