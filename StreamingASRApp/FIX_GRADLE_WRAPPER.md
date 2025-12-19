# 修复 Gradle Wrapper

## 问题

缺少 `gradle/wrapper/gradle-wrapper.jar` 文件，导致无法运行 `./gradlew` 命令。

错误信息：
```
Error: Could not find or load main class "-Xmx64m"
Caused by: java.lang.ClassNotFoundException: "-Xmx64m"
```

## 解决方法

### 方法 1：使用本地 Gradle 生成 Wrapper（推荐）

如果你本地安装了 Gradle：

```bash
cd StreamingASRApp

# 生成 wrapper（Gradle 8.13）
gradle wrapper --gradle-version 8.13

# 验证
ls -lh gradle/wrapper/gradle-wrapper.jar
```

### 方法 2：从其他 Android 项目复制

从任何正常的 Android 项目复制 `gradle-wrapper.jar`：

```bash
# 从其他项目复制
cp /path/to/other-project/gradle/wrapper/gradle-wrapper.jar StreamingASRApp/gradle/wrapper/

# 验证
ls -lh StreamingASRApp/gradle/wrapper/gradle-wrapper.jar
```

### 方法 3：直接下载（需要网络）

下载官方的 gradle-wrapper.jar：

```bash
cd StreamingASRApp/gradle/wrapper

# 下载 Gradle 8.13 的 wrapper jar
curl -L -o gradle-wrapper.jar https://raw.githubusercontent.com/gradle/gradle/v8.13.0/gradle/wrapper/gradle-wrapper.jar

# 或使用 wget
wget -O gradle-wrapper.jar https://raw.githubusercontent.com/gradle/gradle/v8.13.0/gradle/wrapper/gradle-wrapper.jar

# 验证
ls -lh gradle-wrapper.jar
```

### 方法 4：在 Android Studio 中重新生成

1. 打开项目：`File -> Open -> StreamingASRApp`
2. Android Studio 会自动检测并提示修复 Gradle wrapper
3. 点击 "Download Gradle" 或 "Use embedded JDK"
4. 等待同步完成

## 验证修复

修复后，检查文件是否存在：

```bash
ls -lh StreamingASRApp/gradle/wrapper/

# 应该看到：
# gradle-wrapper.jar
# gradle-wrapper.properties
```

然后尝试运行：

```bash
cd StreamingASRApp
./gradlew --version

# 应该显示 Gradle 8.13 版本信息
```

## 成功后继续

Gradle wrapper 修复后，就可以继续打包 AAR 了：

```bash
# 编译 library AAR
./gradlew :library:assembleRelease

# 复制到 demo
./copy-aar-to-demo.sh

# 编译 demo
./gradlew :demo:assembleDebug
```

---

**注意：** `gradle-wrapper.jar` 是二进制文件，通常不应该手动修改，应该通过官方方式生成或下载。
