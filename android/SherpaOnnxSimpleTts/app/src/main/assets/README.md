# Assets 目录

⚠️ **注意**: 本项目不再使用 assets 目录存放 TTS 模型！

## 新的模型存放位置

模型文件现在存放在应用的私有存储目录：

```
/data/data/com.k2fsa.sherpa.onnx.simpletts/files/models/tts/
```

## 为什么改变？

- ✅ **APK 体积小**: 模型文件不会打包到 APK 中（模型文件通常 100MB+）
- ✅ **灵活更新**: 可以单独下载或更新模型，无需重新安装应用
- ✅ **更好的实践**: 符合 Android 应用的最佳实践

## 如何配置模型？

请参考项目根目录的 **README.md** 文件，里面有详细的模型配置说明。

简要步骤：

1. 下载模型文件
2. 使用 adb 推送到应用私有目录：
   ```bash
   adb push vits-melo-tts-zh_en/* /data/data/com.k2fsa.sherpa.onnx.simpletts/files/models/tts/vits-melo-tts-zh_en/
   ```

详细说明请查看主 README.md
