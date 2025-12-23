#!/bin/bash
set -e

echo "=================================="
echo "开始构建 SherpaOnnxTts AAR 包"
echo "=================================="

# 检查必要的文件
echo ""
echo "检查必要文件..."

# 检查 .so 文件
if [ ! -f "library/src/main/jniLibs/arm64-v8a/libsherpa-onnx-jni.so" ]; then
    echo "❌ 错误: 缺少 library/src/main/jniLibs/arm64-v8a/libsherpa-onnx-jni.so"
    echo "请先运行 ./download-libs.sh 下载 native 库文件"
    exit 1
fi

echo "✅ Native 库文件检查完成"
echo ""
echo "📌 注意: 此 AAR 不包含模型文件"
echo "   模型文件需要放到应用的 /data/data/包名/files/models/tts/ 目录"
echo "   使用 TtsManager 类自动加载"
echo ""

# 清理旧的构建
echo "清理旧的构建文件..."
gradle :library:clean

# 构建 AAR
echo ""
echo "开始构建 AAR..."
gradle :library:assembleRelease

# 检查构建结果
if [ -f "library/build/outputs/aar/library-release.aar" ]; then
    echo ""
    echo "=================================="
    echo "✅ 构建成功!"
    echo "=================================="
    echo ""
    echo "AAR 文件位置:"
    echo "  $(pwd)/library/build/outputs/aar/library-release.aar"
    echo ""

    # 显示 AAR 文件大小
    SIZE=$(du -h library/build/outputs/aar/library-release.aar | cut -f1)
    echo "文件大小: $SIZE"
    echo ""

    echo "📦 AAR 包含内容:"
    echo "  ✅ 所有源代码（编译后）"
    echo "  ✅ Native 库 (.so 文件)"
    echo "  ✅ Tts.kt API"
    echo "  ✅ TtsManager 管理类"
    echo "  ❌ 模型文件（不包含，需要运行时提供）"
    echo ""
    echo "使用方法:"
    echo "1. 将 AAR 文件复制到目标项目的 app/libs/ 目录"
    echo "2. 在 app/build.gradle.kts 中添加依赖:"
    echo "   implementation(files(\"libs/library-release.aar\"))"
    echo "3. 将模型文件 push 到设备的 /data/data/包名/files/models/tts/ 目录"
    echo "4. 使用 TtsManager 类进行 TTS:"
    echo ""
    echo "   val ttsManager = TtsManager(context)"
    echo "   ttsManager.initialize(\"matcha-icefall-zh-baker\", \"matcha\")"
    echo "   ttsManager.speak(\"你好\")"
    echo ""
else
    echo ""
    echo "❌ 构建失败，请检查错误信息"
    exit 1
fi
