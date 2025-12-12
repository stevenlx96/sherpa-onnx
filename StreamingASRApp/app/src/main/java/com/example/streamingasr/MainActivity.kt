package com.example.streamingasr

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.KeywordSpotter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 实时流式语音识别 MainActivity
 *
 * 功能:
 * 1. 实时流式语音识别
 * 2. PCM音频缓存
 * 3. 导出WAV文件
 * 4. 从内部存储加载模型 (/data/data/com.example.streamingasr/files/models/)
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "StreamingASR"
        const val SAMPLE_RATE = 16000
        private const val IDLE_TIMEOUT_MS = 5000L  // 5秒无语音自动休眠
    }

    /**
     * 唤醒状态
     */
    enum class WakeState {
        STANDBY,   // 待机：仅监听唤醒词
        ACTIVE     // 激活：进行语音识别
    }

    // UI组件
    private lateinit var btnStartStop: Button
    private lateinit var btnClearCache: Button
    private lateinit var tvResult: TextView
    private lateinit var tvStatus: TextView
    private lateinit var scrollView: ScrollView

    // 核心组件
    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    private var audioRecorder: AudioRecorder? = null
    private var vad: Vad? = null  // VAD 用于智能断句
    private var keywordSpotter: KeywordSpotter? = null  // 唤醒词识别器
    private var kwsStream: OnlineStream? = null
    private lateinit var modelManager: ModelManager

    // 状态
    private var isRecording = false
    private var wakeState: WakeState = WakeState.STANDBY
    private var recognitionJob: Job? = null
    private var kwsJob: Job? = null
    private var lastSpeechTime: Long = 0L  // 最后检测到语音的时间

    // 权限请求
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            initializeRecognizer()
        } else {
            Toast.makeText(this, "需要录音权限才能使用语音识别", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()

        // 初始化模型管理器
        modelManager = ModelManager(this)

        // 检查权限
        checkPermission()
    }

    private fun initViews() {
        btnStartStop = findViewById(R.id.btnStartStop)
        btnClearCache = findViewById(R.id.btnClearCache)
        tvResult = findViewById(R.id.tvResult)
        tvStatus = findViewById(R.id.tvStatus)
        scrollView = findViewById(R.id.scrollView)
    }

    private fun setupListeners() {
        btnStartStop.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                // 如果有KWS，从STANDBY开始；否则直接进入ACTIVE
                if (keywordSpotter != null) {
                    startWakeWordMonitoring()
                } else {
                    startRecording()
                }
            }
        }

        btnClearCache.setOnClickListener {
            clearCache()
        }
    }

    private fun checkPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                initializeRecognizer()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    /**
     * 初始化识别器
     */
    private fun initializeRecognizer() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    updateStatus("正在加载模型...")
                }

                // 检查是否有 keywords.txt 文件
                val hasKeywords = modelManager.checkKwsExists()

                if (hasKeywords) {
                    // KWS 模式：只加载 KeywordSpotter（使用专用 KWS 小模型）
                    // VAD 会在唤醒后创建，避免与 KWS 资源冲突
                    keywordSpotter = modelManager.createKeywordSpotter(
                        keywordsFile = "keywords.txt",
                        threshold = 0.5F,      // 唤醒阈值
                        score = 1.0F           // 关键词分数
                    )

                    withContext(Dispatchers.Main) {
                        if (keywordSpotter != null) {
                            updateStatus("✓ KWS唤醒模式已启用\n模型路径: ${modelManager.getModelDir().absolutePath}\n\n点击\"开始监听\"进入待机模式\n唤醒后自动启用 VAD 智能断句")
                            btnStartStop.text = "开始监听"
                            Log.i(TAG, "KeywordSpotter initialized successfully (KWS mode)")
                        } else {
                            updateStatus("KWS模型加载失败\n请检查 keywords.txt 文件")
                            Log.e(TAG, "Failed to initialize KeywordSpotter")
                        }
                    }
                } else {
                    // 直接识别模式：加载 ASR + VAD
                    recognizer = modelManager.createOnlineRecognizer(
                        modelType = ModelManager.ModelType.ZIPFORMER_TRANSDUCER
                    )

                    // 加载 VAD（仅在直接识别模式）
                    vad = modelManager.createVad(
                        threshold = 0.5F,
                        minSilenceDuration = 1.0F,  // 延长静音时间，给热词更多修正机会
                        minSpeechDuration = 0.25F,
                        maxSpeechDuration = 10.0F
                    )

                    withContext(Dispatchers.Main) {
                        if (recognizer != null) {
                            val vadStatus = if (vad != null) "✓ VAD已启用 (智能断句)" else "✗ VAD未加载 (使用内置endpoint)"
                            updateStatus("✓ 直接识别模式\n模型路径: ${modelManager.getModelDir().absolutePath}\n$vadStatus\n\n点击\"开始识别\"直接开始")
                            btnStartStop.text = "开始识别"
                            Log.i(TAG, "Recognizer initialized successfully (Direct ASR mode)")
                        } else {
                            val instructions = modelManager.getModelDownloadInstructions()
                            updateStatus("模型加载失败\n\n$instructions")
                            Log.e(TAG, "Failed to initialize recognizer - model files not found")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateStatus("初始化失败: ${e.message}")
                }
                Log.e(TAG, "Error initializing recognizer", e)
            }
        }
    }

    /**
     * 开始录制和识别
     */
    private fun startRecording() {
        if (recognizer == null) {
            Toast.makeText(this, "识别器未初始化，请先加载模型", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // 创建音频缓存目录
            val cacheDir = File(filesDir, "audio_cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            // 初始化音频录制器
            audioRecorder = AudioRecorder(SAMPLE_RATE, cacheDir)

            // 创建识别流
            stream = recognizer?.createStream()

            // 开始录制 (保存PCM)
            if (audioRecorder?.startRecording(savePcm = true) == true) {
                isRecording = true
                btnStartStop.text = "停止识别"
                btnStartStop.setBackgroundColor(getColor(android.R.color.holo_red_light))
                updateStatus("正在录音和识别...")

                // 清空结果显示
                tvResult.text = ""

                // 启动识别任务
                startRecognitionTask()
            } else {
                Toast.makeText(this, "启动录音失败", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 开始唤醒词监听（STANDBY 模式）
     */
    private fun startWakeWordMonitoring() {
        if (keywordSpotter == null) {
            Toast.makeText(this, "唤醒词识别器未初始化", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // 创建音频缓存目录
            val cacheDir = File(filesDir, "audio_cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            // 初始化音频录制器
            audioRecorder = AudioRecorder(SAMPLE_RATE, cacheDir)

            // 创建KWS流
            kwsStream = keywordSpotter?.createStream()

            // 开始录制（不保存PCM，不缓存到内存）
            if (audioRecorder?.startRecording(savePcm = false, cacheInMemory = false) == true) {
                isRecording = true
                wakeState = WakeState.STANDBY
                btnStartStop.text = "停止监听"
                btnStartStop.setBackgroundColor(getColor(android.R.color.holo_orange_light))
                updateStatus("⏸️ 待机中，等待唤醒词...")

                // 启动KWS监听任务
                startKwsTask()
            } else {
                Toast.makeText(this, "启动录音失败", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting wake word monitoring", e)
            Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * KWS监听任务
     */
    private fun startKwsTask() {
        kwsJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                while (isActive && isRecording && wakeState == WakeState.STANDBY) {
                    // 读取音频数据
                    val samples = audioRecorder?.readAudioData()

                    if (samples != null && samples.isNotEmpty()) {
                        // 送入KWS流
                        kwsStream?.acceptWaveform(samples, SAMPLE_RATE)

                        // 解码
                        while (keywordSpotter?.isReady(kwsStream!!) == true) {
                            keywordSpotter?.decode(kwsStream!!)
                        }

                        // 检查是否检测到唤醒词
                        val result = keywordSpotter?.getResult(kwsStream!!)
                        if (result != null && result.keyword.isNotEmpty()) {
                            Log.i(TAG, "🎤 检测到唤醒词: ${result.keyword}")

                            withContext(Dispatchers.Main) {
                                onWakeWordDetected(result.keyword)
                            }

                            // 重置KWS流，准备下次唤醒
                            keywordSpotter?.reset(kwsStream!!)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "KWS task error", e)
                withContext(Dispatchers.Main) {
                    updateStatus("KWS监听出错: ${e.message}")
                }
            }
        }
    }

    /**
     * 唤醒词检测到后的处理
     */
    private fun onWakeWordDetected(keyword: String) {
        Log.i(TAG, "🔊 唤醒词触发: $keyword")
        updateStatus("🔊 唤醒！检测到: \"$keyword\"\n正在启动识别...")

        // 停止KWS任务
        kwsJob?.cancel()
        kwsStream?.release()
        kwsStream = null

        // 切换到ACTIVE模式
        wakeState = WakeState.ACTIVE
        btnStartStop.setBackgroundColor(getColor(android.R.color.holo_green_light))

        // 启动ASR识别
        startAsrRecognition()
    }

    /**
     * 启动ASR识别（ACTIVE 模式）
     */
    private fun startAsrRecognition() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 动态创建 OnlineRecognizer（避免与 KWS 资源冲突）
                if (recognizer == null) {
                    Log.i(TAG, "Creating OnlineRecognizer on wake...")
                    recognizer = modelManager.createOnlineRecognizer(
                        modelType = ModelManager.ModelType.ZIPFORMER_TRANSDUCER,
                        hotwordsFile = "hotwords.txt",      // 热词文件
                        hotwordsScore = 10.0f               // 热词全局权重（极高值强制识别）
                    )
                }

                if (recognizer == null) {
                    withContext(Dispatchers.Main) {
                        updateStatus("ASR模型加载失败")
                        Log.e(TAG, "Failed to create OnlineRecognizer")

                        // 返回STANDBY模式
                        if (keywordSpotter != null) {
                            kwsStream = keywordSpotter?.createStream()
                            wakeState = WakeState.STANDBY
                            btnStartStop.setBackgroundColor(getColor(android.R.color.holo_orange_light))
                            updateStatus("⏸️ 待机中，等待唤醒词...")
                            startKwsTask()
                        }
                    }
                    return@launch
                }

                // 动态创建 VAD（在 KWS 模式下唤醒后创建）
                if (vad == null) {
                    Log.i(TAG, "Creating VAD on wake...")
                    vad = modelManager.createVad(
                        threshold = 0.5F,
                        minSilenceDuration = 1.0F,  // 延长静音时间，给热词更多修正机会
                        minSpeechDuration = 0.25F,
                        maxSpeechDuration = 10.0F
                    )
                    if (vad != null) {
                        Log.i(TAG, "VAD created successfully")
                    }
                }

                // 创建识别流
                stream = recognizer?.createStream()
                lastSpeechTime = System.currentTimeMillis()

                withContext(Dispatchers.Main) {
                    val vadStatus = if (vad != null) " (VAD智能断句)" else ""
                    updateStatus("🎙️ 正在识别...$vadStatus")
                    tvResult.text = ""
                }

                // 启动识别任务
                startRecognitionTask()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting ASR recognition", e)
                withContext(Dispatchers.Main) {
                    updateStatus("启动识别失败: ${e.message}")

                    // 识别启动失败，返回STANDBY模式
                    if (keywordSpotter != null) {
                        kwsStream = keywordSpotter?.createStream()
                        wakeState = WakeState.STANDBY
                        btnStartStop.setBackgroundColor(getColor(android.R.color.holo_orange_light))
                        updateStatus("⏸️ 待机中，等待唤醒词...")
                        startKwsTask()
                    }
                }
            }
        }
    }

    /**
     * 停止录制和识别
     */
    private fun stopRecording() {
        isRecording = false
        wakeState = WakeState.STANDBY
        recognitionJob?.cancel()
        kwsJob?.cancel()

        // 停止音频录制
        audioRecorder?.stopRecording()

        // 释放识别流
        stream?.release()
        stream = null

        // 释放KWS流
        kwsStream?.release()
        kwsStream = null

        btnStartStop.text = if (keywordSpotter != null) "开始监听" else "开始识别"
        btnStartStop.setBackgroundColor(getColor(android.R.color.holo_green_light))

        val pcmFile = audioRecorder?.getCurrentPcmFile()
        val statusText = if (keywordSpotter != null) {
            "已停止监听\nPCM文件: ${pcmFile?.absolutePath ?: "无"}"
        } else {
            "录音已停止\nPCM文件: ${pcmFile?.absolutePath ?: "无"}"
        }
        updateStatus(statusText)
    }

    /**
     * 启动识别任务
     */
    private fun startRecognitionTask() {
        recognitionJob = lifecycleScope.launch(Dispatchers.IO) {
            var lastText = ""
            val completedText = StringBuilder()  // 累积已完成的文本

            try {
                while (isActive && isRecording && wakeState == WakeState.ACTIVE) {
                    // 读取音频数据
                    val samples = audioRecorder?.readAudioData()

                    if (samples != null && samples.isNotEmpty()) {
                        // 送入 VAD 检测（如果可用）
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

                        // 检测自我修正：比较新旧文本
                        if (lastText.isNotEmpty() && currentText.isNotEmpty() && currentText != lastText) {
                            val correctionDetected = detectTextCorrection(lastText, currentText)
                            if (correctionDetected != null) {
                                Log.i(TAG, "🔄 自我修正检测: \"$correctionDetected\" → \"$currentText\"")
                            }
                        }

                        // 🎯 断句逻辑（三种方式，优先级递减）
                        val hasSentenceEnd = currentText.contains(Regex("[。！？.!?]"))
                        val vadDetected = vad?.isSpeechDetected() == false  // VAD 检测到静音
                        val isEndpoint = recognizer?.isEndpoint(stream!!) == true  // 内置 endpoint

                        // 组合断句策略
                        val shouldBreak = when {
                            hasSentenceEnd -> {
                                Log.d(TAG, "断句触发: 标点符号")
                                true
                            }
                            vadDetected && currentText.isNotEmpty() -> {
                                Log.d(TAG, "断句触发: VAD 静音检测")
                                true
                            }
                            isEndpoint -> {
                                Log.d(TAG, "断句触发: 内置 endpoint")
                                true
                            }
                            else -> false
                        }

                        // 如果检测到句子结束
                        if (shouldBreak && currentText.isNotEmpty()) {
                            // 句子结束，将当前文本添加到已完成的文本中
                            if (completedText.isNotEmpty()) {
                                completedText.append("\n")
                            }
                            completedText.append(currentText)

                            Log.i(TAG, "✓ 句子完成: $currentText")

                            // 更新UI：显示所有已完成的文本
                            withContext(Dispatchers.Main) {
                                tvResult.text = completedText.toString()
                                scrollToBottom()
                            }

                            // 重置流，继续识别下一句
                            recognizer?.reset(stream!!)
                            vad?.reset()  // 重置 VAD
                            lastText = ""
                        } else if (currentText != lastText) {
                            // 实时更新：只有文本长度>=3个字符时才显示（给热词更多上下文）
                            val minCharsForDisplay = 3  // 至少3个字符才显示实时结果

                            if (currentText.length >= minCharsForDisplay) {
                                withContext(Dispatchers.Main) {
                                    val displayText = if (completedText.isEmpty()) {
                                        currentText
                                    } else {
                                        "$completedText\n$currentText"
                                    }
                                    tvResult.text = displayText
                                    scrollToBottom()
                                }
                            }
                            // 注意：即使不显示，也要更新 lastText 用于修正检测
                            lastText = currentText
                        }

                        // 🔄 自动休眠检测（仅在KWS模式下）
                        if (keywordSpotter != null && completedText.isNotEmpty()) {
                            val idleTime = System.currentTimeMillis() - lastSpeechTime
                            if (idleTime > IDLE_TIMEOUT_MS) {
                                Log.i(TAG, "💤 空闲超时 (${idleTime}ms)，返回待机模式")

                                withContext(Dispatchers.Main) {
                                    // 保存最终识别结果
                                    tvResult.text = completedText.toString()

                                    // 释放ASR流、识别器和VAD（节省内存）
                                    stream?.release()
                                    stream = null
                                    recognizer?.release()
                                    recognizer = null
                                    vad?.release()
                                    vad = null
                                    Log.i(TAG, "Released OnlineRecognizer and VAD to save memory")

                                    // 切换回STANDBY模式
                                    wakeState = WakeState.STANDBY
                                    btnStartStop.setBackgroundColor(getColor(android.R.color.holo_orange_light))
                                    updateStatus("💤 自动休眠，等待唤醒词...\n最后识别: ${completedText.toString().takeLast(30)}")

                                    // 重新启动KWS监听
                                    kwsStream = keywordSpotter?.createStream()
                                    startKwsTask()
                                }

                                // 退出ASR循环
                                return@launch
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Recognition error", e)
                withContext(Dispatchers.Main) {
                    updateStatus("识别出错: ${e.message}")
                }
            }
        }
    }

    /**
     * 检测文本修正
     * 返回被修正的部分，如果没有修正则返回null
     */
    private fun detectTextCorrection(oldText: String, newText: String): String? {
        // 如果新文本比旧文本短，可能是修正
        if (newText.length < oldText.length) {
            return oldText
        }

        // 查找公共前缀
        var commonPrefixLength = 0
        val minLength = minOf(oldText.length, newText.length)
        for (i in 0 until minLength) {
            if (oldText[i] == newText[i]) {
                commonPrefixLength++
            } else {
                break
            }
        }

        // 如果公共前缀不等于旧文本长度，说明有修正
        if (commonPrefixLength < oldText.length) {
            // 返回被修正的部分（旧文本中被改变的部分）
            return oldText.substring(commonPrefixLength)
        }

        return null
    }

    /**
     * 清除缓存
     */
    private fun clearCache() {
        // 显示当前缓存大小
        val cacheSize = audioRecorder?.getCacheSize() ?: 0L
        val cacheSizeMB = cacheSize / 1024 / 1024

        // 清除内存缓存
        audioRecorder?.clearPcmCache()

        // 删除所有磁盘缓存文件
        audioRecorder?.deleteAllCacheFiles()

        // 清空结果显示
        tvResult.text = ""

        val message = "已清除缓存 ($cacheSizeMB MB)"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        updateStatus(message)
        Log.i(TAG, "User cleared cache: $cacheSizeMB MB")
    }

    /**
     * 更新状态文本
     */
    private fun updateStatus(status: String) {
        tvStatus.text = status
    }

    /**
     * 滚动到底部
     */
    private fun scrollToBottom() {
        scrollView.post {
            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) {
            stopRecording()
        }
        audioRecorder = null
        recognizer = null
        vad?.release()
        vad = null
        keywordSpotter?.release()
        keywordSpotter = null
    }
}
