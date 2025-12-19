#!/bin/bash

# 打包 Streaming ASR Library 为 AAR 文件

set -e

echo "================================================"
echo "构建 Streaming ASR Library AAR"
echo "================================================"

# 检查是否在正确的目录
if [ ! -f "settings.gradle.kts" ]; then
    echo "❌ 错误: 请在 StreamingASRApp 根目录运行此脚本"
    exit 1
fi

echo ""
echo "步骤 1: 清理旧的构建文件 ..."
./gradlew clean

echo ""
echo "步骤 2: 构建 Release AAR ..."
./gradlew :app:assembleRelease

echo ""
echo "步骤 3: 构建 Debug AAR ..."
./gradlew :app:assembleDebug

echo ""
echo "================================================"
echo "✓ 构建完成！"
echo "================================================"

# 显示生成的文件
echo ""
echo "生成的 AAR 文件:"
AAR_DIR="app/build/outputs/aar"
if [ -d "$AAR_DIR" ]; then
    ls -lh "$AAR_DIR"/*.aar
    echo ""
    echo "文件位置: $AAR_DIR/"
    echo ""
    echo "Release AAR: $AAR_DIR/app-release.aar"
    echo "Debug AAR: $AAR_DIR/app-debug.aar"
else
    echo "⚠️  未找到 AAR 文件目录"
fi

echo ""
echo "================================================"
echo "使用方法"
echo "================================================"
echo ""
echo "1. 将 AAR 文件复制到其他项目:"
echo "   cp $AAR_DIR/app-release.aar /path/to/your/project/libs/"
echo ""
echo "2. 在项目的 build.gradle 中添加依赖:"
echo "   implementation(files(\"libs/app-release.aar\"))"
echo ""
echo "3. 或者发布到 Maven 本地仓库:"
echo "   ./gradlew publishToMavenLocal"
echo ""
