# StreamingASR Library Module

这是一个用于打包 AAR 的 library module，包含了完整的语音识别功能。

## 📂 目录结构

```
library/
├── src/
│   └── main/
│       ├── java/                  # 源代码（已从 app 复制）
│       ├── res/                   # 资源文件
│       ├── assets/               # 模型文件（需要手动放置）
│       │   ├── sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/
│       │   │   ├── model.int8.onnx
│       │   │   └── tokens.txt
│       │   └── silero_vad.onnx
│       ├── jniLibs/              # Native 库（需要手动放置）
│       │   ├── arm64-v8a/
│       │   │   ├── libsherpa-onnx-jni.so
│       │   │   └── libonnxruntime.so
│       │   └── armeabi-v7a/
│       │       ├── libsherpa-onnx-jni.so
│       │       └── libonnxruntime.so
│       └── AndroidManifest.xml
├── build.gradle.kts              # Library 构建配置
└── AAR构建指南.md                # 详细构建指南
```

## 🚀 快速开始

### 1. 准备文件

**放置 native 库 (.so 文件):**
```bash
cp your-so-files/* library/src/main/jniLibs/arm64-v8a/
cp your-so-files/* library/src/main/jniLibs/armeabi-v7a/
```

**放置模型文件:**
```bash
cp -r your-model-directory library/src/main/assets/
```

### 2. 构建 AAR

使用快捷脚本（推荐）：
```bash
cd StreamingASRApp
./build-library-aar.sh
```

或手动构建：
```bash
./gradlew :library:assembleRelease
```

### 3. 获取 AAR

构建成功后，AAR 文件在：
```
library/build/outputs/aar/library-release.aar
```

## 📦 AAR 内容

打包的 AAR 包含：
- ✅ 所有 Kotlin/Java 源代码（编译后的 class 文件）
- ✅ Native 库 (.so 文件) for arm64-v8a 和 armeabi-v7a
- ✅ 模型文件（如果放置在 assets 目录）
- ✅ 所有资源文件和布局
- ✅ AndroidManifest.xml 配置

## 💡 特点

- **开箱即用**: 使用者只需添加 AAR 依赖即可
- **无需额外配置**: 不需要单独配置 .so 文件路径
- **包含所有依赖**: 所有必要的代码和资源都打包在内

## 📋 使用示例

目标项目中的 `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation(files("libs/library-release.aar"))

    // 必需的外部依赖
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

在代码中使用：
```kotlin
import com.example.streamingasr.AudioRecorder
import com.example.streamingasr.MainActivity

// 直接使用 library 中的类
val recorder = AudioRecorder()
```

## 🔧 自定义

如果需要修改 library 的配置，编辑 `library/build.gradle.kts` 文件。

如果需要添加/修改源代码，编辑 `library/src/main/java/` 目录下的文件。

## ⚠️ 注意事项

1. **AAR 文件大小**: 由于包含了 native 库和模型文件，AAR 可能会很大（几十MB到几百MB）
2. **架构支持**: 目前只支持 arm64-v8a 和 armeabi-v7a，如需其他架构请修改 `ndk.abiFilters`
3. **模型更新**: 如需更换模型，重新放置文件后需重新构建 AAR
