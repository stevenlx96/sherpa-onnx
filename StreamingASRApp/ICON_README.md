# 应用图标说明

## 如何添加应用图标

应用图标需要放在 `app/src/main/res/mipmap-*/` 目录中。

### 方法1：使用Android Studio（推荐）
1. 在Android Studio中右键点击 `res` 目录
2. 选择 `New` -> `Image Asset`
3. 配置图标并生成所有尺寸

### 方法2：使用在线工具
访问 https://icon.kitchen/ 或 https://romannurik.github.io/AndroidAssetStudio/
上传你的图标，自动生成所有尺寸，然后下载并解压到 `res/` 目录

### 方法3：手动创建
需要创建以下目录和文件：
```
res/
├── mipmap-mdpi/
│   ├── ic_launcher.png (48x48)
│   └── ic_launcher_round.png (48x48)
├── mipmap-hdpi/
│   ├── ic_launcher.png (72x72)
│   └── ic_launcher_round.png (72x72)
├── mipmap-xhdpi/
│   ├── ic_launcher.png (96x96)
│   └── ic_launcher_round.png (96x96)
├── mipmap-xxhdpi/
│   ├── ic_launcher.png (144x144)
│   └── ic_launcher_round.png (144x144)
└── mipmap-xxxhdpi/
    ├── ic_launcher.png (192x192)
    └── ic_launcher_round.png (192x192)
```

## 临时方案

在图标就位之前，应用会使用系统默认图标。应用仍然可以正常运行。

## 快速获取默认图标

如果你只想快速测试，可以从Android SDK复制默认图标：

```bash
# 从现有的Android项目复制，或者使用简单的占位符图标
```

或者暂时注释掉AndroidManifest.xml中的图标引用：
```xml
<!-- android:icon="@mipmap/ic_launcher" -->
<!-- android:roundIcon="@mipmap/ic_launcher_round" -->
```
