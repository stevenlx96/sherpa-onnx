# Demo AAR 测试库文件夹

## 说明

此文件夹用于存放编译好的 AAR 文件，让 demo 项目可以独立测试 AAR。

⚠️ **重要**: 如果此文件夹中没有 `library-release.aar` 文件，编译 demo 会失败并报错：
```
Null extracted folder for artifact: ResolvedArtifact(...library-release.aar...)
```

## 快速开始（推荐）

**在父项目 StreamingASRApp 目录下运行完整测试流程：**

```bash
cd StreamingASRApp
./build-and-test-demo.sh
```

这个脚本会自动完成：
1. ✅ 编译 library 生成 AAR
2. ✅ 复制 AAR 到 demo/app/libs/
3. ✅ 编译 demo 独立项目

## 手动步骤

### 1. 编译 library 模块生成 AAR

```bash
cd StreamingASRApp

# 编译 AAR
./gradlew :library:assembleRelease
```

生成的 AAR 位于：`library/build/outputs/aar/library-release.aar`

### 2. 复制 AAR 到此目录

**方法 A：使用脚本**

```bash
# 从 StreamingASRApp 目录运行
./copy-aar-to-demo.sh
```

**方法 B：手动复制**

```bash
# 从 StreamingASRApp 目录运行
cp library/build/outputs/aar/library-release.aar demo/app/libs/
```

### 3. 编译 demo 独立项目

```bash
# demo 现在是完全独立的 Android 项目，有自己的 Gradle wrapper
cd demo
./gradlew assembleDebug

# 安装到设备
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 验证 AAR 文件

确保此目录包含 `library-release.aar` 文件：

```bash
ls -lh demo/app/libs/library-release.aar
```

应该看到 AAR 文件（大小约几 MB，取决于是否包含 .so 文件）。

## 故障排查

### 错误: "Null extracted folder for artifact"

**原因**: `demo/app/libs/library-release.aar` 文件不存在

**解决方案**:
```bash
# 返回父项目目录
cd StreamingASRApp

# 运行完整构建流程
./build-and-test-demo.sh
```

或手动执行：
```bash
# 1. 编译 AAR
./gradlew :library:assembleRelease

# 2. 复制到 demo
cp library/build/outputs/aar/library-release.aar demo/app/libs/

# 3. 编译 demo
cd demo && ./gradlew assembleDebug
```

## 注意事项

- ⚠️ AAR 文件**不会**提交到 git（已添加到 .gitignore）
- ⚠️ 每次修改 library 代码后，需要重新编译并复制 AAR
- ⚠️ demo 现在是**完全独立的 Android 项目**，有自己的 Gradle wrapper
- ⚠️ demo 通过 AAR 文件使用功能，而不是依赖源码

## 依赖配置

demo/app/build.gradle.kts 已配置为使用 AAR：

```kotlin
dependencies {
    // 使用 AAR 文件
    implementation(files("libs/library-release.aar"))

    // 必需的外部依赖
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

---

**这才是真正测试 AAR 的方式！** demo 作为独立项目通过 AAR 文件使用功能，模拟真实的 AAR 集成场景。
