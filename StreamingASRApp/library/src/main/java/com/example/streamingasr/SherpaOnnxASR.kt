package com.example.streamingasr

import android.content.Context
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.KeywordSpotter
import java.io.File

/**
 * Sherpa-ONNX ASR Library - 公共 API
 *
 * 这是 StreamingASR 库的主要入口点，提供语音识别、唤醒词检测和音频录制功能。
 *
 * 特性：
 * - 实时流式语音识别 (ASR)
 * - 唤醒词检测 (KWS)
 * - 语音活动检测 (VAD)
 * - 自动缓存管理
 * - 音频录制和导出
 *
 * 使用示例：
 * ```kotlin
 * val asr = SherpaOnnxASR(context)
 *
 * // 检查并加载模型
 * if (!asr.hasModels()) {
 *     Log.e(TAG, asr.getModelDownloadInstructions())
 *     return
 * }
 *
 * // 创建识别器
 * val recognizer = asr.createRecognizer()
 *
 * // 创建音频录制器
 * val recorder = asr.createAudioRecorder()
 * recorder.startRecording(savePcm = true, cacheInMemory = true)
 *
 * // 清理缓存
 * asr.clearAudioCache()
 * ```
 */
class SherpaOnnxASR(private val context: Context) {

    private val modelManager: ModelManager = ModelManager(context)
    private var audioRecorder: AudioRecorder? = null

    companion object {
        const val TAG = "SherpaOnnxASR"
        const val SAMPLE_RATE = 16000

        /**
         * 库版本号
         */
        const val VERSION = "1.0.0"
    }

    // ========================================
    // 模型管理
    // ========================================

    /**
     * 检查 ASR 模型是否存在
     * @param modelType 模型类型（默认 ZIPFORMER_TRANSDUCER）
     */
    fun hasAsrModel(modelType: ModelManager.ModelType = ModelManager.ModelType.ZIPFORMER_TRANSDUCER): Boolean {
        return modelManager.checkModelExists(modelType)
    }

    /**
     * 检查 KWS 唤醒词模型是否存在
     */
    fun hasKwsModel(): Boolean {
        return modelManager.checkKwsExists()
    }

    /**
     * 检查 VAD 模型是否存在
     */
    fun hasVadModel(): Boolean {
        return modelManager.checkVadExists()
    }

    /**
     * 检查是否有任何模型（至少 ASR 或 KWS 之一）
     */
    fun hasModels(): Boolean {
        return hasAsrModel() || hasKwsModel()
    }

    /**
     * 获取模型目录路径
     */
    fun getModelDir(): File {
        return modelManager.getModelDir()
    }

    /**
     * 获取模型下载和部署说明
     */
    fun getModelDownloadInstructions(): String {
        return modelManager.getModelDownloadInstructions()
    }

    /**
     * 列出模型目录中的所有文件
     */
    fun listModelFiles(): List<String> {
        return modelManager.listModelFiles()
    }

    // ========================================
    // 识别器创建
    // ========================================

    /**
     * 创建在线语音识别器 (ASR)
     * @param modelType 模型类型
     * @param numThreads 线程数（默认为 CPU 核心数）
     * @return OnlineRecognizer 或 null
     */
    fun createRecognizer(
        modelType: ModelManager.ModelType = ModelManager.ModelType.ZIPFORMER_TRANSDUCER,
        numThreads: Int = Runtime.getRuntime().availableProcessors()
    ): OnlineRecognizer? {
        return modelManager.createOnlineRecognizer(modelType, numThreads)
    }

    /**
     * 自动检测并创建识别器
     * @param numThreads 线程数
     * @return OnlineRecognizer 或 null
     */
    fun createRecognizerAuto(
        numThreads: Int = Runtime.getRuntime().availableProcessors()
    ): OnlineRecognizer? {
        return modelManager.createOnlineRecognizerAuto(numThreads)
    }

    /**
     * 创建自定义模型识别器
     * @param modelFiles 自定义模型文件配置
     * @param numThreads 线程数
     * @return OnlineRecognizer 或 null
     */
    fun createRecognizerCustom(
        modelFiles: ModelFiles,
        numThreads: Int = Runtime.getRuntime().availableProcessors()
    ): OnlineRecognizer? {
        return modelManager.createOnlineRecognizer(modelFiles, numThreads)
    }

