# AAR 打包指南

## 📁 第一步：准备文件

### 1. 放置 native 库文件 (.so)

将你的 `.so` 文件放到以下目录：

```
library/src/main/jniLibs/
├── arm64-v8a/
│   ├── libsherpa-onnx-jni.so
│   └── libonnxruntime.so
└── armeabi-v7a/
    ├── libsherpa-onnx-jni.so
    └── libonnxruntime.so
```

### 2. 放置模型文件

将你的模型文件放到：

```
library/src/main/assets/
├── sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/
│   ├── model.int8.onnx
│   └── tokens.txt
└── silero_vad.onnx
```

或者任何你需要的模型文件。

## 🔨 第二步：构建 AAR

在项目根目录执行：

```bash
./gradlew :library:assembleRelease
```

构建成功后，AAR 文件会生成在：

```
library/build/outputs/aar/library-release.aar
```

## 📦 第三步：使用 AAR

### 在其他项目中使用这个 AAR：

1. 将 `library-release.aar` 复制到目标项目的 `app/libs/` 目录

2. 在目标项目的 `app/build.gradle.kts` 中添加：

```kotlin
dependencies {
    implementation(files("libs/library-release.aar"))

    // 以下依赖是必需的
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

3. 在 `AndroidManifest.xml` 中添加必要的权限：

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
```

4. Sync 项目后就可以直接使用了！

## ✅ 优点

- **开箱即用**：AAR 已经包含了所有的 .so 文件和模型文件
- **无需额外配置**：使用者不需要单独配置 native 库路径
- **跨架构支持**：支持 arm64-v8a 和 armeabi-v7a 两种架构

## 📝 注意事项

- 确保 .so 文件和模型文件都已经放到对应目录后再构建 AAR
- AAR 文件会比较大（因为包含了 native 库和模型）
- 如果模型文件很大，考虑是否需要动态下载
