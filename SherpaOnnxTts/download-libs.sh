#!/usr/bin/env bash

# 下载sherpa-onnx预编译库文件

set -e

SHERPA_ONNX_VERSION="1.12.18"
GITHUB_REPO="k2-fsa/sherpa-onnx"
BASE_URL="https://github.com/${GITHUB_REPO}/releases/download/v${SHERPA_ONNX_VERSION}"

echo "正在下载 sherpa-onnx v${SHERPA_ONNX_VERSION} 预编译库..."

# 创建临时目录
TEMP_DIR=$(mktemp -d)
cd "$TEMP_DIR"

# 下载Android AAR包
AAR_FILE="sherpa-onnx-${SHERPA_ONNX_VERSION}.aar"
AAR_URL="${BASE_URL}/${AAR_FILE}"

echo "下载 ${AAR_FILE}..."
curl -L -O "${AAR_URL}" || {
    echo "错误: 无法下载AAR文件"
    echo "请手动从 ${AAR_URL} 下载"
    echo "或者访问 https://github.com/${GITHUB_REPO}/releases 查看所有发布版本"
    exit 1
}

# 解压AAR文件 (AAR实际上是一个ZIP文件)
echo "解压 ${AAR_FILE}..."
unzip -q "${AAR_FILE}"

# 获取脚本所在目录的绝对路径
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JNI_LIBS_DIR="${SCRIPT_DIR}/app/src/main/jniLibs"

# 复制.so文件到项目
echo "复制库文件到项目..."
for arch in arm64-v8a armeabi-v7a; do
    if [ -d "jni/${arch}" ]; then
        echo "  复制 ${arch}..."
        mkdir -p "${JNI_LIBS_DIR}/${arch}"
        cp jni/${arch}/*.so "${JNI_LIBS_DIR}/${arch}/"
    fi
done

# 清理临时文件
cd -
rm -rf "$TEMP_DIR"

echo "✅ 库文件下载完成！"
echo ""
echo "已安装的库文件:"
find "${JNI_LIBS_DIR}" -name "*.so" -exec ls -lh {} \;
