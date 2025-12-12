#!/bin/bash
# HomophoneReplacer 文件部署脚本 (Bash)
# 用法: ./deploy-homophone-replacer.sh

echo "================================================"
echo "部署 HomophoneReplacer 文件到 Android 设备"
echo "================================================"
echo ""

# 创建临时目录
TEMP_DIR="./temp_hr_files"
mkdir -p "$TEMP_DIR"

# 下载文件
echo "步骤 1: 下载 lexicon.txt ..."
LEXICON_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/hr-files/lexicon.txt"
LEXICON_PATH="$TEMP_DIR/lexicon.txt"

if [ ! -f "$LEXICON_PATH" ]; then
    if curl -L "$LEXICON_URL" -o "$LEXICON_PATH" 2>/dev/null; then
        SIZE=$(du -h "$LEXICON_PATH" | cut -f1)
        echo "✓ lexicon.txt 下载完成 ($SIZE)"
    else
        echo "❌ 下载失败"
        exit 1
    fi
else
    echo "✓ lexicon.txt 已存在，跳过下载"
fi

echo ""
echo "步骤 2: 下载 replace.fst ..."
FST_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/hr-files/replace.fst"
FST_PATH="$TEMP_DIR/replace.fst"

if [ ! -f "$FST_PATH" ]; then
    if curl -L "$FST_URL" -o "$FST_PATH" 2>/dev/null; then
        SIZE=$(du -h "$FST_PATH" | cut -f1)
        echo "✓ replace.fst 下载完成 ($SIZE)"
    else
        echo "❌ 下载失败"
        exit 1
    fi
else
    echo "✓ replace.fst 已存在，跳过下载"
fi

echo ""
echo "================================================"
echo "部署到 Android 设备"
echo "================================================"

# 检查 ADB
echo ""
echo "步骤 3: 检查 ADB 和设备连接 ..."
if adb devices | grep -q "device$"; then
    echo "✓ 设备连接正常"
else
    echo "❌ 错误: 未检测到 Android 设备"
    echo "   请确保:"
    echo "   1. 设备已通过 USB 连接"
    echo "   2. 设备已开启 USB 调试"
    echo "   3. 已授权此电脑进行调试"
    exit 1
fi

# 推送文件到临时目录
echo ""
echo "步骤 4: 推送文件到设备临时目录 ..."
adb push "$LEXICON_PATH" /data/local/tmp/ > /dev/null 2>&1
adb push "$FST_PATH" /data/local/tmp/ > /dev/null 2>&1
echo "✓ 文件推送完成"

# 创建目标目录
echo ""
echo "步骤 5: 创建应用目录 ..."
adb shell "run-as com.example.streamingasr mkdir -p /data/data/com.example.streamingasr/files/models/asr" 2> /dev/null
echo "✓ 目录创建完成"

# 复制到应用私有目录
echo ""
echo "步骤 6: 复制到应用目录 ..."
echo "   目标路径: /data/data/com.example.streamingasr/files/models/asr/"
adb shell "run-as com.example.streamingasr cp /data/local/tmp/lexicon.txt /data/data/com.example.streamingasr/files/models/asr/"
adb shell "run-as com.example.streamingasr cp /data/local/tmp/replace.fst /data/data/com.example.streamingasr/files/models/asr/"
echo "✓ 文件复制完成"

# 清理临时文件
echo ""
echo "步骤 7: 清理设备临时文件 ..."
adb shell "rm /data/local/tmp/lexicon.txt"
adb shell "rm /data/local/tmp/replace.fst"
echo "✓ 临时文件已清理"

# 验证文件
echo ""
echo "步骤 8: 验证部署 ..."
echo "应用目录中的文件:"
adb shell "run-as com.example.streamingasr ls -lh /data/data/com.example.streamingasr/files/models/asr/" 2>&1 | grep -E "lexicon.txt|replace.fst"

echo ""
echo "================================================"
echo "✓ 部署完成！"
echo "================================================"
echo ""
echo "HomophoneReplacer 功能说明:"
echo "  - lexicon.txt: 词典文件 (汉字到拼音的映射)"
echo "  - replace.fst: 替换规则 (同音字替换规则)"
echo ""
echo "功能示例:"
echo "  ❌ '金安达' → ✅ '津安达'"
echo "  ❌ '捷安达' → ✅ '津安达'"
echo "  ❌ '吉安达' → ✅ '津安达'"
echo "  ❌ '在坐的各位' → ✅ '在座的各位'"
echo "  ❌ '因该这样做' → ✅ '应该这样做'"
echo ""
echo "现在重新编译安装应用，HomophoneReplacer 将自动启用！"
echo "日志中会显示: 'HomophoneReplacer enabled'"
echo ""
echo "下一步操作:"
echo "  1. 重新编译应用: cd StreamingASRApp && ./gradlew assembleDebug"
echo "  2. 安装到设备: adb install -r app/build/outputs/apk/debug/app-debug.apk"
echo "  3. 启动应用测试"
echo ""
