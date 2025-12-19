@echo off
REM 下载预编译的 JNI 库文件 - Windows 版本
REM 方法：从官方发布的 TTS APK 中提取 SO 文件

setlocal enabledelayedexpansion

echo ================================================
echo   下载 sherpa-onnx JNI 库文件 (Windows)
echo ================================================
echo.

REM 创建 jniLibs 目录
if not exist "app\src\main\jniLibs\arm64-v8a" mkdir app\src\main\jniLibs\arm64-v8a
if not exist "app\src\main\jniLibs\armeabi-v7a" mkdir app\src\main\jniLibs\armeabi-v7a
if not exist "app\src\main\jniLibs\x86_64" mkdir app\src\main\jniLibs\x86_64
if not exist "app\src\main\jniLibs\x86" mkdir app\src\main\jniLibs\x86

REM APK 下载地址
set APK_URL=https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/sherpa-onnx-v1.10.30-arm64-v8a-tts-en-kokoro.apk

echo 📥 下载官方 TTS APK (约 30MB)...
echo 使用 PowerShell 下载...
powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%APK_URL%' -OutFile 'sherpa-onnx-tts.apk' -UseBasicParsing}"

if not exist "sherpa-onnx-tts.apk" (
    echo ❌ 下载失败！
    echo 请检查网络连接或手动下载：
    echo %APK_URL%
    pause
    exit /b 1
)

echo.
echo 📦 解压 APK...
REM 使用 PowerShell 解压（Windows 内置）
powershell -Command "& {Expand-Archive -Path 'sherpa-onnx-tts.apk' -DestinationPath 'sherpa-onnx-extracted' -Force}"

echo 📤 复制 JNI 库文件...

REM 复制各个架构的 SO 文件
for %%a in (arm64-v8a armeabi-v7a x86_64 x86) do (
    if exist "sherpa-onnx-extracted\lib\%%a" (
        echo   → %%a
        copy /Y "sherpa-onnx-extracted\lib\%%a\*.so" "app\src\main\jniLibs\%%a\" >nul 2>&1
        if !errorlevel! equ 0 (
            echo     ✓ 已复制
        ) else (
            echo     ⚠️  该架构没有 SO 文件
        )
    )
)

echo.
echo 🧹 清理临时文件...
del /F /Q sherpa-onnx-tts.apk >nul 2>&1
rd /S /Q sherpa-onnx-extracted >nul 2>&1

echo.
echo ================================================
echo   ✅ JNI 库文件下载完成！
echo ================================================
echo.
echo 已安装的库文件：
for %%a in (arm64-v8a armeabi-v7a x86_64 x86) do (
    if exist "app\src\main\jniLibs\%%a\*.so" (
        echo   📱 %%a:
        dir /B "app\src\main\jniLibs\%%a\*.so" 2>nul | findstr /R ".*" >nul && (
            for %%f in (app\src\main\jniLibs\%%a\*.so) do (
                echo      - %%~nxf
            )
        )
    )
)
echo.
echo 🎉 现在可以构建项目了: gradlew assembleDebug
echo.
pause
