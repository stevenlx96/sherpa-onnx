#!/bin/bash
set -e

echo "=================================="
echo "复制 AAR 到 Demo 项目"
echo "=================================="

AAR_SOURCE="library/build/outputs/aar/library-release.aar"
AAR_TARGET="demo/app/libs/library-release.aar"

# 检查 AAR 是否存在
if [ ! -f "$AAR_SOURCE" ]; then
    echo "❌ 错误: AAR 文件不存在"
    echo "   路径: $AAR_SOURCE"
    echo ""
    echo "请先构建 AAR:"
    echo "  ./build-library-aar.sh"
    exit 1
fi

# 创建目标目录
mkdir -p demo/app/libs

# 复制 AAR
echo "复制 AAR 文件..."
cp "$AAR_SOURCE" "$AAR_TARGET"

# 显示信息
if [ -f "$AAR_TARGET" ]; then
    SIZE=$(du -h "$AAR_TARGET" | cut -f1)
    echo ""
    echo "✅ AAR 复制成功!"
    echo ""
    echo "源文件: $AAR_SOURCE"
    echo "目标文件: $AAR_TARGET"
    echo "文件大小: $SIZE"
    echo ""
    echo "现在可以构建 Demo 项目了:"
    echo "  cd demo"
    echo "  gradle :app:assembleDebug"
else
    echo "❌ 复制失败"
    exit 1
fi
