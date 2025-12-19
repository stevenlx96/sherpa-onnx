#!/bin/bash

# 下载并部署HomophoneReplacer文件到Android设备
# 用法: ./download-homophone-files.sh

set -e

echo "================================================"
echo "下载 HomophoneReplacer 文件"
echo "================================================"

# 创建临时目录
TEMP_DIR="./temp_hr_files"
mkdir -p "$TEMP_DIR"
cd "$TEMP_DIR"

echo ""
echo "步骤 1: 下载 lexicon.txt ..."
if [ ! -f "lexicon.txt" ]; then
    wget -q --show-progress https://github.com/k2-fsa/sherpa-onnx/releases/download/hr-files/lexicon.txt
    echo "✓ lexicon.txt 下载完成"
else
    echo "✓ lexicon.txt 已存在，跳过下载"
fi

echo ""
echo "步骤 2: 下载 replace.fst ..."
if [ ! -f "replace.fst" ]; then
    wget -q --show-progress https://github.com/k2-fsa/sherpa-onnx/releases/download/hr-files/replace.fst
    echo "✓ replace.fst 下载完成"
else
    echo "✓ replace.fst 已存在，跳过下载"
fi

echo ""
echo "================================================"
echo "部署文件到Android设备"
echo "================================================"

# 检查设备连接
echo ""
echo "步骤 3: 检查设备连接 ..."
if ! adb devices | grep -q "device$"; then
    echo "❌ 错误: 未检测到Android设备"
    echo "   请确保："
    echo "   1. 设备已通过USB连接"
    echo "   2. 设备已开启USB调试"
    echo "   3. 已授权此电脑进行调试"
    exit 1
fi
echo "✓ 设备连接正常"

# 推送文件到临时目录
echo ""
echo "步骤 4: 推送文件到设备临时目录 ..."
adb push lexicon.txt /data/local/tmp/
adb push replace.fst /data/local/tmp/
echo "✓ 文件推送完成"

# 复制到应用私有目录
echo ""
echo "步骤 5: 复制到应用目录 ..."
echo "   目标路径: /data/data/com.example.streamingasr/files/models/"
adb shell "run-as com.example.streamingasr mkdir -p /data/data/com.example.streamingasr/files/models" 2>/dev/null || true
adb shell "run-as com.example.streamingasr cp /data/local/tmp/lexicon.txt /data/data/com.example.streamingasr/files/models/"
adb shell "run-as com.example.streamingasr cp /data/local/tmp/replace.fst /data/data/com.example.streamingasr/files/models/"
echo "✓ 文件复制完成"

# 清理临时文件
echo ""
echo "步骤 6: 清理设备临时文件 ..."
adb shell "rm /data/local/tmp/lexicon.txt"
adb shell "rm /data/local/tmp/replace.fst"
echo "✓ 临时文件已清理"

# 验证文件
echo ""
echo "步骤 7: 验证文件 ..."
echo "应用目录中的文件："
adb shell "run-as com.example.streamingasr ls -lh /data/data/com.example.streamingasr/files/models/" | grep -E "lexicon.txt|replace.fst" || echo "   未找到文件"

cd ..

echo ""
echo "================================================"
echo "✓ 部署完成！"
echo "================================================"
echo ""
echo "HomophoneReplacer 功能说明:"
echo "  - lexicon.txt: 词典文件 (定义发音到文字的映射)"
echo "  - replace.fst: 替换规则 (定义同音字替换规则)"
echo ""
echo "功能示例:"
echo "  ❌ '在坐的各位' → ✅ '在座的各位'"
echo "  ❌ '因该这样做' → ✅ '应该这样做'"
echo ""
echo "现在重启应用，HomophoneReplacer 将自动启用！"
echo ""
echo "如需卸载这些文件，运行:"
echo "  adb shell \"run-as com.example.streamingasr rm /data/data/com.example.streamingasr/files/models/lexicon.txt\""
echo "  adb shell \"run-as com.example.streamingasr rm /data/data/com.example.streamingasr/files/models/replace.fst\""
echo ""
