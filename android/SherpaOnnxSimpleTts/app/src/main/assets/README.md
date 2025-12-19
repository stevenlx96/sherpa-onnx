# 模型文件目录

请将下载的 TTS 模型文件放置在此目录下。

## 推荐模型：vits-melo-tts-zh_en

### 下载地址
https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-melo-tts-zh_en.tar.bz2

### 目录结构

解压后，将文件放置如下：

```
assets/
└── vits-melo-tts-zh_en/
    ├── model.onnx          (必需)
    ├── lexicon.txt         (必需)
    ├── tokens.txt          (必需)
    ├── date.fst            (可选)
    ├── number.fst          (可选)
    └── phone.fst           (可选)
```

### 快速下载脚本

```bash
# 在项目根目录执行
cd app/src/main/assets

# 下载并解压
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-melo-tts-zh_en.tar.bz2
tar -xjf vits-melo-tts-zh_en.tar.bz2

# 清理压缩包
rm vits-melo-tts-zh_en.tar.bz2
```

## 注意事项

⚠️ 不要将模型文件提交到 Git 仓库（文件较大）
⚠️ 确保三个核心文件（model.onnx, lexicon.txt, tokens.txt）都存在
