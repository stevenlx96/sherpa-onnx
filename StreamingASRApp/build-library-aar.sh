#!/bin/bash
set -e

echo "=================================="
echo "开始构建 StreamingASR AAR 包"
echo "=================================="

# 检查必要的文件
echo ""
echo "检查必要文件..."

# 检查 .so 文件
if [ ! -f "library/src/main/jniLibs/arm64-v8a/libsherpa-onnx-jni.so" ]; then
    echo "❌ 错误: 缺少 library/src/main/jniLibs/arm64-v8a/libsherpa-onnx-jni.so"
    echo "请先将 native 库文件放到 library/src/main/jniLibs/ 目录"
    exit 1
fi

echo "✅ Native 库文件检查完成"
echo ""
echo "📌 注意: 此 AAR 不包含模型文件"
echo "   模型文件需要放到应用的 /data/data/包名/files/models/ 目录"
echo "   使用 ModelManager 类自动加载"
echo ""

# 清理旧的构建
echo "清理旧的构建文件..."
./gradlew :library:clean

# 构建 AAR
echo ""
echo "开始构建 AAR..."
./gradlew :library:assembleRelease

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
    echo "  ✅ 资源文件和布局"
    echo "  ❌ 模型文件（不包含，需要运行时提供）"
    echo ""
    echo "使用方法:"
    echo "1. 将 AAR 文件复制到目标项目的 app/libs/ 目录"
    echo "2. 在 app/build.gradle.kts 中添加依赖"
    echo "3. 将模型文件 push 到设备的 /data/data/包名/files/models/ 目录"
    echo "4. 使用 ModelManager 类自动加载模型"
    echo ""
    echo "详细使用说明请查看 AAR打包说明.md"
    echo ""
else
    echo ""
    echo "❌ 构建失败，请检查错误信息"
    exit 1
fi
