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
    private lateinit var modelManager: ModelManager

    // 状态
    private var isRecording = false
    private var recognitionJob: Job? = null

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
                startRecording()
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

                recognizer = modelManager.createOnlineRecognizer(
                    modelType = ModelManager.ModelType.ZIPFORMER_TRANSDUCER
                )

                withContext(Dispatchers.Main) {
                    if (recognizer != null) {
                        updateStatus("模型加载成功\n模型路径: ${modelManager.getModelDir().absolutePath}")
                        Log.i(TAG, "Recognizer initialized successfully")
                    } else {
                        val instructions = modelManager.getModelDownloadInstructions()
                        updateStatus("模型加载失败\n\n$instructions")
                        Log.e(TAG, "Failed to initialize recognizer - model files not found")
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
     * 停止录制和识别
     */
    private fun stopRecording() {
        isRecording = false
        recognitionJob?.cancel()

        // 停止音频录制
        audioRecorder?.stopRecording()

        // 释放识别流
        stream?.release()
        stream = null

        btnStartStop.text = "开始识别"
        btnStartStop.setBackgroundColor(getColor(android.R.color.holo_green_light))

        val pcmFile = audioRecorder?.getCurrentPcmFile()
        updateStatus("录音已停止\nPCM文件: ${pcmFile?.absolutePath ?: "无"}")
    }

    /**
     * 启动识别任务
     */
    private fun startRecognitionTask() {
        recognitionJob = lifecycleScope.launch(Dispatchers.IO) {
            var lastText = ""
            val completedText = StringBuilder()  // 累积已完成的文本

            try {
                while (isActive && isRecording) {
                    // 读取音频数据
                    val samples = audioRecorder?.readAudioData()

                    if (samples != null && samples.isNotEmpty()) {
                        // 送入识别流
                        stream?.acceptWaveform(samples, SAMPLE_RATE)

                        // 解码
                        while (recognizer?.isReady(stream!!) == true) {
                            recognizer?.decode(stream!!)
                        }

                        // 获取识别结果
                        val result = recognizer?.getResult(stream!!)
                        val currentText = result?.text ?: ""

                        // 检测自我修正：比较新旧文本
                        if (lastText.isNotEmpty() && currentText.isNotEmpty() && currentText != lastText) {
                            val correctionDetected = detectTextCorrection(lastText, currentText)
                            if (correctionDetected != null) {
                                Log.i(TAG, "🔄 自我修正检测: \"$correctionDetected\" → \"$currentText\"")
                            }
                        }

                        // 检查是否包含句子结束标点符号（语义断句）
                        val hasSentenceEnd = currentText.contains(Regex("[。！？.!?]"))

                        // 同时检查静音endpoint作为辅助判断
                        val isEndpoint = recognizer?.isEndpoint(stream!!) == true

                        // 如果检测到句子结束（标点符号优先，静音作为备选）
                        if ((hasSentenceEnd || isEndpoint) && currentText.isNotEmpty()) {
                            // 句子结束，将当前文本添加到已完成的文本中
                            if (completedText.isNotEmpty()) {
                                completedText.append("\n")
                            }
                            completedText.append(currentText)

                            Log.i(TAG, "Sentence completed: $currentText (punctuation: $hasSentenceEnd, silence: $isEndpoint)")

                            // 更新UI：显示所有已完成的文本
                            withContext(Dispatchers.Main) {
                                tvResult.text = completedText.toString()
                                scrollToBottom()
                            }

                            // 重置流，继续识别下一句
                            recognizer?.reset(stream!!)
                            lastText = ""
                        } else if (currentText != lastText) {
                            // 实时更新：显示已完成的文本 + 当前正在识别的文本
                            withContext(Dispatchers.Main) {
                                val displayText = if (completedText.isEmpty()) {
                                    currentText
                                } else {
                                    "$completedText\n$currentText"
                                }
                                tvResult.text = displayText
                                scrollToBottom()
                            }
                            lastText = currentText
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
        audioRecorder?.clearPcmCache()
        tvResult.text = ""
        Toast.makeText(this, "缓存已清除", Toast.LENGTH_SHORT).show()
        updateStatus("缓存已清除")
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
    }
}
