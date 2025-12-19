package com.example.streamingasr

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.KeywordSpotter
import kotlinx.coroutines.*
import java.io.File

/**
 * Sherpa ASR Manager - 高级封装 API
 *
 * 功能特性：
 * - 🎙️ KWS 唤醒检测（低功耗待机）
 * - 🔊 实时流式识别（边说边识别）
 * - ✂️ VAD 智能断句（基于语音段检测）
 * - 📝 同音字纠正（HomophoneReplacer）
 * - 🔄 自动休眠机制（节省资源）
 *
 * 使用示例：
 * ```kotlin
 * val asr = SherpaAsrManager(context)
 *
 * // 设置回调
 * asr.onWakeWordDetected = { keyword ->
 *     Log.i(TAG, "唤醒: $keyword")
 * }
 *
 * asr.onSentenceComplete = { text ->
 *     // 发送给 LLM
 *     sendToLLM(text)
 * }
 *
 * asr.onPartialResult = { text ->
 *     // 实时显示部分结果
 *     updateUI(text)
 * }
 *
 * asr.onStateChanged = { state ->
 *     when (state) {
 *         State.STANDBY -> Log.i(TAG, "待机中，等待唤醒")
 *         State.ACTIVE -> Log.i(TAG, "激活中，正在识别")
 *     }
 * }
 *
 * // 开始监听
 * asr.startListening()
 *
 * // 停止监听
 * asr.stopListening()
 *
 * // 清理资源
 * asr.release()
 * ```
 *
 * 模型路径：
 * - 所有模型从 `/data/data/包名/files/models/` 加载
 * - KWS 模型：`kws/sherpa-onnx-kws-zipformer-*`
 * - ASR 模型：`asr/sherpa-onnx-streaming-zipformer-*`
 * - VAD 模型：`vad/silero_vad.onnx`
 */
class SherpaAsrManager(private val context: Context) {

    companion object {
        private const val TAG = "SherpaAsrManager"
        const val SAMPLE_RATE = 16000
        private const val IDLE_TIMEOUT_MS = 5000L  // 5秒无语音自动休眠
    }

    /**
     * 状态枚举
     */
    enum class State {
        STANDBY,   // 待机：仅监听唤醒词
        ACTIVE     // 激活：进行语音识别
    }

    // ========================================
    // 回调接口
    // ========================================

    /**
     * 唤醒词检测回调
     * @param keyword 检测到的唤醒词（如 "你好小智"）
     */
    var onWakeWordDetected: ((keyword: String) -> Unit)? = null

    /**
     * 句子完成回调（VAD 断句触发）
     * @param text 完整的句子文本
     */
    var onSentenceComplete: ((text: String) -> Unit)? = null

    /**
     * 部分结果回调（实时识别）
     * @param text 当前识别的部分文本
     */
    var onPartialResult: ((text: String) -> Unit)? = null

    /**
     * 状态变化回调
     * @param state 新的状态（STANDBY 或 ACTIVE）
     */
    var onStateChanged: ((state: State) -> Unit)? = null

    /**
     * 错误回调
     * @param error 错误信息
     */
    var onError: ((error: String) -> Unit)? = null

    // ========================================
    // 核心组件
    // ========================================

    private val modelManager: ModelManager = ModelManager(context)
    private var audioRecorder: AudioRecorder? = null
    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    private var vad: Vad? = null
    private var keywordSpotter: KeywordSpotter? = null
    private var kwsStream: OnlineStream? = null

    // ========================================
    // 状态管理
    // ========================================

    private var currentState: State = State.STANDBY
        set(value) {
            if (field != value) {
                field = value
                onStateChanged?.invoke(value)
            }
        }

    private var isListening = false
    private var recognitionJob: Job? = null
    private var kwsJob: Job? = null
    private var lastSpeechTime: Long = 0L

    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // ========================================
    // 配置参数
    // ========================================

