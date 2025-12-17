# Demo AAR 测试库文件夹

## 说明

此文件夹用于存放编译好的 AAR 文件，让 demo 模块可以独立测试 AAR。

## 使用步骤

### 1. 编译 library 模块生成 AAR

```bash
cd StreamingASRApp

# 编译 AAR
./gradlew :library:assembleRelease
```

生成的 AAR 位于：`library/build/outputs/aar/library-release.aar`

### 2. 复制 AAR 到此目录

**方法 A：使用脚本（推荐）**

```bash
# 自动复制 AAR
./copy-aar-to-demo.sh
```

**方法 B：手动复制**

```bash
cp library/build/outputs/aar/library-release.aar demo/libs/
```

### 3. 编译 demo 测试 AAR

```bash
# 编译 demo（使用 AAR）
./gradlew :demo:assembleDebug

# 安装到设备
adb install -r demo/build/outputs/apk/debug/demo-debug.apk
```

## 验证

确保此目录包含 `library-release.aar` 文件：

```bash
ls -lh demo/libs/library-release.aar
```

应该看到 AAR 文件（大小约几 MB，取决于是否包含 .so 文件）。

## 注意事项

- ⚠️ AAR 文件**不会**提交到 git（已添加到 .gitignore）
- ⚠️ 每次修改 library 代码后，需要重新编译并复制 AAR
- ⚠️ demo 模块现在**独立于** library 模块，完全通过 AAR 文件使用功能

## 依赖配置

demo/build.gradle.kts 已配置为使用 AAR：

```kotlin
dependencies {
    // 使用 AAR 文件
    implementation(files("libs/library-release.aar"))

    // 必需的外部依赖
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

---

**这才是真正测试 AAR 的方式！** demo 通过 AAR 文件使用功能，而不是依赖源码。
