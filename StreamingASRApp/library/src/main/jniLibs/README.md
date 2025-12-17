# JNI 库目录

## 说明

此目录用于存放 Sherpa-ONNX 的 JNI 库（.so 文件），这些库会被打包进 AAR。

## 目录结构

```
jniLibs/
  ├── arm64-v8a/
  │   └── libsherpa-onnx-jni.so
  └── armeabi-v7a/
      └── libsherpa-onnx-jni.so
```

## 如何获取 .so 文件

### 方法 1：从 app 模块复制（推荐）

如果你的 app 模块已经有 .so 文件：

```bash
cp -r app/src/main/jniLibs/* library/src/main/jniLibs/
```

### 方法 2：从预编译包复制

1. 下载 Sherpa-ONNX Android 预编译库：
   ```
   https://github.com/k2-fsa/sherpa-onnx/releases
   ```

2. 解压后复制 .so 文件到对应的架构目录

### 方法 3：手动编译

如果你需要自己编译 Sherpa-ONNX：

```bash
cd sherpa-onnx
./build-android-arm64-v8a.sh
./build-android-armeabi-v7a.sh
```

编译完成后，复制生成的 `libsherpa-onnx-jni.so` 到对应目录。

## 验证

确保每个架构目录都有 `libsherpa-onnx-jni.so` 文件：

```bash
ls -lh library/src/main/jniLibs/arm64-v8a/libsherpa-onnx-jni.so
ls -lh library/src/main/jniLibs/armeabi-v7a/libsherpa-onnx-jni.so
```

## 注意事项

- ⚠️ 这些 .so 文件**不会**被提交到 git（已添加到 .gitignore）
- ⚠️ 每次 clone 项目后需要重新复制 .so 文件
- ✅ 编译 AAR 时会自动打包这些 .so 文件
- ✅ 使用 AAR 的项目不需要单独部署 .so 文件
