#!/bin/bash
# 快速配置脚本 - 自动下载并配置 TTS 模型
#
# 使用方法:
#   chmod +x setup_model.sh
#   ./setup_model.sh

set -e  # 遇到错误立即退出

echo "================================================"
echo "  简单中文 TTS - 模型配置脚本"
echo "================================================"
echo ""

# 检查 adb 是否可用
if ! command -v adb &> /dev/null; then
    echo "❌ 错误: 未找到 adb 命令"
    echo "请先安装 Android SDK 并配置 adb"
    exit 1
fi

# 检查设备连接
if ! adb devices | grep -q "device$"; then
    echo "❌ 错误: 没有连接的 Android 设备"
    echo "请连接设备并启用 USB 调试"
    exit 1
fi

echo "✅ 设备已连接"
echo ""

# 1. 下载模型（如果还没下载）
if [ ! -d "vits-melo-tts-zh_en" ]; then
    echo "📥 正在下载模型文件 (约 100MB)..."
    if command -v wget &> /dev/null; then
        wget https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-melo-tts-zh_en.tar.bz2
    elif command -v curl &> /dev/null; then
        curl -L -O https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-melo-tts-zh_en.tar.bz2
    else
        echo "❌ 错误: 未找到 wget 或 curl"
        echo "请手动下载: https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-melo-tts-zh_en.tar.bz2"
        exit 1
    fi

    echo "📦 正在解压..."
    tar -xjf vits-melo-tts-zh_en.tar.bz2
    rm vits-melo-tts-zh_en.tar.bz2
    echo "✅ 模型文件已准备好"
else
    echo "✅ 模型文件已存在，跳过下载"
fi
echo ""

# 检查必需文件
echo "🔍 检查模型文件..."
REQUIRED_FILES=("model.onnx" "lexicon.txt" "tokens.txt")
for file in "${REQUIRED_FILES[@]}"; do
    if [ ! -f "vits-melo-tts-zh_en/$file" ]; then
        echo "❌ 错误: 缺少文件 $file"
        exit 1
    fi
    echo "  ✓ $file"
done
echo ""

# 2. 创建目录
echo "📁 创建应用目录..."
adb shell mkdir -p /data/data/com.k2fsa.sherpa.onnx.simpletts/files/models/tts/vits-melo-tts-zh_en
echo "✅ 目录已创建"
echo ""

# 3. 推送模型文件
echo "📤 推送模型文件到设备..."
echo "  → model.onnx"
adb push vits-melo-tts-zh_en/model.onnx /data/data/com.k2fsa.sherpa.onnx.simpletts/files/models/tts/vits-melo-tts-zh_en/
echo "  → lexicon.txt"
adb push vits-melo-tts-zh_en/lexicon.txt /data/data/com.k2fsa.sherpa.onnx.simpletts/files/models/tts/vits-melo-tts-zh_en/
echo "  → tokens.txt"
adb push vits-melo-tts-zh_en/tokens.txt /data/data/com.k2fsa.sherpa.onnx.simpletts/files/models/tts/vits-melo-tts-zh_en/
echo "✅ 文件已推送"
echo ""

# 4. 验证
echo "✅ 验证文件..."
adb shell ls -lh /data/data/com.k2fsa.sherpa.onnx.simpletts/files/models/tts/vits-melo-tts-zh_en/
echo ""

echo "================================================"
echo "  ✅ 配置完成！"
echo "================================================"
echo ""
echo "现在可以启动应用并使用 TTS 功能了！"
echo ""
