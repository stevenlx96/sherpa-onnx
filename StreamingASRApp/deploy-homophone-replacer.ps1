# HomophoneReplacer 文件部署脚本 (PowerShell)
# 用法: .\deploy-homophone-replacer.ps1

Write-Host "================================================" -ForegroundColor Cyan
Write-Host "部署 HomophoneReplacer 文件到 Android 设备" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

# 创建临时目录
$TempDir = ".\temp_hr_files"
if (!(Test-Path $TempDir)) {
    New-Item -ItemType Directory -Path $TempDir | Out-Null
}

# 下载文件
Write-Host "步骤 1: 下载 lexicon.txt ..." -ForegroundColor Yellow
$LexiconUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/hr-files/lexicon.txt"
$LexiconPath = Join-Path $TempDir "lexicon.txt"

if (!(Test-Path $LexiconPath)) {
    try {
        Invoke-WebRequest -Uri $LexiconUrl -OutFile $LexiconPath -UseBasicParsing
        Write-Host "✓ lexicon.txt 下载完成 ($((Get-Item $LexiconPath).Length / 1MB) MB)" -ForegroundColor Green
    } catch {
        Write-Host "❌ 下载失败: $_" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "✓ lexicon.txt 已存在，跳过下载" -ForegroundColor Green
}

Write-Host ""
Write-Host "步骤 2: 下载 replace.fst ..." -ForegroundColor Yellow
$FstUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/hr-files/replace.fst"
$FstPath = Join-Path $TempDir "replace.fst"

if (!(Test-Path $FstPath)) {
    try {
        Invoke-WebRequest -Uri $FstUrl -OutFile $FstPath -UseBasicParsing
        Write-Host "✓ replace.fst 下载完成 ($((Get-Item $FstPath).Length / 1KB) KB)" -ForegroundColor Green
    } catch {
        Write-Host "❌ 下载失败: $_" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "✓ replace.fst 已存在，跳过下载" -ForegroundColor Green
}

Write-Host ""
Write-Host "================================================" -ForegroundColor Cyan
Write-Host "部署到 Android 设备" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan

# 检查 ADB
Write-Host ""
Write-Host "步骤 3: 检查 ADB 和设备连接 ..." -ForegroundColor Yellow
$AdbDevices = & adb devices 2>&1
if ($AdbDevices -match "device$") {
    Write-Host "✓ 设备连接正常" -ForegroundColor Green
} else {
    Write-Host "❌ 错误: 未检测到 Android 设备" -ForegroundColor Red
    Write-Host "   请确保:" -ForegroundColor Yellow
    Write-Host "   1. 设备已通过 USB 连接" -ForegroundColor Yellow
    Write-Host "   2. 设备已开启 USB 调试" -ForegroundColor Yellow
    Write-Host "   3. 已授权此电脑进行调试" -ForegroundColor Yellow
    exit 1
}

# 推送文件到临时目录
Write-Host ""
Write-Host "步骤 4: 推送文件到设备临时目录 ..." -ForegroundColor Yellow
& adb push $LexiconPath /data/local/tmp/ 2>&1 | Out-Null
& adb push $FstPath /data/local/tmp/ 2>&1 | Out-Null
Write-Host "✓ 文件推送完成" -ForegroundColor Green

# 创建目标目录
Write-Host ""
Write-Host "步骤 5: 创建应用目录 ..." -ForegroundColor Yellow
& adb shell "run-as com.example.streamingasr mkdir -p /data/data/com.example.streamingasr/files/models/asr" 2>&1 | Out-Null
Write-Host "✓ 目录创建完成" -ForegroundColor Green

# 复制到应用私有目录
Write-Host ""
Write-Host "步骤 6: 复制到应用目录 ..." -ForegroundColor Yellow
Write-Host "   目标路径: /data/data/com.example.streamingasr/files/models/asr/" -ForegroundColor Gray
& adb shell "run-as com.example.streamingasr cp /data/local/tmp/lexicon.txt /data/data/com.example.streamingasr/files/models/asr/"
& adb shell "run-as com.example.streamingasr cp /data/local/tmp/replace.fst /data/data/com.example.streamingasr/files/models/asr/"
Write-Host "✓ 文件复制完成" -ForegroundColor Green

# 清理临时文件
Write-Host ""
Write-Host "步骤 7: 清理设备临时文件 ..." -ForegroundColor Yellow
& adb shell "rm /data/local/tmp/lexicon.txt"
& adb shell "rm /data/local/tmp/replace.fst"
Write-Host "✓ 临时文件已清理" -ForegroundColor Green

# 验证文件
Write-Host ""
Write-Host "步骤 8: 验证部署 ..." -ForegroundColor Yellow
Write-Host "应用目录中的文件:" -ForegroundColor Gray
$FileList = & adb shell "run-as com.example.streamingasr ls -lh /data/data/com.example.streamingasr/files/models/asr/" 2>&1
$FileList | Select-String -Pattern "lexicon.txt|replace.fst" | ForEach-Object {
    Write-Host "   $_" -ForegroundColor Gray
}

Write-Host ""
Write-Host "================================================" -ForegroundColor Cyan
Write-Host "✓ 部署完成！" -ForegroundColor Green
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "HomophoneReplacer 功能说明:" -ForegroundColor White
Write-Host "  - lexicon.txt: 词典文件 (汉字到拼音的映射)" -ForegroundColor Gray
Write-Host "  - replace.fst: 替换规则 (同音字替换规则)" -ForegroundColor Gray
Write-Host ""
Write-Host "功能示例:" -ForegroundColor White
Write-Host "  ❌ '金安达' → ✅ '津安达'" -ForegroundColor Yellow
Write-Host "  ❌ '在坐的各位' → ✅ '在座的各位'" -ForegroundColor Yellow
Write-Host "  ❌ '因该这样做' → ✅ '应该这样做'" -ForegroundColor Yellow
Write-Host ""
Write-Host "现在重新编译安装应用，HomophoneReplacer 将自动启用！" -ForegroundColor Cyan
Write-Host "日志中会显示: 'HomophoneReplacer enabled'" -ForegroundColor Gray
Write-Host ""

# 提示下一步
Write-Host "下一步操作:" -ForegroundColor White
Write-Host "  1. 重新编译应用" -ForegroundColor Gray
Write-Host "  2. 安装到设备" -ForegroundColor Gray
Write-Host "  3. 启动应用测试" -ForegroundColor Gray
Write-Host ""
Write-Host "如需卸载这些文件，运行:" -ForegroundColor White
Write-Host '  adb shell "run-as com.example.streamingasr rm /data/data/com.example.streamingasr/files/models/asr/lexicon.txt"' -ForegroundColor Gray
Write-Host '  adb shell "run-as com.example.streamingasr rm /data/data/com.example.streamingasr/files/models/asr/replace.fst"' -ForegroundColor Gray
Write-Host ""
