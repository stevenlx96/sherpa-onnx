#!/bin/bash

# 复制编译好的 AAR 到 demo/app/libs/ 目录
# 使 demo 项目可以独立测试 AAR

set -e

echo "📦 复制 AAR 到 demo/app/libs/ ..."

# 检查 AAR 是否存在
if [ ! -f "library/build/outputs/aar/library-release.aar" ]; then
    echo "❌ AAR 文件不存在，请先编译 library 模块："
    echo "   ./gradlew :library:assembleRelease"
    exit 1
fi

# 创建 demo/app/libs 目录
mkdir -p demo/app/libs

# 复制 AAR
cp library/build/outputs/aar/library-release.aar demo/app/libs/

echo "✅ AAR 已复制到 demo/app/libs/library-release.aar"
echo ""
echo "现在可以编译 demo 项目测试 AAR："
echo "   cd demo && ./gradlew assembleDebug"
