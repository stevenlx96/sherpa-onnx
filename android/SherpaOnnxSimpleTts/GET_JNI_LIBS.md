# 获取 JNI 库文件的三种方法

## 当前状态
项目已创建完成，但 `app/src/main/jniLibs/` 目录下缺少 .so 文件。

## 方法一：从 Maven 中央仓库下载（最简单）

```bash
cd android/SherpaOnnxSimpleTts

# 下载最新的 AAR 包
VERSION="1.10.30"
wget "https://repo1.maven.org/maven2/com/k2fsa/sherpa-onnx/${VERSION}/sherpa-onnx-${VERSION}.aar"

# 解压 AAR（它实际上是个 ZIP 文件）
unzip "sherpa-onnx-${VERSION}.aar" -d tmp_aar

# 复制 .so 文件
cp -r tmp_aar/jni/* app/src/main/jniLibs/

# 清理
rm -rf tmp_aar "sherpa-onnx-${VERSION}.aar"

echo "完成！"
find app/src/main/jniLibs -name "*.so"
```

## 方法二：使用项目根目录的构建脚本

**前提条件：** 需要安装 Android NDK 并设置 `ANDROID_NDK` 环境变量

```bash
# 1. 设置 NDK 路径（根据你的实际安装路径修改）
export ANDROID_NDK=/path/to/android/ndk/25.x.x

# 2. 返回项目根目录
cd /home/user/sherpa-onnx

# 3. 构建 ARM64 架构（最常用）
./build-android-arm64-v8a.sh

# 4. 复制生成的 .so 文件
cp build-android-arm64-v8a/install/lib/*.so \
   android/SherpaOnnxSimpleTts/app/src/main/jniLibs/arm64-v8a/

# 5. （可选）构建其他架构
./build-android-armv7-eabi.sh
cp build-android-armv7-eabi/install/lib/*.so \
   android/SherpaOnnxSimpleTts/app/src/main/jniLibs/armeabi-v7a/
```

## 方法三：从 GitHub Releases 手动下载

**如果网络代理限制，可以在浏览器中手动下载：**

1. 访问：https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.12.13
2. 下载：`sherpa-onnx-v1.12.13-android.tar.bz2`
3. 解压到本地
4. 将 `jni/` 目录下的文件复制到项目：

```bash
# 假设你下载并解压到了 ~/Downloads/
cd /home/user/sherpa-onnx/android/SherpaOnnxSimpleTts

# 复制 .so 文件
cp ~/Downloads/sherpa-onnx-v1.12.13-android/jni/arm64-v8a/*.so \
   app/src/main/jniLibs/arm64-v8a/
cp ~/Downloads/sherpa-onnx-v1.12.13-android/jni/armeabi-v7a/*.so \
   app/src/main/jniLibs/armeabi-v7a/
cp ~/Downloads/sherpa-onnx-v1.12.13-android/jni/x86_64/*.so \
   app/src/main/jniLibs/x86_64/
cp ~/Downloads/sherpa-onnx-v1.12.13-android/jni/x86/*.so \
   app/src/main/jniLibs/x86/
```

## 验证安装

```bash
# 检查是否成功安装
find app/src/main/jniLibs -name "*.so"

# 应该看到类似这样的输出：
# app/src/main/jniLibs/arm64-v8a/libsherpa-onnx-jni.so
# app/src/main/jniLibs/arm64-v8a/libonnxruntime.so
# ...
```

## 获取 .so 后的下一步

```bash
# 构建 APK
./gradlew assembleDebug

# APK 位置
# app/build/outputs/apk/debug/app-debug.apk
```

---

**推荐：方法一最简单，如果网络畅通的话。**