    /**
     * 创建 VAD (Voice Activity Detection)
     * @param threshold 语音检测阈值 (0-1, 默认 0.5)
     * @param minSilenceDuration 最短静音时长，用于断句 (秒, 默认 0.3)
     * @param minSpeechDuration 最短语音时长，过滤杂音 (秒, 默认 0.25)
     * @param maxSpeechDuration 最大语音时长，强制断句 (秒, 默认 10.0)
     * @return Vad 或 null
     */
    fun createVad(
        threshold: Float = 0.5F,
        minSilenceDuration: Float = 0.3F,
        minSpeechDuration: Float = 0.25F,
        maxSpeechDuration: Float = 10.0F
    ): Vad? {
        return modelManager.createVad(threshold, minSilenceDuration, minSpeechDuration, maxSpeechDuration)
    }

    /**
     * 创建唤醒词识别器 (KWS)
     * @param keywordsFile 关键词文件名 (默认 keywords.txt)
     * @param threshold 唤醒阈值 (默认 0.5)
     * @param score 关键词分数 (默认 1.0)
     * @param maxActivePaths 最大激活路径数 (默认 4)
     * @param numThreads 线程数 (默认 1)
     * @return KeywordSpotter 或 null
     */
    fun createKeywordSpotter(
        keywordsFile: String = "keywords.txt",
        threshold: Float = 0.5F,
        score: Float = 1.0F,
        maxActivePaths: Int = 4,
        numThreads: Int = 1
    ): KeywordSpotter? {
        return modelManager.createKeywordSpotter(keywordsFile, threshold, score, maxActivePaths, numThreads)
    }

    // ========================================
    // 音频录制
    // ========================================

    /**
     * 创建音频录制器
     * @param sampleRate 采样率（默认 16000）
     * @param cacheDir 缓存目录（默认使用 context.filesDir/audio_cache）
     * @return AudioRecorder
     */
    fun createAudioRecorder(
        sampleRate: Int = SAMPLE_RATE,
        cacheDir: File = File(context.filesDir, "audio_cache")
    ): AudioRecorder {
        // 确保缓存目录存在
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        audioRecorder = AudioRecorder(sampleRate, cacheDir)
        return audioRecorder!!
    }

    /**
     * 获取当前音频录制器（如果已创建）
     */
    fun getAudioRecorder(): AudioRecorder? {
        return audioRecorder
    }

    // ========================================
    // 缓存管理
    // ========================================

    /**
     * 获取音频缓存总大小（字节）
     */
    fun getAudioCacheSize(): Long {
        return audioRecorder?.getCacheSize() ?: 0L
    }

    /**
     * 获取音频缓存大小（MB）
     */
    fun getAudioCacheSizeMB(): Long {
        return getAudioCacheSize() / 1024 / 1024
    }

    /**
     * 清理旧的音频缓存文件
     * @param maxCacheSizeBytes 最大缓存大小（字节），默认 500MB
     * @param keepRecentCount 保留最近的文件数量，默认 1 个
     */
    fun cleanAudioCache(
        maxCacheSizeBytes: Long = 500 * 1024 * 1024,
        keepRecentCount: Int = 1
    ) {
        audioRecorder?.cleanOldCacheFiles(maxCacheSizeBytes, keepRecentCount)
    }

    /**
     * 清除所有音频缓存文件
     */
    fun clearAudioCache() {
        audioRecorder?.deleteAllCacheFiles()
    }

    /**
     * 清除内存中的音频缓存
     */
    fun clearMemoryCache() {
        audioRecorder?.clearPcmCache()
    }

    // ========================================
    // 工具方法
    // ========================================

    /**
     * 获取库版本信息
     */
    fun getVersionInfo(): String {
        return """
            Sherpa-ONNX ASR Library
            Version: $VERSION

            Features:
            - Real-time Streaming ASR
            - Keyword Spotting (KWS)
            - Voice Activity Detection (VAD)
            - Automatic Cache Management
            - Audio Recording & Export

            Model Directory: ${getModelDir().absolutePath}
            ASR Model: ${if (hasAsrModel()) "✓" else "✗"}
            KWS Model: ${if (hasKwsModel()) "✓" else "✗"}
            VAD Model: ${if (hasVadModel()) "✓" else "✗"}
            Audio Cache: ${getAudioCacheSizeMB()} MB
        """.trimIndent()
    }
}
