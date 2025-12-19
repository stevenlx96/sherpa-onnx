# 快速设置指南

## 第一步：获取 JNI 库

运行下载脚本获取所需的 native 库文件：

```bash
cd android/SherpaOnnxSimpleTts
./download_jni_libs.sh
```

**如果下载失败**，请从项目根目录运行构建脚本：

```bash
cd ../..
./build-android-arm64-v8a.sh    # 构建 ARM64 库
./build-android-armv7-eabi.sh   # 构建 ARMv7 库
```

然后复制生成的 .so 文件到 jniLibs 目录：

```bash
cd android/SherpaOnnxSimpleTts
cp ../../build-android-arm64-v8a/install/lib/*.so app/src/main/jniLibs/arm64-v8a/
cp ../../build-android-armv7-eabi/install/lib/*.so app/src/main/jniLibs/armeabi-v7a/
```

## 第二步：构建 APK

```bash
./gradlew assembleDebug
```

APK 位置：`app/build/outputs/apk/debug/app-debug.apk`

## 第三步：安装并准备模型文件

1. 安装 APK 到设备：
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

2. 下载中文 TTS 模型（如 vits-melo-tts-zh_en）

3. 推送模型文件到设备：
   ```bash
   adb push /path/to/model.onnx /data/data/com.k2fsa.sherpa.onnx.tts/files/models/tts/vits-melo-tts-zh_en/
   adb push /path/to/lexicon.txt /data/data/com.k2fsa.sherpa.onnx.tts/files/models/tts/vits-melo-tts-zh_en/
   adb push /path/to/tokens.txt /data/data/com.k2fsa.sherpa.onnx.tts/files/models/tts/vits-melo-tts-zh_en/
   ```

## 完成！

现在可以启动应用，输入中文文字并点击"开始朗读"按钮了。

## 常见问题

**Q: 应用崩溃，提示 UnsatisfiedLinkError**
A: 请确保已完成第一步，将 .so 文件放到 jniLibs 目录。

**Q: 提示模型文件未找到**
A: 请确保模型文件路径正确：`/data/data/com.k2fsa.sherpa.onnx.tts/files/models/tts/vits-melo-tts-zh_en/`

**Q: 构建脚本需要什么环境**
A: 需要安装 Android NDK，并设置 `ANDROID_NDK` 环境变量。

详细信息请参阅 [README.md](README.md)。
