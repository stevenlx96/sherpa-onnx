#!/bin/bash
# 下载预编译的 JNI 库文件
# 方法：从官方发布的 TTS APK 中提取 SO 文件

set -e

echo "================================================"
echo "  下载 sherpa-onnx JNI 库文件"
echo "================================================"
echo ""

# 创建 jniLibs 目录
mkdir -p app/src/main/jniLibs/{arm64-v8a,armeabi-v7a,x86_64,x86}

# 方法 1: 从官方 TTS APK 提取（推荐）
APK_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/sherpa-onnx-v1.10.30-arm64-v8a-tts-en-kokoro.apk"

echo "📥 下载官方 TTS APK (约 30MB)..."
if command -v wget &> /dev/null; then
    wget -q --show-progress "${APK_URL}" -O sherpa-onnx-tts.apk || {
        echo "⚠️  wget 下载失败，尝试使用 curl..."
        curl -L -# "${APK_URL}" -o sherpa-onnx-tts.apk
    }
elif command -v curl &> /dev/null; then
    curl -L -# "${APK_URL}" -o sherpa-onnx-tts.apk
else
    echo "❌ 错误: 未找到 wget 或 curl"
    echo "请手动下载 APK 并提取 SO 文件"
    exit 1
fi

echo ""
echo "📦 解压 APK..."
unzip -q sherpa-onnx-tts.apk -d sherpa-onnx-extracted

echo "📤 复制 JNI 库文件..."
# APK 中的 SO 文件在 lib/ 目录下
if [ -d "sherpa-onnx-extracted/lib" ]; then
    for arch in arm64-v8a armeabi-v7a x86_64 x86; do
        if [ -d "sherpa-onnx-extracted/lib/$arch" ]; then
            echo "  → $arch"
            cp sherpa-onnx-extracted/lib/$arch/*.so app/src/main/jniLibs/$arch/ 2>/dev/null && \
            echo "    ✓ 已复制 $(ls sherpa-onnx-extracted/lib/$arch/*.so | wc -l) 个文件" || \
            echo "    ⚠️  该架构没有 SO 文件"
        fi
    done
else
    echo "❌ 错误: 在 APK 中未找到 lib/ 目录"
    ls -la sherpa-onnx-extracted/
    exit 1
fi

echo ""
echo "🧹 清理临时文件..."
rm -rf sherpa-onnx-tts.apk sherpa-onnx-extracted

echo ""
echo "================================================"
echo "  ✅ JNI 库文件下载完成！"
echo "================================================"
echo ""
echo "已安装的库文件："
for arch in arm64-v8a armeabi-v7a x86_64 x86; do
    so_files=$(find app/src/main/jniLibs/$arch -name "*.so" 2>/dev/null | wc -l)
    if [ $so_files -gt 0 ]; then
        echo "  📱 $arch: $so_files 个文件"
        ls -lh app/src/main/jniLibs/$arch/*.so | awk '{print "     - " $9 " (" $5 ")"}'
    fi
done
echo ""
echo "🎉 现在可以构建项目了: ./gradlew assembleDebug"
echo ""
