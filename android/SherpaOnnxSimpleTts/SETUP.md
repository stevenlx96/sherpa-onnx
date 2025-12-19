# 快速设置指南

这是一个**零配置**的简单中文 TTS demo，按以下步骤操作即可运行。

## 📋 环境要求

确认你的开发环境满足以下要求：
- ✅ **JDK 17**
- ✅ **Android Studio Hedgehog** (2023.1.1+)
- ✅ **Android SDK 34**
- ✅ **Gradle 8.6+**（自动使用，无需手动安装）

## 🚀 快速开始（3步）

### 步骤 1: 下载 JNI 库

```bash
cd android/SherpaOnnxSimpleTts
./download_jni_libs.sh
```

这会自动下载预编译的 JNI 库文件（约 50MB）。

### 步骤 2: 构建并安装应用

```bash
./gradlew installDebug
```

或者在 Android Studio 中直接点击 Run ▶️

### 步骤 3: 配置 TTS 模型

**方法 A：使用一键脚本**（推荐）
```bash
./setup_model.sh
```

**方法 B：手动配置**
```bash
# 下载模型
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-melo-tts-zh_en.tar.bz2
tar -xjf vits-melo-tts-zh_en.tar.bz2

# 推送到设备
adb shell mkdir -p /data/data/com.k2fsa.sherpa.onnx.simpletts/files/models/tts/vits-melo-tts-zh_en
adb push vits-melo-tts-zh_en/model.onnx /data/data/com.k2fsa.sherpa.onnx.simpletts/files/models/tts/vits-melo-tts-zh_en/
adb push vits-melo-tts-zh_en/lexicon.txt /data/data/com.k2fsa.sherpa.onnx.simpletts/files/models/tts/vits-melo-tts-zh_en/
adb push vits-melo-tts-zh_en/tokens.txt /data/data/com.k2fsa.sherpa.onnx.simpletts/files/models/tts/vits-melo-tts-zh_en/
```

## ✅ 完成！

现在打开应用，输入中文文字，点击"朗读"按钮即可。

---

## 🐛 常见问题

### Q1: 构建失败 "Minimum supported Gradle version is 8.6"

**解决**：项目已配置使用 Gradle 8.6，清理缓存后重试：
```bash
./gradlew clean
./gradlew build
```

### Q2: 运行时崩溃 "UnsatisfiedLinkError"

**原因**：JNI 库文件未下载。

**解决**：运行 `./download_jni_libs.sh`

### Q3: 应用启动但提示"模型文件未找到"

**原因**：TTS 模型未配置。

**解决**：运行 `./setup_model.sh`

### Q4: 找不到 adb 命令

**解决**：将 Android SDK platform-tools 添加到 PATH：
```bash
export PATH=$PATH:$ANDROID_HOME/platform-tools
```

---

## 📦 项目结构

```
SherpaOnnxSimpleTts/
├── app/
│   ├── src/main/
│   │   ├── java/.../MainActivity.kt    # 主代码
│   │   ├── jniLibs/                    # JNI 库 (download_jni_libs.sh 生成)
│   │   │   ├── arm64-v8a/
│   │   │   ├── armeabi-v7a/
│   │   │   ├── x86_64/
│   │   │   └── x86/
│   │   └── res/layout/                 # UI 布局
│   └── build.gradle                    # App 配置
├── build.gradle                        # 项目配置 (AGP 8.4.0)
├── gradle/wrapper/
│   └── gradle-wrapper.properties       # Gradle 8.6
├── download_jni_libs.sh                # 下载 JNI 库
├── setup_model.sh                      # 配置 TTS 模型
└── README.md                           # 详细文档
```

---

## 🔧 技术栈

| 组件 | 版本 |
|------|------|
| Android Gradle Plugin | 8.4.0 |
| Gradle | 8.6 |
| Kotlin | 1.9.23 |
| Java | 17 |
| compileSdk / targetSdk | 34 |
| minSdk | 21 |
| sherpa-onnx | Latest |

---

## 📚 更多信息

- 详细文档：[README.md](README.md)
- sherpa-onnx 官方：https://k2-fsa.github.io/sherpa/onnx/
- TTS 模型下载：https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models
