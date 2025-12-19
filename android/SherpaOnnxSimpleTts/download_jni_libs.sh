#!/bin/bash

# 下载 sherpa-onnx Android JNI 库
# 使用方法：./download_jni_libs.sh

set -e

VERSION="1.12.13"
BASE_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v${VERSION}"
TARGET_DIR="app/src/main/jniLibs"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "开始下载 sherpa-onnx JNI 库 (版本 ${VERSION})..."

# 创建临时目录
TMP_DIR=$(mktemp -d)
cd "$TMP_DIR"

# 下载 Android 预编译包
ARCHIVE_FILE="sherpa-onnx-v${VERSION}-android.tar.bz2"
echo "下载 ${ARCHIVE_FILE}..."
wget -c "${BASE_URL}/${ARCHIVE_FILE}" || curl -L -o "${ARCHIVE_FILE}" "${BASE_URL}/${ARCHIVE_FILE}"

# 解压文件
echo "解压文件..."
tar xf "${ARCHIVE_FILE}"

# 查找解压后的目录
EXTRACTED_DIR=$(find . -maxdepth 1 -type d -name "sherpa-onnx-*-android" | head -1)

if [ -z "$EXTRACTED_DIR" ]; then
    echo "错误：未找到解压后的目录"
    ls -la
    exit 1
fi

echo "找到解压目录: ${EXTRACTED_DIR}"

# 复制 .so 文件到对应的架构目录
echo "复制 JNI 库文件..."
for arch in arm64-v8a armeabi-v7a x86 x86_64; do
    if [ -d "${EXTRACTED_DIR}/jni/${arch}" ]; then
        echo "  复制 ${arch}..."
        mkdir -p "${SCRIPT_DIR}/${TARGET_DIR}/${arch}"
        cp "${EXTRACTED_DIR}/jni/${arch}"/*.so "${SCRIPT_DIR}/${TARGET_DIR}/${arch}/" 2>/dev/null || true
    fi
done

# 清理临时目录
cd "$SCRIPT_DIR"
rm -rf "$TMP_DIR"

echo "完成！JNI 库已下载到 ${TARGET_DIR}"
echo ""
echo "已安装的库："
find "${TARGET_DIR}" -name "*.so" -type f 2>/dev/null || echo "未找到 .so 文件"
