# Streaming ASR Library ProGuard Rules
# 这些规则会自动应用到使用此库的应用中

# 保留 sherpa-onnx JNI 接口
-keep class com.k2fsa.sherpa.onnx.** { *; }

# 保留 StreamingASR 公共 API
-keep public class com.example.streamingasr.ModelManager { *; }
-keep public class com.example.streamingasr.AudioRecorder { *; }
-keep public class com.example.streamingasr.ModelManager$ModelType { *; }

# 保留所有 native 方法
-keepclasseswithmembernames class * {
    native <methods>;
}

# 保留数据类
-keep class com.k2fsa.sherpa.onnx.*Config { *; }
-keep class com.k2fsa.sherpa.onnx.*Result { *; }
