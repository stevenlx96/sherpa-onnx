#!/bin/bash
# 下载预编译的 JNI 库文件
# 这些库文件从官方发布的 APK 中提取

set -e

echo "================================================"
echo "  下载 sherpa-onnx JNI 库文件"
echo "================================================"
echo ""

# 创建 jniLibs 目录
mkdir -p app/src/main/jniLibs/{arm64-v8a,armeabi-v7a,x86_64,x86}

# GitHub Release URL
RELEASE_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download"
VERSION="v1.10.30"  # 使用最新版本，可以根据需要调整

echo "📥 下载预编译的 AAR 文件..."
wget -q "${RELEASE_URL}/asr-models/sherpa-onnx-${VERSION}.aar" -O sherpa-onnx.aar

echo "📦 解压 AAR 文件..."
unzip -q sherpa-onnx.aar -d sherpa-onnx-extracted

echo "📤 复制 JNI 库文件..."
# 复制各个架构的 SO 文件
if [ -d "sherpa-onnx-extracted/jni" ]; then
    for arch in arm64-v8a armeabi-v7a x86_64 x86; do
        if [ -d "sherpa-onnx-extracted/jni/$arch" ]; then
            echo "  → $arch"
            cp sherpa-onnx-extracted/jni/$arch/*.so app/src/main/jniLibs/$arch/ 2>/dev/null || true
        fi
    done
fi

echo "🧹 清理临时文件..."
rm -rf sherpa-onnx.aar sherpa-onnx-extracted

echo ""
echo "✅ JNI 库文件下载完成！"
echo ""
echo "已安装的库："
find app/src/main/jniLibs -name "*.so" -exec ls -lh {} \;
echo ""