    /**
     * VAD 配置
     */
    data class VadConfig(
        val threshold: Float = 0.5F,
        val minSilenceDuration: Float = 1.0F,  // 静音多久算句子结束
        val minSpeechDuration: Float = 0.25F,  // 最短有效语音长度
        val maxSpeechDuration: Float = 10.0F   // 最长语音段
    )

    /**
     * KWS 配置
     */
    data class KwsConfig(
        val keywordsFile: String = "keywords.txt",
        val threshold: Float = 0.5F,
        val score: Float = 1.0F
    )

    var vadConfig = VadConfig()
    var kwsConfig = KwsConfig()

    // ========================================
    // 公共方法
    // ========================================

    /**
     * 检查模型是否就绪
     * @return true 如果至少有 ASR 或 KWS 模型
     */
    fun isModelReady(): Boolean {
        return modelManager.checkModelExists(ModelManager.ModelType.ZIPFORMER_TRANSDUCER)
            || modelManager.checkKwsExists()
    }

    /**
     * 获取模型目录路径
     */
    fun getModelDir(): File {
        return modelManager.getModelDir()
    }

    /**
     * 获取当前状态
     */
    fun getState(): State {
        return currentState
    }

    /**
     * 开始监听
     * - 如果有 KWS 模型：进入 STANDBY 模式，等待唤醒
     * - 如果没有 KWS 模型：直接进入 ACTIVE 模式，开始识别
     */
    fun startListening() {
        if (isListening) {
            Log.w(TAG, "Already listening")
            return
        }

        if (!isModelReady()) {
            onError?.invoke("模型未加载，请先部署模型到 ${getModelDir().absolutePath}")
            return
        }

        try {
            // 创建音频缓存目录
            val cacheDir = File(context.filesDir, "audio_cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            // 初始化音频录制器
            audioRecorder = AudioRecorder(SAMPLE_RATE, cacheDir)

            // 检查是否有 KWS 模型
            if (modelManager.checkKwsExists()) {
                startKwsMode()
            } else {
                startAsrMode()
            }

            isListening = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            onError?.invoke("启动失败: ${e.message}")
        }
    }

    /**
     * 停止监听
     */
    fun stopListening() {
        if (!isListening) {
            return
        }

        isListening = false
        currentState = State.STANDBY

        // 取消任务
        recognitionJob?.cancel()
        kwsJob?.cancel()

        // 停止录音
        audioRecorder?.stopRecording()

        // 释放流
        stream?.release()
        stream = null
        kwsStream?.release()
        kwsStream = null

        Log.i(TAG, "Stopped listening")
    }

    /**
     * 释放所有资源
     */
    fun release() {
        stopListening()

        // 释放模型
        recognizer?.release()
        recognizer = null
        vad?.release()
        vad = null
        keywordSpotter?.release()
        keywordSpotter = null

        // 取消协程
        scope.cancel()

        audioRecorder = null

        Log.i(TAG, "Released all resources")
    }

    // ========================================
    // KWS 模式（待机 + 唤醒）
    // ========================================

    private fun startKwsMode() {
        scope.launch(Dispatchers.IO) {
            try {
                // 创建 KWS
                if (keywordSpotter == null) {
                    keywordSpotter = modelManager.createKeywordSpotter(
                        keywordsFile = kwsConfig.keywordsFile,
                        threshold = kwsConfig.threshold,
                        score = kwsConfig.score
                    )
                }

                if (keywordSpotter == null) {
                    onError?.invoke("KWS 模型加载失败")
                    return@launch
                }

                kwsStream = keywordSpotter?.createStream()

                // 开始录音（不保存，不缓存）
                if (audioRecorder?.startRecording(savePcm = false, cacheInMemory = false) == true) {
                    currentState = State.STANDBY
                    startKwsTask()
                } else {
                    onError?.invoke("启动录音失败")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting KWS mode", e)
                onError?.invoke("KWS 启动失败: ${e.message}")
            }
        }
    }

    private fun startKwsTask() {
        kwsJob = scope.launch(Dispatchers.IO) {
            try {
                while (isActive && isListening && currentState == State.STANDBY) {
                    val samples = audioRecorder?.readAudioData()

                    if (samples != null && samples.isNotEmpty()) {
                        kwsStream?.acceptWaveform(samples, SAMPLE_RATE)

                        while (keywordSpotter?.isReady(kwsStream!!) == true) {
                            keywordSpotter?.decode(kwsStream!!)
                        }

                        val result = keywordSpotter?.getResult(kwsStream!!)
                        if (result != null && result.keyword.isNotEmpty()) {
                            Log.i(TAG, "🎤 检测到唤醒词: ${result.keyword}")

                            // 触发唤醒回调
                            onWakeWordDetected?.invoke(result.keyword)

                            // 切换到 ACTIVE 模式
                            handleWakeWordDetected(result.keyword)

                            // 重置 KWS 流
                            keywordSpotter?.reset(kwsStream!!)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "KWS task error", e)
                onError?.invoke("KWS 监听出错: ${e.message}")
            }
        }
    }

    private fun handleWakeWordDetected(keyword: String) {
        // 停止 KWS 任务
        kwsJob?.cancel()
        kwsStream?.release()
        kwsStream = null

        // 切换到 ACTIVE 模式
        currentState = State.ACTIVE

        // 启动 ASR 识别
        startAsrRecognition()
    }

    // ========================================
    // ASR 模式（直接识别）
    // ========================================

    private fun startAsrMode() {
        scope.launch(Dispatchers.IO) {
            try {
                // 创建 ASR
                if (recognizer == null) {
                    recognizer = modelManager.createOnlineRecognizer(
                        modelType = ModelManager.ModelType.ZIPFORMER_TRANSDUCER
                    )
                }

                // 创建 VAD
                if (vad == null) {
                    vad = modelManager.createVad(
                        threshold = vadConfig.threshold,
                        minSilenceDuration = vadConfig.minSilenceDuration,
                        minSpeechDuration = vadConfig.minSpeechDuration,
                        maxSpeechDuration = vadConfig.maxSpeechDuration
                    )
                }

                if (recognizer == null) {
                    onError?.invoke("ASR 模型加载失败")
                    return@launch
                }

                stream = recognizer?.createStream()

                // 开始录音（保存 PCM）
                if (audioRecorder?.startRecording(savePcm = true) == true) {
                    currentState = State.ACTIVE
                    startRecognitionTask()
                } else {
                    onError?.invoke("启动录音失败")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting ASR mode", e)
                onError?.invoke("ASR 启动失败: ${e.message}")
            }
        }
    }

    private fun startAsrRecognition() {
        scope.launch(Dispatchers.IO) {
            try {
                // 动态创建 ASR（避免与 KWS 资源冲突）
                if (recognizer == null) {
                    Log.i(TAG, "Creating OnlineRecognizer on wake...")
                    recognizer = modelManager.createOnlineRecognizer(
                        modelType = ModelManager.ModelType.ZIPFORMER_TRANSDUCER
                    )
                }

                if (recognizer == null) {
                    onError?.invoke("ASR 模型加载失败")
                    returnToStandby()
                    return@launch
                }

                // 动态创建 VAD
                if (vad == null) {
                    Log.i(TAG, "Creating VAD on wake...")
                    vad = modelManager.createVad(
                        threshold = vadConfig.threshold,
                        minSilenceDuration = vadConfig.minSilenceDuration,
                        minSpeechDuration = vadConfig.minSpeechDuration,
                        maxSpeechDuration = vadConfig.maxSpeechDuration
                    )
                    if (vad != null) {
                        Log.i(TAG, "VAD created successfully")
                    }
                }

                stream = recognizer?.createStream()
                lastSpeechTime = System.currentTimeMillis()

                startRecognitionTask()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting ASR recognition", e)
                onError?.invoke("启动识别失败: ${e.message}")
                returnToStandby()
            }
        }
    }

    // ========================================
    // 识别任务（核心逻辑）
    // ========================================

    private fun startRecognitionTask() {
        recognitionJob = scope.launch(Dispatchers.IO) {
            var lastText = ""
            val completedText = StringBuilder()

            try {
                while (isActive && isListening && currentState == State.ACTIVE) {
                    val samples = audioRecorder?.readAudioData()

                    if (samples != null && samples.isNotEmpty()) {
                        // 送入 VAD 检测
                        vad?.acceptWaveform(samples)

                        // 送入识别流
                        stream?.acceptWaveform(samples, SAMPLE_RATE)

                        // 解码
                        while (recognizer?.isReady(stream!!) == true) {
                            recognizer?.decode(stream!!)
                        }

                        // 获取识别结果
                        val result = recognizer?.getResult(stream!!)
                        val currentText = result?.text ?: ""

                        // 更新最后语音时间
                        if (currentText.isNotEmpty()) {
                            lastSpeechTime = System.currentTimeMillis()
                        }

                        // 🎯 VAD 真正的断句逻辑
                        var vadSentenceComplete = false
                        if (vad != null && vad?.empty() == false) {
                            // VAD 检测到了一个完整的语音段
                            val segment = vad?.front()
                            vad?.pop()

                            Log.d(TAG, "断句触发: VAD 检测到完整语音段 (start=${segment?.start}, samples=${segment?.samples?.size})")
                            vadSentenceComplete = true
                        }

                        // 备用断句：ASR 内置 endpoint
                        val isEndpoint = recognizer?.isEndpoint(stream!!) == true

                        // 组合断句策略
                        val shouldBreak = when {
                            vadSentenceComplete && currentText.isNotEmpty() -> {
                                Log.d(TAG, "断句触发: VAD 语音段完成")
                                true
                            }
                            isEndpoint && currentText.isNotEmpty() -> {
                                Log.d(TAG, "断句触发: ASR 内置 endpoint")
                                true
                            }
                            else -> false
                        }

                        // 如果检测到句子结束
                        if (shouldBreak && currentText.isNotEmpty()) {
                            // 句子结束，累积文本
                            if (completedText.isNotEmpty()) {
                                completedText.append("\n")
                            }
                            completedText.append(currentText)

                            Log.i(TAG, "✓ 句子完成: $currentText")

                            // 🎯 触发句子完成回调
                            onSentenceComplete?.invoke(currentText)

                            // 重置流，继续识别下一句
                            recognizer?.reset(stream!!)
                            vad?.reset()
                            lastText = ""
                        } else if (currentText != lastText) {
                            // 实时更新部分结果
                            val minCharsForDisplay = 3

                            if (currentText.length >= minCharsForDisplay) {
                                val displayText = if (completedText.isEmpty()) {
                                    currentText
                                } else {
                                    "$completedText\n$currentText"
                                }
                                onPartialResult?.invoke(displayText)
                            }
                            lastText = currentText
                        }

                        // 🔄 自动休眠检测（仅在 KWS 模式下）
                        if (keywordSpotter != null && completedText.isNotEmpty()) {
                            val idleTime = System.currentTimeMillis() - lastSpeechTime
                            if (idleTime > IDLE_TIMEOUT_MS) {
                                Log.i(TAG, "💤 空闲超时 (${idleTime}ms)，返回待机模式")

                                // 释放 ASR 资源
                                stream?.release()
                                stream = null
                                recognizer?.release()
                                recognizer = null
                                vad?.release()
                                vad = null
                                Log.i(TAG, "Released OnlineRecognizer and VAD to save memory")

                                // 返回待机模式
                                returnToStandby()
                                return@launch
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Recognition error", e)
                onError?.invoke("识别出错: ${e.message}")
            }
        }
    }

    // ========================================
    // 状态切换
    // ========================================

    private fun returnToStandby() {
        if (keywordSpotter != null) {
            scope.launch(Dispatchers.IO) {
                kwsStream = keywordSpotter?.createStream()
                currentState = State.STANDBY
                startKwsTask()
            }
        }
    }
}
